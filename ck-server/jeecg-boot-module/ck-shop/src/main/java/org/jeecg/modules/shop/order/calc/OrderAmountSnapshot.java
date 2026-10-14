package org.jeecg.modules.shop.order.calc;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 算钱结果快照（**内部模型，不直接出参**）。
 * </p>
 *
 * 结算预览、提交下单、后台改价复核，三处共用同一份结果结构，
 * 保证"预览看到的金额"与"下单写入的金额"来自同一段计算逻辑。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
public class OrderAmountSnapshot {

    private List<OrderCalcLine> lines = new ArrayList<>();

    /** 吊牌价合计 */
    private BigDecimal totalAmount;
    /** 成交金额合计（优惠前） */
    private BigDecimal productAmount;
    /** 促销优惠合计 */
    private BigDecimal promotionDiscount;
    private BigDecimal couponDiscount;
    private BigDecimal pointsDiscount;
    private BigDecimal memberDiscount;
    /** 优惠合计 */
    private BigDecimal discountAmount;
    /** 运费 */
    private BigDecimal freightAmount;
    private BigDecimal otherFee;
    /** 应付金额 */
    private BigDecimal payAmount;
    /** 成本合计 */
    private BigDecimal costAmount;
    /** 毛利 */
    private BigDecimal grossProfit;

    private Integer itemCount;
    private Integer totalQuantity;
    private BigDecimal totalWeight;

    /**
     * 明细规范化摘要的输入串：按 productId|skuId 升序拼接 productId|skuId|quantity。
     *
     * <p>排序是关键：用户勾选商品的顺序不同、前端传参顺序不同，
     * 摘要必须一致，否则同一个购物车会算出不同的 priceToken，
     * 用户"返回再进来"就会提示价格已变化。
     */
    public String canonicalItems() {
        return lines.stream()
                .sorted((a, b) -> a.lineKey().compareTo(b.lineKey()))
                .map(line -> line.lineKey() + "|" + (line.getQuantity() == null ? 0 : line.getQuantity()))
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }
}
