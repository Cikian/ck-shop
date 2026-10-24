# CikVerify 验证码接入（2026-10-19）

> 状态：**已上线到本地生产构建**（店铺端登录 / 注册 / 忘记密码三条链路）。
> 服务地址：`https://cap.cikian.cn`；`appId=ck-shop`；`appSecret` 只存在于
> `ck-server/.env`（gitignored），**不在前端、不在 Git、不写日志**。
>
> 关联文档：[店铺端账号体系改造-接口契约](../../docs/店铺端账号体系改造-接口契约.md)、
> [CikVerify 用户接入手册](../../../CikVerify/docs/接入与使用/用户接入手册.md)。

---

## 1. 架构与调用链

```
浏览器（店铺前端，同源）
  │ ① POST /api/captcha/challenge   { type?, data?:{type?} }
  │ ② POST /api/captcha/verify      { id, data }
  ▼
Nuxt BFF（server/api/captcha/*）
  │ ③ 转发到 Java：POST /api/captcha/challenge | /verify
  │    - 带上浏览器 User-Agent
  │    - 带上单个真实客户端 IP（X-Forwarded-For）
  ▼
ck-server（Java，Spring Boot 4.1）
  │ ④ CikVerify Java SDK 用 HMAC 调用 https://cap.cikian.cn/v1/*
  │ ⑤ verify 成功：SDK 在你自己的 Redis 签发一次性 ticket
  │     key: captcha:ticket:ck-shop:{ticket}，TTL 120s，绑定 IP + UA
  ▼
浏览器拿到 { code:200, data:{ ticket } }
  │ ⑥ POST /api/auth/send-code { email, scene, ticket }
  ▼
ck-server：CaptchaTicketService.consume(ticket, request)
  │ ⑦ 一次性消费成功 → 发邮箱验证码；失败 → "请先完成安全验证"
```

**为什么浏览器不直连验证码服务**：`appSecret` 只在后端；无 CORS；BFF 可统一限流、
转发真实 IP/UA；验证码服务只暴露给后端。

**为什么 ticket 在 Java 侧签发/消费**：发信接口在 Java，若只由 BFF 校验，
攻击者绕过 BFF 直连 9951 就能免验证发信。Java 必须自己消费 ticket（本实现如此）。

---

## 2. 依赖（全部线上正式发布版，无本地引入）

| 端 | 依赖 | 版本 | 来源 |
|---|---|---|---|
| Java | `cn.cikian:captcha-client-spring-boot-starter` | `1.0.0` | Maven `https://mvnrep.cikian.cn/repository/public/`（根 pom `CikianMaven`） |
| Java（传递） | `cn.cikian:crydis` | `0.2.6` | 同上 |
| 前端 | `@cikian/captcha-web` | `1.0.0` | npm `registry.npmjs.org`（`--save-exact`） |

- 未使用 system scope / 本地 jar / 本地源码；未复制 `tac` 静态文件。
- 前端 tac 资源默认由 npm 包从官方 CDN `https://cdn.cap.cikian.cn/assets/tac/` 加载。
- 升级时只改版本号并重新构建；不要改 SDK 源码。

---

## 3. 后端配置（ck-server）

### 3.1 `application-{dev,prod,test,docker}.yml`

```yaml
ck:
  captcha:
    enabled: true
    service-url: https://cap.cikian.cn
    app-id: ck-shop
    app-secret: ${CK_SHOP_CAPTCHA_SECRET}
    timeout-millis: 3000
    allow-insecure-http: false
    endpoints:
      # server.servlet.context-path=/api，所以这里写 context 相对路径；
      # 对外是 /api/captcha/challenge 与 /api/captcha/verify
      challenge-path: /captcha/challenge
      verify-path: /captcha/verify
    ticket-store: redis
    ticket-ttl-seconds: 120
    ticket-bind-ua: true
    # BFF 会把真实客户端 IP 覆盖进 X-Forwarded-For；9951 必须只对内网开放
    trust-forwarded-for: true

  crydis:
    enable: false        # 关键：Spring 全局 lazy-init 下不能用 crydis 自动装配，
                         # 让 SDK 自己在首个验证码请求时初始化（见 §8 踩坑）
    host: ${REDIS_HOST:127.0.0.1}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    database: ${REDIS_DATABASE:0}
    timeout: 2000
    max-active: 16
    max-idle: 8
    min-idle: 0
    max-wait: 2000
    allowed-packages:
      - cn.cikian.
      - java.util.ArrayList
```

Shiro 白名单（4 个 profile 的 `jeecg.shiro.excludeUrls`）：

```text
/captcha/challenge,/captcha/verify
```

> 注意：是 **context 相对路径**，不是 `/api/captcha/**`（后者会变成 `/api/api/...`）。

### 3.2 环境变量

`ck-server/.env`（gitignored）：

```properties
CK_SHOP_CAPTCHA_SECRET=<32 位以上随机串>
```

`ck-server/.env.example` 已加入空占位。缺失/长度 <32 时 SDK **启动即失败**（fail-fast），
避免误配成"没验证也能发信"。

### 3.3 业务侧

- `SendEmailCodeDTO`：`{ email, scene, ticket }`（`captcha`/`checkKey` 已删除）。
- `ShopAccountServiceImpl#sendEmailCode` 顺序：
  1. email / scene 校验；
  2. IP 维度限流（分钟 + 小时）；
  3. `captchaTicketService.consume(ticket, request)`，失败 → `请先完成安全验证`；
  4. 场景邮箱存在性检查（register 已注册 / login|reset 未注册）；
  5. 发信 + 现有邮箱冷却/每日上限。
- 旧 `validateCaptcha`、`Md5Util`、`jeecgSignatureSecret` 已删除；`RandImageUtil` 仅保留给管理端登录。
- 邮箱验证码失败锁定、IP/邮箱/账号限流**全部保留**。

---

## 4. BFF（Nuxt）

| 文件 | 作用 |
|---|---|
| `server/api/captcha/challenge.post.ts` | 同源 challenge 代理；body ≤256KB；20 次/分/IP；透传 type |
| `server/api/captcha/verify.post.ts` | 同源 verify 代理；body `{id,data}`；60 次/分/IP |
| `server/utils/captchaProxy.ts` | 公共代理：body 限制、UA + 单个 XFF、信封透传、502 fail-closed |
| `server/api/auth/send-code.post.ts` | `{email,scene,ticket,lang}`；ticket 空→400；转发 Java `{email,scene,ticket}` + 同一 UA/XFF |
| `server/utils/apiClient.ts` | 新增 `rawEnvelope`（仅验证码链路）；Java 全局 advice 给 SDK `ApiResult` 补 `success`，默认解包会毁掉信封 |

- `rawEnvelope` 是唯一共享层改动，默认行为不变。
- Java 业务失败按 SDK 契约返回 **HTTP 200 + `{code,msg}`**（4000/4001/403/4290/5000），BFF 原样透传；
  只有传输失败/非信封响应才 502 + `{code:5000,msg:"验证服务暂不可用"}`。
- `lang` 解析：`body.lang` → `x-language` → `event.context.locale` → `en`；决定 Java 错误文案语言。

---

## 5. 前端（Nuxt / Vue 3）

- 依赖：`@cikian/captcha-web@1.0.0`。
- `composables/useCikianCaptcha.ts`：
  - 客户端单例、动态 import；
  - `challengeUrl='/api/captcha/challenge'`、`verifyUrl='/api/captcha/verify'`；
  - 默认 `type='SLIDER'`，可用 `NUXT_PUBLIC_CAPTCHA_TYPE=POW` 临时联调；
  - locale 用 BCP-47（zh-CN/en-US/fr-FR），`captcha.*` 15 条翻译；
  - 品牌色 `#FF4D6D / #E63E5C`；
  - SSR 调用直接抛明确错误；不打印 ticket。
- `EmailCodeFields.vue`：图形码区块已删除，只剩「邮箱 + 验证码 + 获取验证码」；
  提示文案「点击获取验证码后将进行安全验证」。
- 三个页面（login/register/forgot-password）统一流程：
  1. 校验邮箱；
  2. `await openCaptcha()`（用户关闭 `reason==='closed'` → 静默返回）；
  3. 拿到 ticket 后 `requestEmailCode({email, scene, ticket})`；
  4. 成功启动后端返回的 `cooldownSeconds` 倒计时；失败展示后端文案。
- 旧 `composables/useCaptcha.ts` 与 `server/api/captcha/[key].get.ts` 已删除。

---

## 6. 错误码与用户体验

| code | 含义 | 前端表现 |
|---|---|---|
| 200 | 成功，`data.ticket`（verify） | 继续发信 |
| 4000 | challenge 失效/已消费 | SDK 自动刷新验证码 |
| 4001 | 轨迹校验失败 | SDK 自动刷新验证码 |
| 403 | 参数非法（id 等） | 提示重试 |
| 4010 | 签名/时间戳/nonce 错误 | 检查后端 secret/NTP；用户侧提示稍后重试 |
| 4030 | 应用禁用/IP 不允许 | 后端日志排查 |
| 4290 | 配额超限 | 提示稍后重试 |
| 5000 | 验证服务不可用/内部错误 | BFF 502 或前端提示稍后重试（fail-closed） |

用户关闭弹窗：`reason='closed'`，**不提示错误**。
`resource-load`（CDN/CSP/网络）/`timeout`/`ticket-missing`：提示「安全验证暂时不可用，请稍后重试」。

---

## 7. 验证记录（2026-10-19 ~ 20）

### 后端（直接 9951）

- `POST /api/captcha/challenge {"type":"POW"}` → HTTP 200 `{"code":200,"data":{"id":"ck-shop_...","type":"POW"}}`
- `POST /api/captcha/verify {"id":"invalid","data":{}}` → HTTP 200 `{"code":403,"msg":"验证码 ID 非法"}`
- 完整 POW 链路：verify 签发 43 字符 ticket → Redis `captcha:ticket:ck-shop:*` → send-code 消费成功 →
  重放被拒 → Redis key 归零。
- Maven 解析日志确认 `Downloaded from CikianMaven: https://mvnrep.cikian.cn/...`；
  fat jar 内含 `captcha-client-spring-boot-starter-1.0.0.jar`、`crydis-0.2.6.jar`。

### BFF（3000）

- `POST /api/captcha/challenge {}` → 200，`type=SLIDER`，`data.id` 以 `ck-shop_` 开头。
- `POST /api/captcha/verify {"id":"invalid","data":{}}` → 200 + `code=403`。
- body >256KB → 413；非法 JSON → 400；无 ticket 发信 → 400「请先完成安全验证」；
  Java 停机时 challenge/verify → 502 + `{code:5000}`。

### 浏览器 e2e（Edge headless + CDP，证据在 `nuxt-shop/.probe/captcha-e2e/`）

- POW（`/zh/register`）：弹窗（POW 自动完成）→ challenge 200 → verify 200 + 43 字符 ticket →
  send-code 200 `cooldownSeconds=60` → 「60s 后重发」；ticket 不出现在 console/`__NUXT__`；
  同 ticket 重放 → 400「请先完成安全验证」；`/zh/login` 未注册邮箱 → 后端/UI 均为中文「该邮箱尚未注册」。
- SLIDER（`/en/register`）：弹窗打开（aria-label "Security verification"、lang en-US、CDN tac.js/css 加载、
  品牌色滑轨）；ESC 关闭无报错、无 send-code 请求。

---

## 8. 踩坑与修复

1. **`TicketStoreUnavailableException` / verify 5000**
   - 现象：challenge 正常，verify 签发 ticket 时报 Redis 不可用。
   - 根因：ck-server 开了 `spring.main.lazy-initialization=true`，crydis 自动装配的
     `crydisInit` 无依赖方永不创建；而 SDK 的 `CrydisSupport` 只要看到
     `ck.crydis.enable=true` 就直接返回 true、不自行初始化。
   - 修复：4 个 profile 把 `ck.crydis.enable` 改为 `false`，让 SDK 自己初始化；
     首个验证码请求日志会出现「验证码已使用内置 Crydis 初始化 Redis」。
2. **BFF challenge 502**
   - 根因：JeecgBoot 全局 `ResponseBodyAdvice` 给 SDK 的 `ApiResult` 补了 `success`，
     BFF 的 `apiClient` 默认把 `success:true` 解包成不存在的 `result`（undefined），
     `success:false` 又抛错丢信封。
   - 修复：`createApiClient(..., { rawEnvelope: true })`，仅验证码链路原样透传。
3. **ticket 一直"请先完成安全验证"**
   - 根因：BFF 未转发浏览器 UA 或 XFF；ticket 签发绑定 BFF 的 IP/UA，消费时对不上。
   - 修复：challenge / verify / send-code 三条链路统一转发浏览器原始 UA + 单个真实 IP。
4. **本地/测试环境**
   - 联调用 `NUXT_PUBLIC_CAPTCHA_TYPE=POW`（自动完成，便于 e2e）；生产用 SLIDER。
5. **CSP 把 tac 资源全拦了（2026-10-22 修复）**
   - 现象：点「获取验证码」后页面提示「安全验证加载失败，请重试」，弹窗根本不出现；
     控制台刷 CSP 报错：
     `Loading the script 'https://cdn.cap.cikian.cn/assets/tac/js/tac.min.js' violates …
     "script-src 'self' 'unsafe-inline'"`，stylesheet（tac.css）与 tac.js 兜底同样被拦。
   - 根因：`server/middleware/security-headers.ts` 的 CSP 只放行 `'self'`，
     而 SDK 默认 `assetBaseUrl=https://cdn.cap.cikian.cn/assets/tac`，tac.js / tac.css 属外链。
   - 修复：`script-src` / `style-src` 放行 `https://cdn.cap.cikian.cn`，并补
     `worker-src 'self' blob:`（tac 用 Blob 生成 Worker）。**改 CSP 后要重启 dev / 重新构建**。
   - 排查捷径：先在 Network 看 `tac.min.js` 是否被 CSP 拦（页面里根本没有该 script 时必然是它），
     再去怀疑后端；后端 challenge 直连 9951 与走 BFF 都是 200 的话，问题就只可能在前端资源加载。

---

## 9. 部署清单

- [ ] `ck-server/.env` / 部署环境注入 `CK_SHOP_CAPTCHA_SECRET`（≥32 位；不要进 Git）。
- [ ] Java 构建：`mvn ... package`（**不要 `-o`**，需从 CikianMaven 拉 SDK）。
- [ ] 后端 9951 只对内网/BFF 开放；BFF 覆盖 `X-Forwarded-For`（单 IP）。
- [ ] Redis 可用；多实例共享；`ck.crydis.enable=false`。
- [ ] 4 个 profile 的 Shiro `excludeUrls` 含 `/captcha/challenge,/captcha/verify`。
- [ ] 前端构建前 `npm install`（含 `@cikian/captcha-web@1.0.0`）；`npm run build`。
- [ ] 浏览器可访问 `https://cdn.cap.cikian.cn/assets/tac/`（CSP 需放行；离线可换自托管 assetBaseUrl）。
- [ ] 服务器 NTP 正常（HMAC timestamp ±300s）。
- [ ] 监控 4010/4030/4290/5000 与 BFF 502；验证码服务不可用时 fail-closed（不放行发信）。
- [ ] 定期轮换 secret（管理端轮换后旧值保留 7 天，及时更新环境变量并重启后端）。
- [ ] **上线前轮换一次 secret**：本次接入的 appSecret 曾在开发沟通中出现过，按"密钥可能泄露"处理，
      到 CikVerify 管理端轮换后更新 `CK_SHOP_CAPTCHA_SECRET` 并重启 ck-server。

---

## 10. 已知限制 / 风险

1. **错误文案语言**：CikVerify 服务端的 msg 由 Java 后端透传；当前 ck-shop 的业务错误是 Java 硬编码中文，
   `lang` 只影响 `Accept-Language/x-language` 到达后端；后续后端 i18n 化即可生效。
2. **前端限流是进程内**：challenge 20/min/IP、verify 60/min/IP 只是边缘减速；
   权威配额在验证码服务（按 app）与 Java（按 IP/邮箱）。
3. **CDN 依赖**：tac.js/css 默认来自 `cdn.cap.cikian.cn`；若生产网络/CSP 限制，需要自托管
   `tac` 并配置 `assetBaseUrl`（仍通过 npm 包，不复制源码）。
4. **ticket 绑定 IP+UA**：BFF 必须准确转发；若未来加 CDN/WAF，需保证 XFF 可信链不中断。
5. **POW 仅联调用**：生产使用 SLIDER；POW 是无交互验证，不能挡脚本。
6. **旧图形码已彻底移除**：回滚需要 revert 本次改动并恢复 `RandImageUtil` 发信校验；
   本次改造已通过 e2e，建议保留 git 记录作为回滚点。

---

## 11. 变更文件清单

**ck-server**
- `pom.xml`（CikianMaven + 版本属性）
- `jeecg-boot-module/ck-shop/pom.xml`（starter 1.0.0）
- `application-{dev,prod,test,docker}.yml`（ck.captcha / ck.crydis / Shiro）
- `SendEmailCodeDTO.java`
- `ShopAccountServiceImpl.java`
- `.env.example`

**nuxt-shop**
- `package.json` / `package-lock.json`（`@cikian/captcha-web@1.0.0`）
- `nuxt.config.ts`（`runtimeConfig.public.captchaType`）
- `server/api/captcha/challenge.post.ts`、`verify.post.ts`、`server/utils/captchaProxy.ts`
- `server/api/auth/send-code.post.ts`
- `server/utils/apiClient.ts`（`rawEnvelope`）
- `composables/useCikianCaptcha.ts`、`useAuthForm.ts`、`useAuth.ts`
- `app/components/auth/EmailCodeFields.vue`
- `app/pages/{login,register,forgot-password}.vue`
- `i18n/locales/{zh,en,fr}.json`
- 删除 `composables/useCaptcha.ts`、`server/api/captcha/[key].get.ts`
- `.env.example`（`NUXT_PUBLIC_CAPTCHA_TYPE`）
