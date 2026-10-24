/**
 * CikVerify 人机验证（工作量证明 POW）浏览器 SDK 在本项目里的唯一入口。
 *
 * 依赖：npm 包 `@cikian/captcha-web@1.0.0`（线上正式发布版），弹窗资源默认走官方 CDN
 * （https://cdn.cap.cikian.cn/assets/tac），**不复制、不自托管 tac 文件**。
 * 浏览器只访问同源的 /api/captcha/challenge 与 /api/captcha/verify，
 * appSecret 只在后端，前端拿到的只有一次性 ticket。
 *
 * 为什么集中在一个 hook 里，而不是登录页各写一遍：
 *   · SDK 是「单例 + 弹窗」模型，同一实例在已打开时会复用同一个 Promise（防重复弹窗）；
 *     页面级各自 createCaptcha() 会让快速切换页面后出现两个遮罩；
 *   · challenge/verify 路径、验证码类型、主题色、双语文案必须一致，散出去必然改歪；
 *   · tac.js / tac.css 由 SDK 注入并缓存在模块级，单例才能避免重复注入。
 *
 * 关键设计：
 *   1. 单例：instance / loading 是模块级变量，并发调用复用同一个 Promise。
 *   2. 动态 import：点击时才 import('@cikian/captcha-web')，不把浏览器 SDK 拉进首屏包。
 *   3. 不打印 ticket：ticket 是一次性凭证，进日志等于把"人机验证"白送出去。
 *   4. locale 变化时 setLocale：只影响**下一次** open() 的 lang/aria-label 与文案。
 *
 * 文案来源：i18n 的 `captcha.*`（15 个 key，见 CikVerify 用户接入手册 §6.1）。
 * 用 vue-i18n 的 te() 判存在、t() 取值；某个 key 缺失就不传，SDK 会回退内置中文。
 */

import type { CaptchaInstance, CaptchaMessages, CaptchaWebError } from '@cikian/captcha-web';

import { i18n } from '/@/locales/setupI18n';
import { useGlobSetting } from '/@/hooks/setting';

/** 与 CikVerify 用户接入手册 §6.1 的 15 个 key 一一对应。 */
const CAPTCHA_MESSAGE_KEYS = [
  'tipsSuccess',
  'tipsError',
  'tipsExpired',
  'tipsNetworkError',
  'sliderTitle',
  'concatTitle',
  'rotateTitle',
  'imageClickTitle',
  'imageClickConfirm',
  'powTitle',
  'powLoading',
  'disabledTitle',
  'closeTitle',
  'refreshTitle',
  'overlayLabel',
] as const satisfies readonly (keyof CaptchaMessages)[];

/**
 * 验证码类型兜底：POW（工作量证明）。
 * 不需要背景图，纯浏览器算力校验，管理端登录用它替代原来的字符图形验证码。
 * 可用 VITE_GLOB_CAPTCHA_TYPE 覆盖（写进 _app.config.js，发布后可直接改，无需重新打包），
 * 取值必须与验证码平台给 appId 开通的类型一致。
 */
export const DEFAULT_CAPTCHA_TYPE = 'POW';

/** 主题色兜底：取不到 CSS 变量时用 JeecgBoot 默认主色。 */
const FALLBACK_PRIMARY_COLOR = '#0960bd';

let instance: CaptchaInstance | null = null;
let loading: Promise<CaptchaInstance> | null = null;

/** i18n 当前语言（'zh-CN' / 'en'），只读快照，用于组装 lang 与文案。 */
function currentLocale(): string {
  const global = i18n?.global as any;
  const value = global?.locale;
  if (typeof value === 'string') return value;
  return value?.value ?? 'zh-CN';
}

/** 把项目 locale 映射成 SDK 期望的 BCP-47（zh_CN → zh-CN，en → en-US）。 */
function toCaptchaLocale(locale: string): string {
  const normalized = String(locale || '').replace('_', '-');
  if (!normalized) return 'zh-CN';
  if (normalized.toLowerCase() === 'en') return 'en-US';
  return normalized;
}

/** 按 i18n 的 captcha.* key 组装 SDK messages；缺失的 key 不传，交给 SDK 回退中文。 */
function buildMessages(): Partial<CaptchaMessages> {
  const global = i18n?.global as any;
  const messages: Record<string, string> = {};
  if (!global?.te || !global?.t) return messages as Partial<CaptchaMessages>;
  for (const key of CAPTCHA_MESSAGE_KEYS) {
    const path = `captcha.${key}`;
    if (!global.te(path)) continue;
    const value = global.t(path);
    // vue-i18n 在 key 缺失时会返回 key 本身：再判一次，避免把 "captcha.xxx" 当文案传下去
    if (typeof value === 'string' && value && value !== path) {
      messages[key] = value;
    }
  }
  return messages as Partial<CaptchaMessages>;
}

/** 主色（滑轨颜色与站点主题保持一致），取不到时回退默认主色。 */
function primaryColor(): string {
  try {
    const value = getComputedStyle(document.documentElement).getPropertyValue('--j-global-primary-color');
    return value?.trim() || FALLBACK_PRIMARY_COLOR;
  } catch {
    return FALLBACK_PRIMARY_COLOR;
  }
}

/**
 * challenge/verify 必须与页面同源，所以复用项目统一的接口前缀
 * （VITE_GLOB_API_URL，运行时由 _app.config.js 注入），而不是硬编码 /api。
 */
function resolveEndpoints() {
  const { apiUrl, captchaType } = useGlobSetting();
  const base = String(apiUrl || '').replace(/\/+$/, '');
  return {
    challengeUrl: `${base}/captcha/challenge`,
    verifyUrl: `${base}/captcha/verify`,
    // 平台侧给 appId 开通的类型；未配置时按 POW
    type: String(captchaType || DEFAULT_CAPTCHA_TYPE).trim() || DEFAULT_CAPTCHA_TYPE,
  };
}

/** 首次调用时动态加载并创建实例；并发调用复用同一个 Promise。 */
async function ensureInstance(): Promise<CaptchaInstance> {
  if (instance) return instance;
  if (!loading) {
    loading = import('@cikian/captcha-web')
      .then(({ createCaptcha }) => {
        const { challengeUrl, verifyUrl, type } = resolveEndpoints();
        instance = createCaptcha({
          challengeUrl,
          verifyUrl,
          type,
          locale: toCaptchaLocale(currentLocale()),
          messages: buildMessages(),
          theme: {
            moveTrackMaskBgColor: primaryColor(),
            moveTrackMaskBorderColor: primaryColor(),
          },
        });
        return instance;
      })
      .finally(() => {
        loading = null;
      });
  }
  return await loading;
}

/**
 * 打开验证码弹窗，用户通过后 resolve 一次性 ticket。
 *
 * 调用方必须处理这些 reject reason：
 *   · closed        用户主动关闭 —— 静默返回，不报错；
 *   · resource-load tac CDN/CSP 加载失败 —— 提示稍后重试；
 *   · timeout       操作超时 —— 提示稍后重试；
 *   · ticket-missing 后端 verify 没返回 ticket —— 部署/契约问题，提示重试；
 *   · challenge-failed / init-failed / unsupported —— 同上。
 * ⚠️ 不要打印 resolve 出来的 ticket。
 */
export function useCikianCaptcha() {
  const open = async (): Promise<string> => {
    const captcha = await ensureInstance();
    // SPA 切语言后旧组件的 watcher 已随卸载销毁，这里每次打开前显式同步一次
    // （SDK 语义：下一次 open 生效，已打开的弹窗不受影响）。
    captcha.setLocale(toCaptchaLocale(currentLocale()), buildMessages());
    return await captcha.open();
  };

  return { open };
}

/** 判断是否"用户主动关闭"（不算错误，不该弹提示）。 */
export function isCaptchaClosed(error: unknown): boolean {
  return (error as CaptchaWebError | undefined)?.reason === 'closed';
}
