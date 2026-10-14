package org.jeecg.modules.shop.promotion.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * <p>
 * 店铺端「进行中的促销活动」简要信息 VO
 * </p>
 *
 * 只承载首页促销条需要的最小字段集：倒计时、活动名、跳转入口。
 * 活动 → 商品的明细价格不在这里返回（秒杀条不展示商品列表）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-13
 */

@Data
@Schema(description = "店铺端促销活动简要信息")
public class PromoBriefVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "活动ID")
    private String id;

    @Schema(description = "活动类型: DIRECT-直降 / PERCENT-折扣 / FULL_CUT-满减 / SECKILL-秒杀")
    private String type;

    @Schema(description = "活动名称，如「限时秒杀」")
    private String title;

    @Schema(description = "副标题/宣传语，可空")
    private String subTitle;

    /**
     * 秒杀条背景图 URL，可空。
     *
     * <p>为空时由前台用品牌色渐变兜底，后端**不提供默认图**：
     * 不同店铺品牌色不同，塞一张硬编码的图会让所有店铺长得一模一样。
     */
    @Schema(description = "秒杀条背景图URL; 为空时前台用品牌色渐变兜底")
    private String bgImage;

    /**
     * 活动落地页路径（不含语言前缀，由前端按当前语言补全）。
     * 为空时前端**不渲染**活动入口，避免死链。
     */
    @Schema(description = "活动落地页路径(不含语言前缀; 为空则前端不渲染入口)")
    private String linkUrl;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间")
    private Date startTime;

    /** 结束时间 —— 前端倒计时的**唯一依据** */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间(前端倒计时依据)")
    private Date endTime;

    @Schema(description = "直降金额 或 折扣率(如8.50=8.5折)，可空")
    private BigDecimal discountValue;

    @Schema(description = "满减门槛(FULL_CUT用)，可空")
    private BigDecimal thresholdAmount;

    @Schema(description = "秒杀限量(SECKILL用)，可空")
    private Integer seckillStock;

    @Schema(description = "该活动关联的第一个商品ID(用于「立即抢购」跳转)，没有则为 null")
    private String productId;

    @Schema(description = "该活动关联的商品总数(按 product_id 去重)，没有则为 0")
    private Integer productCount;
}
