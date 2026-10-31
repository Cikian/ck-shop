package org.jeecg.modules.shop.analytics.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.shop.analytics.config.AnalyticsProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * <p>
 * 行为实时层（Redis）。
 * </p>
 *
 * <p>键按契约 §3.4：{@code track:online} ZSET、{@code track:pv:{minute}} INCR、
 * {@code track:uv:{day}} HyperLogLog。Redis 异常时**静默降级**（实时指标归 0 / 跳过），
 * 绝不因 Redis 抖动影响采集与查询主链路。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Slf4j
@Service
public class TrackRedisService {

    public static final String KEY_ONLINE = "track:online";
    public static final String KEY_PV_MINUTE = "track:pv:";
    public static final String KEY_UV_DAY = "track:uv:";

    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 分钟 PV 键 TTL：2 小时 */
    private static final long PV_TTL_SECONDS = 2 * 60 * 60L;
    /** 日 UV 键 TTL：3 天 */
    private static final long UV_TTL_SECONDS = 3 * 24 * 60 * 60L;

    private final RedisUtil redisUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AnalyticsProperties properties;

    public TrackRedisService(RedisUtil redisUtil,
                             RedisTemplate<String, Object> redisTemplate,
                             AnalyticsProperties properties) {
        this.redisUtil = redisUtil;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /** 在线心跳：ZADD track:online，member=session_id，score=最后活跃 epoch ms */
    public void recordOnline(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        try {
            redisTemplate.opsForZSet().add(KEY_ONLINE, sessionId, System.currentTimeMillis());
        } catch (Exception e) {
            log.debug("[analytics] 在线心跳写入失败：{}", e.getMessage());
        }
    }

    /** 分钟级 PV：INCR track:pv:{yyyyMMddHHmm}，TTL 2h */
    public void recordPv(LocalDateTime eventTime) {
        try {
            String key = KEY_PV_MINUTE + eventTime.format(MINUTE_FMT);
            long count = redisUtil.incr(key, 1L);
            if (count == 1L) {
                redisUtil.expire(key, PV_TTL_SECONDS);
            }
        } catch (Exception e) {
            log.debug("[analytics] 分钟PV写入失败：{}", e.getMessage());
        }
    }

    /** 当日 UV：PFADD track:uv:{yyyyMMdd}，TTL 3d */
    public void recordUv(String visitorId, LocalDateTime eventTime) {
        if (StringUtils.isBlank(visitorId)) {
            return;
        }
        try {
            String key = KEY_UV_DAY + eventTime.format(DAY_FMT);
            redisTemplate.opsForHyperLogLog().add(key, visitorId);
            redisTemplate.expire(key, java.time.Duration.ofSeconds(UV_TTL_SECONDS));
        } catch (Exception e) {
            log.debug("[analytics] 日UV写入失败：{}", e.getMessage());
        }
    }

    /** 在线人数：清理超过 TTL 的会话后 ZCARD。异常降级为 0。 */
    public int onlineCount() {
        try {
            long threshold = System.currentTimeMillis() - properties.getOnlineTtlSeconds() * 1000L;
            redisTemplate.opsForZSet().removeRangeByScore(KEY_ONLINE, 0, threshold);
            Long size = redisTemplate.opsForZSet().size(KEY_ONLINE);
            return size == null ? 0 : size.intValue();
        } catch (Exception e) {
            log.debug("[analytics] 在线人数读取失败：{}", e.getMessage());
            return 0;
        }
    }
}
