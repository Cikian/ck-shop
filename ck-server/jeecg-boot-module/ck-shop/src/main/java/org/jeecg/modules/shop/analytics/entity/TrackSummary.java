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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户行为多维度汇总（ck_shop_track_summary）。
 * </p>
 *
 * <p>单表承载"小时 + 日 + 多维度"：{@code granularity} + {@code dimType} + {@code dimValue}
 * 三个字段区分桶，避免一张维度一张表。{@code orgCode='ALL'} 表示全局（超管视角）；
 * {@code productId='ALL'} 表示非商品维度。
 *
 * <p>漏斗各步（加购/下单/支付）由聚合任务从业务流水回填，保证与订单统计口径一致。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@TableName("ck_shop_track_summary")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "用户行为多维度汇总")
public class TrackSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "统计桶起点(整点或整天)")
    private LocalDateTime statTime;

    @Schema(description = "粒度: hour/day")
    private String granularity;

    @Schema(description = "店铺归属; ALL=全局")
    private String orgCode;

    @Schema(description = "商品维度; ALL=非商品维度")
    private String productId;

    @Schema(description = "维度: global/store/product/region/source/device/page")
    private String dimType;

    @Schema(description = "维度取值")
    private String dimValue;

    @Schema(description = "浏览量")
    private Long pv;

    @Schema(description = "去重访客数")
    private Long uv;

    @Schema(description = "去重IP数")
    private Long ipCount;

    @Schema(description = "会话数")
    private Long visitCount;

    @Schema(description = "跳出会话数")
    private Long bounceCount;

    @Schema(description = "停留总时长(ms)")
    private Long totalDurationMs;

    @Schema(description = "曝光数")
    private Long exposeCount;

    @Schema(description = "点击数")
    private Long clickCount;

    @Schema(description = "加购数(回填)")
    private Long addCartCount;

    @Schema(description = "下单数(回填)")
    private Long orderCount;

    @Schema(description = "支付数(回填)")
    private Long paidCount;

    @Schema(description = "下单额(回填)")
    private BigDecimal orderAmount;

    @Schema(description = "实收额(回填)")
    private BigDecimal paidAmount;
}
