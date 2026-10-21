package org.jeecg.modules.shop.common;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.shop.account.constant.ShopAccountConstant;
import org.springframework.stereotype.Service;

/**
 * 店铺端 Redis 固定窗口限流器。
 *
 * <p>为什么不用内存计数：线上可能是多实例部署，进程内 Map 每个实例各算一份，
 * 攻击者轮询实例就能把配额放大 N 倍；而且重启即清零。
 * 本项目后端已经有 Redis，限流是最适合放 Redis 的东西之一。</p>
 *
 * <p><b>失败策略：fail-closed</b>。Redis 异常时对「发信 / 登录 / 验证码」这类
 * 消耗型动作直接拒绝（"系统繁忙"），而不是放行——放行等于限流失效，
 * 攻击者只要把 Redis 打挂就能无限发信。</p>
 *
 * <p>方法都是固定窗口：首次 incr 时设置 TTL，窗口内累加，超过上限抛业务异常。
 * 固定窗口在边界（如 59s + 60s）会有最多 2 倍瞬时流量，对这些低频接口足够。</p>
 *
 * @author Cikian
 */
@Slf4j
@Service
public class ShopRateLimiter {

    private final RedisUtil redisUtil;

    public ShopRateLimiter(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    /**
     * 计数 +1，超过 limit 抛业务异常。
     *
     * @param scope         维度标识，例如 "send-code:ip:min:1.2.3.4"
     * @param limit         窗口内最大次数
     * @param windowSeconds 窗口长度（秒）
     * @param message       超限时给用户看的文案
     */
    public void check(String scope, int limit, long windowSeconds, String message) {
        String key = ShopAccountConstant.RATE_LIMIT_PREFIX + scope;
        long count;
        try {
            count = redisUtil.incr(key, 1L);
            if (count == 1L) {
                redisUtil.expire(key, Math.max(1L, windowSeconds));
            }
        } catch (Exception e) {
            // fail-closed：Redis 不可用时拒绝，而不是放行
            log.error("[rate-limit] Redis 计数失败，按 fail-closed 拒绝。scope={}", scope, e);
            throw new JeecgBootException("系统繁忙，请稍后再试");
        }
        if (count > limit) {
            throw new JeecgBootException(message);
        }
    }

    /**
     * 记录一次失败（用于验证码输错、登录失败等），返回累计失败次数。
     * 计数 key 第一次创建时设置 TTL，过期自动清零。
     */
    public long recordFailure(String scope, long ttlSeconds) {
        String key = ShopAccountConstant.RATE_LIMIT_PREFIX + scope;
        try {
            long count = redisUtil.incr(key, 1L);
            if (count == 1L) {
                redisUtil.expire(key, Math.max(1L, ttlSeconds));
            }
            return count;
        } catch (Exception e) {
            log.error("[rate-limit] Redis 失败计数失败，按 fail-closed 拒绝。scope={}", scope, e);
            throw new JeecgBootException("系统繁忙，请稍后再试");
        }
    }

    /** 读取当前失败次数；不存在返回 0 */
    public long failureCount(String scope) {
        String key = ShopAccountConstant.RATE_LIMIT_PREFIX + scope;
        try {
            Object value = redisUtil.get(key);
            if (value == null) {
                return 0L;
            }
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("[rate-limit] 失败计数不是数字，按 0 处理。scope={}", scope);
            return 0L;
        } catch (Exception e) {
            log.error("[rate-limit] 读取失败计数失败，按 fail-closed 拒绝。scope={}", scope, e);
            throw new JeecgBootException("系统繁忙，请稍后再试");
        }
    }

    /** 清空某个维度的计数（验证成功后调用） */
    public void clear(String scope) {
        try {
            redisUtil.del(ShopAccountConstant.RATE_LIMIT_PREFIX + scope);
        } catch (Exception e) {
            // 清理失败不影响主流程（最多多保留一个窗口的计数）
            log.warn("[rate-limit] 清理计数失败。scope={}", scope, e);
        }
    }
}
