package org.jeecg.modules.shop.promotion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * <p>
 * 促销活动-商品关联表（ck_shop_promotion_product）
 * </p>
 *
 * 一条记录 = "某活动作用于某商品的某 SKU"；{@code sku_id} 为空表示作用于整个 SPU。
 * 因此**同一个 product_id 可能有多行**（多 SKU 分别定价），
 * 统计"活动关联商品数"时必须按 product_id 去重。
 *
 * 注意：该表**没有 del_flag 列**，所以不要加 @TableLogic。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-13
 */

@Data
@TableName("ck_shop_promotion_product")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "促销活动-商品关联")
public class ShopPromotionProduct implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Schema(description = "活动ID")
    private String promotionId;

    @Schema(description = "商品SPU ID")
    private String productId;

    /** 为空 = 作用于整个 SPU */
    @Schema(description = "SKU ID(为空=作用于整个SPU)")
    private String skuId;

    /** 活动价(可空, 由活动类型 + discount_value 计算) */
    @Schema(description = "活动价(可空,由活动类型+discount_value计算)")
    private BigDecimal promoPrice;

    /** 活动限量 */
    @Schema(description = "活动限量")
    private Integer stockLimit;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "创建人")
    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建日期")
    private Date createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新日期")
    private Date updateTime;
}
