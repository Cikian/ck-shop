package org.jeecg.modules.shop.order.calc;

import lombok.Data;
import lombok.experimental.Accessors;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.ShopProductSkuCombination;

import java.math.BigDecimal;

/**
 * <p>
 * 算钱结果中的一行（**内部模型，不直接出参**）。
 * </p>
 *
 * 与 {@code StoreOrderItemVO} 的区别：这里带**成本价**、带商品/SKU 实体引用、
 * 带命中的促销对象。成本属于经营敏感数据，绝不能出现在给买家的响应里，
 * 因此算钱阶段与出参阶段用两个不同的模型，靠类型把它们分开——
 * 而不是"记得在出参时把 cost 字段置空"。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
public class OrderCalcLine {

    private ShopProduct product;

    private ShopProductSkuCombination sku;

    /** SKU 标识；无规格商品为空串（与购物车/订单明细口径一致） */
    private String skuId = "";

    private Integer quantity;

    /** 原价/划线价（可为空） */
    private BigDecimal originalPrice;

    /** 成交单价：SKU 价优先，回退商品基准价 */
    private BigDecimal price;

    /** 成本单价 */
    private BigDecimal costPrice;

    // ---------- 计算结果 ----------

    private BigDecimal totalAmount;
    private BigDecimal productAmount;
    private BigDecimal promotionDiscount;
    private BigDecimal discountAmount;
    private BigDecimal actualAmount;
    private BigDecimal costAmount;

    // ---------- 命中的促销 ----------

    private String promotionId;
    private String promotionType;
    private String promotionTitle;
    private String promotionInstanceId;
    private BigDecimal promotionDiscountTotal;

    // ---------- 可售性（结算预览要"说清楚哪一件不能买"，因此算价阶段就判定） ----------

    /**
     * 是否可结算。
     *
     * <p>刻意不在算价阶段抛异常：用户需要看到"具体是哪一个商品没货了"，
     * 一句"结算失败"帮不上任何忙。因此这里只标记，
     * 由**提交订单**那条路径负责拒绝（下单时"有不可售明细"必须硬失败）。
     */
    private Boolean available = Boolean.TRUE;

    /** 不可结算的原因（商品下架/规格停售/库存不足/不属于本店） */
    private String invalidReason;

    /** 当前可售库存（已扣除他人预占） */
    private Integer stock;

    public String lineKey() {
        return product == null ? "" : product.getId() + "|" + (skuId == null ? "" : skuId);
    }
}
