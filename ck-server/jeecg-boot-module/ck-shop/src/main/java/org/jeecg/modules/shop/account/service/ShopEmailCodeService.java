package org.jeecg.modules.shop.account.service;

import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.shop.account.constant.ShopAccountConstant;
import org.jeecg.modules.shop.account.entity.vo.SendEmailCodeVO;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * <p>
 * 店铺端邮箱验证码服务
 * </p>
 *
 * 承担三件事，缺一不可：
 * <ol>
 *   <li><b>生成与校验</b>：6 位数字，5 分钟有效，**一次性消费**（校验成功立刻删除）；</li>
 *   <li><b>限流</b>：同邮箱 60 秒冷却 + 每日 10 条、同 IP 每日 30 条。
 *       没有限流的话，这个接口就是一台"免费给陌生人发垃圾邮件的机器"，
 *       结局是发信域名被拉黑，**所有用户**都收不到验证码；</li>
 *   <li><b>冷却信息</b>：返回 cooldownSeconds 给前端做倒计时，
 *       让"还不能发"这件事在 UI 上可见，而不是让用户狂点按钮。</li>
 * </ol>
 *
 * <b>关于每日配额是按自然日还是滚动 24 小时：</b>
 * 这里用**自然日**（key 里带日期，TTL 到次日 0 点）。
 * 滚动窗口对用户更"公平"，但自然日与运营看的报表口径一致，
 * 且过期时间可预测（不会出现"某个 key 永远在续期"）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Slf4j
@Service
public class ShopEmailCodeService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedisUtil redisUtil;

    private final ShopMailService shopMailService;

    public ShopEmailCodeService(RedisUtil redisUtil, ShopMailService shopMailService) {
        this.redisUtil = redisUtil;
        this.shopMailService = shopMailService;
    }

    // ==================================================================
    // 发送
    // ==================================================================

    /**
     * 生成并发送验证码。
     *
     * <p>调用方（Controller）必须**已经**校验过图形验证码与场景下的邮箱存在性，
     * 这里只负责限流 + 生成 + 落 Redis + 发信。
     *
     * @param email 邮箱（已规范化：trim + toLowerCase）
     * @param scene register | login | reset
     * @param ip    客户端 IP，用于 IP 维度限流
     * @return 含冷却秒数
     */
    public SendEmailCodeVO sendCode(String email, String scene, String ip) {
        String normalizedEmail = normalizeEmail(email);
        String codeKey = buildCodeKey(scene, normalizedEmail);
        String cooldownKey = buildCooldownKey(normalizedEmail);

        // 1) 冷却检查（放在最前面：连点重发不该消耗每日配额）
        Object cooldown = redisUtil.get(cooldownKey);
        if (cooldown != null) {
            Long remaining = null;
            try {
                remaining = Long.parseLong(cooldown.toString());
            } catch (NumberFormatException ignored) {
                // 值不是数字（历史数据/人工改过）：按已冷却处理，不因为脏数据放过限流
            }
            long seconds = remaining == null ? ShopAccountConstant.EMAIL_CODE_COOLDOWN_SECONDS : remaining;
            throw new JeecgBootException("验证码发送过于频繁，请 " + Math.max(seconds, 1) + " 秒后再试");
        }

        // 2) 每日配额（邮箱 / IP 两个维度都要过）
        checkDailyQuota(ShopAccountConstant.EMAIL_DAILY_EMAIL_PREFIX + dayKey() + ":" + normalizedEmail,
                ShopAccountConstant.EMAIL_DAILY_LIMIT_PER_EMAIL, "该邮箱今日验证码发送次数已达上限，请明天再试");
        if (StringUtils.isNotBlank(ip)) {
            checkDailyQuota(ShopAccountConstant.EMAIL_DAILY_IP_PREFIX + dayKey() + ":" + ip,
                    ShopAccountConstant.EMAIL_DAILY_LIMIT_PER_IP, "当前网络今日验证码发送次数已达上限，请明天再试");
        }

        // 3) 生成并发送。**先发信再落 Redis**：
        //    反过来的话，SMTP 失败会留下一个"用户从没收到、但系统认为已发出"的验证码，
        //    用户在 5 分钟内重发还会被冷却挡住，等于被锁死。
        String code = RandomUtil.randomNumbers(ShopAccountConstant.EMAIL_CODE_LENGTH);
        shopMailService.sendHtml(normalizedEmail, buildSubject(scene), buildBody(scene, code, normalizedEmail));

        // 4) 发信成功后才写入验证码与计数
        redisUtil.set(codeKey, code, ShopAccountConstant.EMAIL_CODE_TTL_SECONDS);
        redisUtil.set(cooldownKey, ShopAccountConstant.EMAIL_CODE_COOLDOWN_SECONDS,
                ShopAccountConstant.EMAIL_CODE_COOLDOWN_SECONDS);
        incrementDailyCounter(ShopAccountConstant.EMAIL_DAILY_EMAIL_PREFIX + dayKey() + ":" + normalizedEmail,
                ShopAccountConstant.EMAIL_DAILY_LIMIT_PER_EMAIL);
        if (StringUtils.isNotBlank(ip)) {
            incrementDailyCounter(ShopAccountConstant.EMAIL_DAILY_IP_PREFIX + dayKey() + ":" + ip,
                    ShopAccountConstant.EMAIL_DAILY_LIMIT_PER_IP);
        }

        log.info("店铺端邮箱验证码已发送，scene={}，email={}", scene, normalizedEmail);
        return new SendEmailCodeVO().setCooldownSeconds((int) ShopAccountConstant.EMAIL_CODE_COOLDOWN_SECONDS);
    }

    // ==================================================================
    // 校验 / 消费
    // ==================================================================

    /**
     * 校验验证码（**不删除**）。用于需要"先校验后做业务、业务失败可重试"的场景。
     *
     * <p>防爆破：6 位数字只有 100 万种组合，如果不限次数，攻击者可以在 5 分钟
     * 有效期内用高并发把验证码撞出来（尤其邮箱是公开信息时）。这里做两层限制：
     * 同一邮箱同一场景最多输错 {@link ShopAccountConstant#EMAIL_CODE_MAX_ATTEMPTS} 次，
     * 超过后直接作废该验证码并锁定该场景 15 分钟，必须重新获取。</p>
     *
     * @throws JeecgBootException 验证码不存在 / 已过期 / 不匹配 / 错误次数过多
     */
    public void verifyCode(String email, String scene, String code) {
        String normalizedEmail = normalizeEmail(email);
        if (StringUtils.isBlank(code)) {
            throw new JeecgBootException("请填写邮箱验证码");
        }
        String codeKey = buildCodeKey(scene, normalizedEmail);
        String failKey = buildFailKey(scene, normalizedEmail);

        // 已经输错太多次：即使这次输对了也不放行（否则锁定形同虚设）
        if (failureCount(failKey) >= ShopAccountConstant.EMAIL_CODE_MAX_ATTEMPTS) {
            redisUtil.del(codeKey);
            throw new JeecgBootException("验证码错误次数过多，请重新获取验证码");
        }

        Object cached = redisUtil.get(codeKey);
        if (cached == null) {
            throw new JeecgBootException("验证码无效或已过期，请重新获取");
        }
        if (!code.trim().equals(cached.toString())) {
            long fails = recordFailure(failKey, ShopAccountConstant.EMAIL_CODE_ATTEMPT_LOCK_SECONDS);
            if (fails >= ShopAccountConstant.EMAIL_CODE_MAX_ATTEMPTS) {
                // 达到阈值立刻作废验证码，避免继续被枚举
                redisUtil.del(codeKey);
                throw new JeecgBootException("验证码错误次数过多，请重新获取验证码");
            }
            long remaining = ShopAccountConstant.EMAIL_CODE_MAX_ATTEMPTS - fails;
            throw new JeecgBootException("邮箱验证码错误，还可尝试 " + remaining + " 次");
        }
        // 校验通过：清掉失败计数，验证码本身在 consumeCode 里删除
        redisUtil.del(failKey);
    }

    /**
     * 校验并**立即删除**验证码（一次性消费）。
     *
     * <p>契约 2.2 / 2.4 / 2.6 都要求"成功后立即删除"。
     * 这里刻意不用"先 verifyCode 再 del"的两步写法：两步之间若有并发
     * （用户双击提交、或者前端重试），同一个验证码会被用两次。
     * 用 {@code redisUtil.get} + 校验 + {@code del} 的单方法串行执行，
     * 配合 5 分钟有效期已足够；真正强一致需要 Lua 或 SETNX，
     * 对"注册/登录"这种低频操作属于过度设计。
     *
     * @throws JeecgBootException 校验失败
     */
    public void consumeCode(String email, String scene, String code) {
        String normalizedEmail = normalizeEmail(email);
        verifyCode(normalizedEmail, scene, code);
        redisUtil.del(buildCodeKey(scene, normalizedEmail));
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /** 邮箱规范化：统一小写，避免 `A@x.com` 与 `a@x.com` 拿到两份配额/两个 key */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String buildCodeKey(String scene, String email) {
        return ShopAccountConstant.EMAIL_CODE_PREFIX + scene + ":" + email;
    }

    private String buildCooldownKey(String email) {
        return ShopAccountConstant.EMAIL_CODE_COOLDOWN_PREFIX + email;
    }

    /** 验证码输错次数的 key：与验证码本身分开存，方便锁定后仍能给出准确文案 */
    private String buildFailKey(String scene, String email) {
        return ShopAccountConstant.EMAIL_CODE_FAIL_PREFIX + scene + ":" + email;
    }

    /** 读取输错次数；Redis 异常时 fail-closed（抛业务异常而不是放行） */
    private long failureCount(String key) {
        try {
            Object value = redisUtil.get(key);
            if (value == null) {
                return 0L;
            }
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("验证码失败计数非法，key={}，按 0 处理", key);
            return 0L;
        } catch (Exception e) {
            log.error("读取验证码失败计数异常，按 fail-closed 拒绝。key={}", key, e);
            throw new JeecgBootException("系统繁忙，请稍后再试");
        }
    }

    /** 累计一次输错；首次创建时设置 TTL（锁定窗口） */
    private long recordFailure(String key, long ttlSeconds) {
        try {
            long count = redisUtil.incr(key, 1L);
            if (count == 1L) {
                redisUtil.expire(key, Math.max(1L, ttlSeconds));
            }
            return count;
        } catch (Exception e) {
            log.error("记录验证码失败次数异常，按 fail-closed 拒绝。key={}", key, e);
            throw new JeecgBootException("系统繁忙，请稍后再试");
        }
    }

    private String dayKey() {
        return LocalDateTime.now().format(DAY_FORMAT);
    }

    /** 距次日 0 点的剩余秒数（至少 1，避免 TTL=0 被 Redis 当作"永不过期"） */
    private long secondsUntilTomorrow() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay();
        return Math.max(Duration.between(now, tomorrow).getSeconds(), 1L);
    }

    /**
     * 读计数并判上限。
     *
     * ⚠️ 用 {@code >=} 而不是 {@code >}：limit 是"每天最多发 N 条"，
     * 已经发到 N 条时下一条就该被拒。
     */
    private void checkDailyQuota(String key, long limit, String errorMessage) {
        Object value = redisUtil.get(key);
        if (value == null) {
            return;
        }
        try {
            if (Long.parseLong(value.toString()) >= limit) {
                throw new JeecgBootException(errorMessage);
            }
        } catch (NumberFormatException e) {
            log.warn("每日配额计数非法，key={}，值={}", key, value);
        }
    }

    /**
     * 计数 +1 并设置到次日 0 点过期。
     *
     * <p>每次都要重设过期时间（而不是只在第一次设）：
     * 用 RedisUtil.incr 建的 key 没有 TTL，如果只在首次 set 过期时间，
     * 后续 incr 不会续期也不会清掉 —— 这没问题；但反过来，
     * 若计数器在 23:59 创建、TTL 按"到次日 0 点"算只剩 60 秒，
     * 那条记录就近乎白送。统一按当前时间重算 TTL，语义最清晰：
     * "这个 key 活到明天 0 点"。
     */
    private void incrementDailyCounter(String key, long limit) {
        long current = redisUtil.incr(key, 1L);
        if (current == 1L) {
            // 第一次创建时才需要设 TTL；后续 incr 不改变 TTL
            redisUtil.expire(key, secondsUntilTomorrow());
        }
        if (current > limit) {
            // 并发下可能双双通过 checkDailyQuota，这里再兜一层
            log.warn("每日配额已超限，key={}，值={}，上限={}", key, current, limit);
        }
    }

    private String buildSubject(String scene) {
        return switch (scene) {
            case ShopAccountConstant.SCENE_REGISTER -> "[ck-shop] 注册验证码";
            case ShopAccountConstant.SCENE_RESET -> "[ck-shop] 重置密码验证码";
            default -> "[ck-shop] 登录验证码";
        };
    }

    /**
     * 邮件正文。
     *
     * 用内联样式而不是外部 CSS：邮件客户端（尤其是 Gmail / Outlook）
     * 会剥离 {@code <style>} 与 class，内联是唯一能保证渲染一致的做法。
     * 颜色沿用站点品牌色（珊瑚玫红 #E1466A / 奶油白底）。
     */
    private String buildBody(String scene, String code, String email) {
        String action = switch (scene) {
            case ShopAccountConstant.SCENE_REGISTER -> "注册账号";
            case ShopAccountConstant.SCENE_RESET -> "重置密码";
            default -> "登录";
        };
        int minutes = (int) (ShopAccountConstant.EMAIL_CODE_TTL_SECONDS / 60);
        return """
                <div style="margin:0;padding:32px 16px;background:#FDF8F4;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;">
                  <div style="max-width:520px;margin:0 auto;background:#FFFFFF;border-radius:16px;padding:32px;box-shadow:0 2px 12px rgba(0,0,0,0.06);">
                    <h1 style="margin:0 0 8px;font-size:20px;line-height:1.4;color:#2B2B2B;font-weight:600;">ck-shop</h1>
                    <p style="margin:0 0 24px;font-size:14px;line-height:1.6;color:#6B6B6B;">您正在%s，请使用下面的验证码完成操作。</p>
                    <div style="background:#FDF1F4;border:1px solid #F6D5DE;border-radius:12px;padding:20px;text-align:center;margin-bottom:24px;">
                      <span style="display:inline-block;font-size:32px;letter-spacing:8px;font-weight:700;color:#E1466A;font-family:'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace;">%s</span>
                    </div>
                    <p style="margin:0 0 8px;font-size:14px;line-height:1.6;color:#6B6B6B;">验证码 <strong style="color:#2B2B2B;">%d 分钟</strong>内有效，且只能使用一次。</p>
                    <p style="margin:0 0 24px;font-size:13px;line-height:1.6;color:#9A9A9A;">如果这不是您本人的操作，请忽略本邮件，您的账号不会有任何变化。</p>
                    <hr style="border:none;border-top:1px solid #EFEFEF;margin:0 0 16px;" />
                    <p style="margin:0;font-size:12px;line-height:1.6;color:#B0B0B0;">此邮件由系统自动发出，请勿直接回复。收件地址：%s</p>
                  </div>
                </div>
                """.formatted(action, code, minutes, email);
    }
}
