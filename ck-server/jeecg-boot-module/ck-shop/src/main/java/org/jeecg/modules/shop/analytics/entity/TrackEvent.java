package org.jeecg.modules.shop.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户行为明细（ck_shop_track_event）。
 * </p>
 *
 * <p><b>刻意不设 {@code del_flag}、不加 {@code @TableLogic}</b>：行为明细是账本，
 * 删除本身就是数据完整性事故（与订单操作流水 / 库存流水同一原则）。要纠错只能追加，
 * 不能删行。按天归档、默认保留 30 天（可配），由独立的清理任务物理归档。
 *
 * <p>{@code orgCode} / {@code storeId} 是采集时刻的快照：事后店铺改绑部门不影响历史统计。
 * 地域字段由 Java 端用 GeoLite2 解析原始 IP 得到，BFF / 客户端不解析。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@TableName("ck_shop_track_event")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "用户行为明细")
public class TrackEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "事件发生时间")
    private LocalDateTime eventTime;

    @Schema(description = "事件名(受控枚举)")
    private String eventName;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "匿名访客ID")
    private String visitorId;

    @Schema(description = "登录用户ID")
    private String userId;

    @Schema(description = "店铺归属部门编码(快照)")
    private String orgCode;

    @Schema(description = "店铺ID")
    private String storeId;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "完整页面URL")
    private String pageUrl;

    @Schema(description = "归一化路径(去query)")
    private String pagePath;

    @Schema(description = "来源页")
    private String referrer;

    @Schema(description = "UTM来源")
    private String utmSource;

    @Schema(description = "UTM媒介")
    private String utmMedium;

    @Schema(description = "UTM活动")
    private String utmCampaign;

    @Schema(description = "设备类型: desktop/mobile/tablet")
    private String deviceType;

    @Schema(description = "操作系统")
    private String os;

    @Schema(description = "浏览器")
    private String browser;

    @Schema(description = "屏幕分辨率")
    private String screen;

    @Schema(description = "界面语言")
    private String lang;

    @Schema(description = "国家码(ISO 3166-1 alpha-2)")
    private String countryCode;

    @Schema(description = "国名")
    private String countryName;

    @Schema(description = "省/州名")
    private String provinceName;

    @Schema(description = "城市名")
    private String cityName;

    @Schema(description = "省/州编码(ISO 3166-2)")
    private String provinceCode;

    @Schema(description = "原始客户端IP(本期不脱敏)")
    private String ip;

    @Schema(description = "停留时长(仅page_leave, ms)")
    private Integer durationMs;

    @Schema(description = "扩展属性JSON(搜索词/曝光位置/点击元素等)")
    private String props;
}
