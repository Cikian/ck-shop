package org.jeecg.modules.shop.analytics.entity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * <p>
 * 采集事件 DTO（BFF/前端批量上报的单个事件）。
 * </p>
 *
 * <p>字段用 snake_case（对齐契约 §3.2 列名 / §4.2 公共属性），BFF 只传原始 IP，
 * country/province/city 由 Java 端据 {@code ip} 用 GeoLite2 解析填充。
 * {@code eventTime} 用 epoch 毫秒（Long），Java 端转 {@code LocalDateTime}。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@Schema(description = "采集事件")
public class TrackEventDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("event_time")
    @Schema(description = "事件发生时间(epoch毫秒)")
    private Long eventTime;

    @JsonProperty("event_name")
    @Schema(description = "事件名")
    private String eventName;

    @JsonProperty("session_id")
    @Schema(description = "会话ID")
    private String sessionId;

    @JsonProperty("visitor_id")
    @Schema(description = "匿名访客ID")
    private String visitorId;

    @JsonProperty("user_id")
    @Schema(description = "登录用户ID")
    private String userId;

    @JsonProperty("org_code")
    @Schema(description = "店铺归属部门编码(快照)")
    private String orgCode;

    @JsonProperty("store_id")
    @Schema(description = "店铺ID")
    private String storeId;

    @JsonProperty("product_id")
    @Schema(description = "商品ID")
    private String productId;

    @JsonProperty("page_url")
    @Schema(description = "完整页面URL")
    private String pageUrl;

    @JsonProperty("page_path")
    @Schema(description = "归一化路径")
    private String pagePath;

    @Schema(description = "来源页")
    private String referrer;

    @JsonProperty("utm_source")
    @Schema(description = "UTM来源")
    private String utmSource;

    @JsonProperty("utm_medium")
    @Schema(description = "UTM媒介")
    private String utmMedium;

    @JsonProperty("utm_campaign")
    @Schema(description = "UTM活动")
    private String utmCampaign;

    @JsonProperty("device_type")
    @Schema(description = "设备类型")
    private String deviceType;

    @Schema(description = "操作系统")
    private String os;

    @Schema(description = "浏览器")
    private String browser;

    @Schema(description = "屏幕分辨率")
    private String screen;

    @Schema(description = "界面语言")
    private String lang;

    @JsonProperty("country_code")
    @Schema(description = "国家码(可空, 缺省由Java解析)")
    private String countryCode;

    @JsonProperty("country_name")
    @Schema(description = "国名(可空, 缺省由Java解析)")
    private String countryName;

    @JsonProperty("province_name")
    @Schema(description = "省/州名(可空, 缺省由Java解析)")
    private String provinceName;

    @JsonProperty("city_name")
    @Schema(description = "城市名(可空, 缺省由Java解析)")
    private String cityName;

    @JsonProperty("province_code")
    @Schema(description = "省/州编码(可空, 缺省由Java解析)")
    private String provinceCode;

    @Schema(description = "原始客户端IP")
    private String ip;

    @JsonProperty("duration_ms")
    @Schema(description = "停留时长(ms)")
    private Integer durationMs;

    @Schema(description = "扩展属性JSON对象")
    private Map<String, Object> props;
}
