package org.jeecg.modules.shop.analytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 用户行为分析配置（前缀 {@code ck.shop.analytics}）。
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@Component
@ConfigurationProperties(prefix = "ck.shop.analytics")
public class AnalyticsProperties {

    /** 采集采样率（0-100，100=全量）。量级增长时启用高基数事件采样。 */
    private int sampleRate = 100;

    /** 明细保留天数（默认 30），清理任务按此归档。 */
    private int retentionDays = 30;

    /** 异步批量写入的批次大小。 */
    private int batchSize = 500;

    /** 内存队列容量；满则静默丢弃（丢行为数据，绝不丢业务、绝不阻塞）。 */
    private int queueCapacity = 10000;

    /** 批量 flush 间隔（毫秒）：队列不满也定期刷。 */
    private long flushIntervalMs = 2000;

    /** 在线判定阈值（秒）：ZSET 里超过该时长未活跃的会话视为离线。 */
    private int onlineTtlSeconds = 60;

    /** 单 IP/单签名每分钟采集上限（0=不限制）。BFF 为唯一调用方且已签名，默认不限制以免误丢数据。 */
    private int trackRateLimitPerMinute = 0;

    /** GeoLite2-City.mmdb 路径；留空/缺失时降级为不解析地域。 */
    private String geoMmdbPath = "";

    /** 采集签名密钥（BFF 与 Java 共用）；留空时不校验签名。 */
    private String signatureSecret = "";
}
