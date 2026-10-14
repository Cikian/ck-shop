package org.jeecg.modules.shop.order.entity;

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
 * 订单明细（ck_shop_order_item）—— 商品快照，只读凭证
 * </p>
 *
 * <h3>为什么全部字段都是"快照"</h3>
 * 订单是**历史凭证**，不是商品表的视图。运营把商品名从"iPhone 15"改成"iPhone 16"、
 * 把价格从 5999 改成 4999、把主图换掉、甚至把商品删除，
 * 历史订单里显示的必须仍然是**下单那一刻**的信息。
 * 如果明细只存 product_id 然后实时 join，那么：
 * <ul>
 *   <li>改价会让历史订单金额与商品表对不上，财务对账直接崩；</li>
 *   <li>删除商品会让历史订单显示空白；</li>
 *   <li>纠纷时无法举证"当时页面写的是什么"。</li>
 * </ul>
 * 因此这里把名称、规格、图片、单价、成本、分类全部复制一份。
 *
 * <p>{@code skuId} 用空串而不是 NULL 表示"无规格商品"，与购物车表
 * {@code ck_shop_cart_item} 的口径**完全一致**：MySQL 唯一索引里 NULL 彼此不相等，
 * 两处口径不一致会导致"购物车能加、订单里对不上"的诡异问题。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@TableName("ck_shop_order_item")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "订单明细")
public class ShopOrderItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Schema(description = "订单ID")
    private String orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "店铺ID(冗余)")
    private String storeId;

    @Schema(description = "店铺部门编码(冗余, 数据权限过滤免回表)")
    private String storeOrgCode;

    @Schema(description = "用户ID(冗余)")
    private String userId;

    // ==================================================================
    // 商品快照
    // ==================================================================

    @Schema(description = "商品ID(仅作追溯线索)")
    private String productId;

    @Schema(description = "SKU组合ID; 无规格为空串")
    private String skuId;

    @Schema(description = "SPU编码快照")
    private String spuCode;

    @Schema(description = "SKU编码快照")
    private String skuCode;

    @Schema(description = "商品名称快照")
    private String productName;

    @Schema(description = "规格文案快照")
    private String skuName;

    @Schema(description = "商品主图快照")
    private String productImage;

    @Schema(description = "单位快照")
    private String unit;

    /** 分类快照：让"按类目统计销量"不必回表 join 商品表（商品可能已改类目或已删除） */
    @Schema(description = "分类ID快照")
    private String categoryId;

    @Schema(description = "分类名称快照")
    private String categoryName;

    @Schema(description = "单件重量(kg)")
    private BigDecimal weight;

    // ==================================================================
    // 价格快照
    // ==================================================================

    @Schema(description = "原价/划线价")
    private BigDecimal originalPrice;

    @Schema(description = "成交单价(服务端计算并锁定)")
    private BigDecimal price;

    @Schema(description = "购买数量")
    private Integer quantity;

    @Schema(description = "原价小计")
    private BigDecimal totalAmount;

    @Schema(description = "成交小计")
    private BigDecimal productAmount;

    @Schema(description = "本行优惠金额")
    private BigDecimal discountAmount;

    @Schema(description = "本行实付 = 成交小计 - 本行优惠")
    private BigDecimal actualAmount;

    /**
     * 成本单价快照 —— 毛利统计的基础。
     *
     * <p>成本会被采购价变动影响，如果不在下单时锁定，
     * 事后算出来的"历史毛利"会随采购价改而变，月度报表永远对不上。
     */
    @Schema(description = "成本单价快照")
    private BigDecimal costPrice;

    @Schema(description = "成本小计")
    private BigDecimal costAmount;

    // ==================================================================
    // 命中的促销快照
    // ==================================================================

    @Schema(description = "命中的促销活动ID")
    private String promotionId;

    @Schema(description = "促销类型快照")
    private String promotionType;

    @Schema(description = "促销名称快照")
    private String promotionTitle;

    @Schema(description = "本行促销优惠")
    private BigDecimal promotionDiscount;

    /** 促销实例标识（秒杀场次等），限量回补时用它精确定位当时占用的是哪一场 */
    @Schema(description = "促销实例标识")
    private String promotionInstanceId;

    // ==================================================================
    // 售后
    // ==================================================================

    @Schema(description = "行退款状态: NONE/PARTIAL/FULL")
    private String refundStatus;

    @Schema(description = "已退数量")
    private Integer refundQuantity;

    @Schema(description = "已退金额")
    private BigDecimal refundAmount;

    // ==================================================================
    // 审计
    // ==================================================================

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

    @Schema(description = "归档标记: 0-正常, 1-已归档")
    private Integer delFlag;
}
