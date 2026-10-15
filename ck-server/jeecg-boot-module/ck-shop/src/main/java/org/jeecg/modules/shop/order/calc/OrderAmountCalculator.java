package org.jeecg.modules.shop.order.calc;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.order.constant.ShopOrderConst;
import org.jeecg.modules.shop.order.entity.dto.OrderItemDTO;
import org.jeecg.modules.shop.order.service.OrderContext;
import org.jeecg.modules.shop.order.util.MoneyUtils;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.ShopProductSkuCombination;
import org.jeecg.modules.shop.product.mapper.ShopProductMapper;
import org.jeecg.modules.shop.product.mapper.ShopProductSkuCombinationMapper;
import org.jeecg.modules.shop.promotion.entity.ShopPromotion;
import org.jeecg.modules.shop.promotion.entity.ShopPromotionProduct;
import org.jeecg.modules.shop.promotion.mapper.ShopPromotionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jeecg.modules.shop.common.ShopOrgUtils;

/**
 * <p>
 * 订单算价器 —— **服务端算钱的唯一入口**。
 * </p>
 *
 * <h3>为什么算钱必须只有一处实现</h3>
 * 结算预览、提交下单、后台复核、退款金额计算，四处都必须用同一套规则。
 * 只要有两处，它们迟早会分叉，然后就是"我看到 100 元、扣了 120 元"这类
 * 最恶劣的用户体验（也是最常见的投诉与合规问题）。
 * 因此全部金额都在这里算一次，其它地方只做"取值"。
 *
 * <h3>优惠计算规则（不叠加，取优先级最高的一条）</h3>
 * 同一行商品只命中**一个**促销活动：
 * <ol>
 *   <li>先按 {@code store_id + 商品集合 + 生效时间} 取出所有进行中的活动，
 *       按 {@code sort_order DESC} 排序；</li>
 *   <li>逐行挑选第一个"作用于该商品或其 SKU"的活动，命中即停；</li>
 *   <li>活动价优先取关联表上的 {@code promo_price}（运营显式定价最准），
 *       没有则按类型公式计算：
 *       <ul>
 *         <li>{@code DIRECT} 直降：单价 = 原价 − {@code discount_value}</li>
 *         <li>{@code PERCENT} 折扣：单价 = 原价 × {@code discount_value / 10}
 *             （{@code discount_value = 8.50} 即 8.5 折）</li>
 *         <li>{@code SECKILL} 秒杀：优先 {@code promo_price}，否则按直降处理
 *             （单价 = 原价 − {@code discount_value}）</li>
 *         <li>{@code FULL_CUT} 满减：单价不变；当**本行成交金额** ≥
 *             {@code threshold_amount} 时整行减 {@code discount_value}</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p><b>为什么不叠加</b>：现有促销管理在保存时就做了冲突检测，
 * 同一商品同一时间本不该有两个活动。真出现重叠（历史脏数据）时，
 * 取一条比叠加更可预测——叠加会产生运营无法手工复算的金额，
 * 而"无法复算的价格"必然变成客服纠纷。
 *
 * <h3>运费</h3>
 * 系统目前**没有运费模板**（无按重/按件/按地区计费的模型）。
 * 因此这里只做两件事：
 * <ul>
 *   <li>全部商品都包邮 → 运费 0；</li>
 *   <li>否则取配置项 {@code ck.shop.order.default-freight}（默认 0）。</li>
 * </ul>
 * 接入运费模板时只需替换 {@link #calculateFreight} 一个方法，
 * 其余算价逻辑不受影响。这一步刻意不"编造"复杂的运费规则：
 * 编出来的规则一定是错的，而错的运费会直接变成资金差异。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Slf4j
@Component
public class OrderAmountCalculator {

    @Autowired
    private ShopProductMapper productMapper;

    @Autowired
    private ShopProductSkuCombinationMapper skuMapper;

    @Autowired
    private ShopPromotionMapper promotionMapper;

    /** 默认运费（元）。没有运费模板时用它兜底；0 表示不计运费。 */
    @Value("${ck.shop.order.default-freight:0}")
    private String defaultFreight;

    // ==================================================================
    // 对外入口
    // ==================================================================

    /**
     * 计算订单金额。
     *
     * <p><b>不抛"商品不可售"异常</b>：预览页面需要把不可售的明细标出来给用户看。
     * 数量超限、明细数量超限这类**入参本身非法**的情况仍然抛异常
     * （那不是业务状态，是请求有问题）。
     *
     * @param context 当前用户 + 店铺上下文
     * @param items   前端传来的明细（只有商品/SKU/数量，没有金额）
     */
    public OrderAmountSnapshot calculate(OrderContext context, List<OrderItemDTO> items) {
        List<OrderCalcLine> lines = loadLines(context, items);
        applyPromotions(context, lines);
        return assemble(lines);
    }

    /**
     * 判断快照里是否全部明细都可下单。提交订单前必须校验。
     */
    public boolean allAvailable(OrderAmountSnapshot snapshot) {
        for (OrderCalcLine line : snapshot.getLines()) {
            if (!Boolean.TRUE.equals(line.getAvailable())) {
                return false;
            }
        }
        return true;
    }

    /** 汇总第一条不可售原因，用于给用户一句能看懂的话 */
    public String firstInvalidReason(OrderAmountSnapshot snapshot) {
        for (OrderCalcLine line : snapshot.getLines()) {
            if (!Boolean.TRUE.equals(line.getAvailable())) {
                String name = line.getProduct() == null ? "商品" : line.getProduct().getSpuCode();
                return name + "：" + line.getInvalidReason();
            }
        }
        return null;
    }

    // ==================================================================
    // ① 加载与校验
    // ==================================================================

    private List<OrderCalcLine> loadLines(OrderContext context, List<OrderItemDTO> items) {
        if (context == null || StringUtils.isBlank(context.getUserId())) {
            throw new JeecgBootException("请先登录后再结算");
        }
        if (StringUtils.isBlank(context.getStoreOrgCode())) {
            throw new JeecgBootException("当前店铺尚未绑定部门，暂时无法下单");
        }
        if (items == null || items.isEmpty()) {
            throw new JeecgBootException("请先选择要结算的商品");
        }

        // 合并同一 SKU 的重复行：前端可能因为状态合并问题传两条一样的明细，
        // 合并是用户的本意（"买 3 件"），而拒绝会让用户无从下手。
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (OrderItemDTO item : items) {
            if (item == null || StringUtils.isBlank(item.getProductId())) {
                throw new JeecgBootException("结算明细中存在缺少商品ID的条目");
            }
            String skuId = StringUtils.trimToEmpty(item.getSkuId());
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            if (quantity < 1) {
                throw new JeecgBootException("购买数量必须大于 0");
            }
            if (quantity > ShopOrderConst.MAX_QUANTITY_PER_ITEM) {
                throw new JeecgBootException("单个商品最多购买 "
                        + ShopOrderConst.MAX_QUANTITY_PER_ITEM + " 件");
            }
            String key = StringUtils.trim(item.getProductId()) + "|" + skuId;
            merged.merge(key, quantity, Integer::sum);
        }
        if (merged.size() > ShopOrderConst.MAX_ITEM_TYPES) {
            throw new JeecgBootException("单笔订单最多包含 "
                    + ShopOrderConst.MAX_ITEM_TYPES + " 种商品");
        }

        List<OrderCalcLine> lines = new ArrayList<>(merged.size());
        for (Map.Entry<String, Integer> entry : merged.entrySet()) {
            String[] parts = entry.getKey().split("\\|", -1);
            String productId = parts[0];
            String skuId = parts[1];
            int quantity = entry.getValue();
            if (quantity > ShopOrderConst.MAX_QUANTITY_PER_ITEM) {
                throw new JeecgBootException("单个商品最多购买 "
                        + ShopOrderConst.MAX_QUANTITY_PER_ITEM + " 件（合并后为 " + quantity + " 件）");
            }
            lines.add(buildLine(context, productId, skuId, quantity));
        }
        return lines;
    }

    private OrderCalcLine buildLine(OrderContext context, String productId, String skuId, int quantity) {
        OrderCalcLine line = new OrderCalcLine()
                .setSkuId(skuId)
                .setQuantity(quantity);

        ShopProduct product = productMapper.selectById(productId);
        line.setProduct(product);

        if (product == null || (product.getDelFlag() != null && product.getDelFlag() == 1)) {
            return markInvalid(line, "商品不存在或已删除");
        }
        // 归属校验：商品必须落在当前店铺的部门可见域内。
        // 少了这条，构造一个别店的 productId 就能在 A 店下单买 B 店的货。
        String productOrg = StringUtils.trimToNull(product.getSysOrgCode());
        if (productOrg == null || !ShopOrgUtils.matchesOrg(productOrg, context.getStoreOrgCode())) {
            return markInvalid(line, "商品在当前店铺不存在或已下架");
        }
        if (!"Y".equals(product.getStatus())) {
            return markInvalid(line, "商品已下架");
        }

        // 有规格商品必须带 SKU；无规格商品必须不带 SKU。
        // 两个方向都堵住，才不会出现"未选规格就下单"的脏数据。
        List<ShopProductSkuCombination> combinations =
                skuMapper.selectByProductIdWithValues(productId);
        boolean hasSpecs = combinations != null && !combinations.isEmpty();

        BigDecimal unitPrice;
        BigDecimal costPrice;
        int available;

        if (hasSpecs) {
            if (StringUtils.isBlank(skuId)) {
                return markInvalid(line, "该商品有规格可选，请先选择规格");
            }
            ShopProductSkuCombination hit = combinations.stream()
                    .filter(it -> skuId.equals(it.getId()))
                    .findFirst().orElse(null);
            if (hit == null) {
                return markInvalid(line, "所选规格已不存在，请重新选择");
            }
            if (!"Y".equals(hit.getSaleable())) {
                return markInvalid(line, "所选规格已停售");
            }
            line.setSku(hit);
            unitPrice = hit.getPrice() != null ? hit.getPrice() : product.getPrice();
            costPrice = hit.getCostPrice() != null ? hit.getCostPrice() : product.getCostPrice();
            available = remaining(hit.getStock(), hit.getFrozenStock());
        } else {
            if (StringUtils.isNotBlank(skuId)) {
                return markInvalid(line, "该商品没有规格，无需选择规格");
            }
            unitPrice = product.getPrice();
            costPrice = product.getCostPrice();
            available = remaining(product.getStock(), product.getFrozenStock());
        }

        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            // 价格为负或缺失属于数据错误，绝不能让它流进订单
            return markInvalid(line, "商品价格异常，请联系客服");
        }

        line.setPrice(MoneyUtils.scale(unitPrice));
        line.setCostPrice(MoneyUtils.scale(costPrice));
        line.setOriginalPrice(MoneyUtils.scaleOrNull(product.getOriginalPrice()));
        line.setStock(available);
        if (available < quantity) {
            // 这里只是"标记"，不是拒绝：预览要让用户看到还剩几件
            line.setAvailable(Boolean.FALSE);
            line.setInvalidReason(available <= 0
                    ? "库存不足（已售罄）"
                    : "库存不足，当前仅剩 " + available + " 件");
        }

        // 价格与数量的基础计算（优惠在下一步叠加）
        line.setProductAmount(MoneyUtils.multiply(line.getPrice(), quantity));
        line.setTotalAmount(line.getOriginalPrice() == null
                ? line.getProductAmount()
                : MoneyUtils.multiply(line.getOriginalPrice(), quantity));
        line.setCostAmount(MoneyUtils.multiply(line.getCostPrice(), quantity));
        line.setPromotionDiscount(MoneyUtils.zero());
        // 暂存：discountAmount 稍后由促销计算覆盖
        line.setDiscountAmount(MoneyUtils.zero());
        line.setActualAmount(line.getProductAmount());
        return line;
    }

    private OrderCalcLine markInvalid(OrderCalcLine line, String reason) {
        line.setAvailable(Boolean.FALSE);
        line.setInvalidReason(reason);
        line.setStock(0);
        line.setPrice(MoneyUtils.zero());
        line.setCostPrice(MoneyUtils.zero());
        line.setProductAmount(MoneyUtils.zero());
        line.setTotalAmount(MoneyUtils.zero());
        line.setCostAmount(MoneyUtils.zero());
        line.setDiscountAmount(MoneyUtils.zero());
        line.setActualAmount(MoneyUtils.zero());
        line.setPromotionDiscount(MoneyUtils.zero());
        return line;
    }

    /** 可售量 = 实际库存 − 预占库存；负数归零（数据被改坏时也不能算出负的可售量） */
    private int remaining(Integer stock, Integer frozen) {
        int s = stock == null ? 0 : stock;
        int f = frozen == null ? 0 : frozen;
        int remaining = s - f;
        return Math.max(remaining, 0);
    }

    // ==================================================================
    // ② 促销计算
    // ==================================================================

    private void applyPromotions(OrderContext context, List<OrderCalcLine> lines) {
        List<String> productIds = new ArrayList<>();
        for (OrderCalcLine line : lines) {
            if (line.getProduct() != null && !productIds.contains(line.getProduct().getId())) {
                productIds.add(line.getProduct().getId());
            }
        }
        if (productIds.isEmpty()) {
            return;
        }

        List<ShopPromotion> promotions =
                promotionMapper.selectActiveByProducts(context.getStoreId(), productIds, new Date());
        if (promotions == null || promotions.isEmpty()) {
            return;
        }

        List<String> promotionIds = new ArrayList<>();
        for (ShopPromotion promotion : promotions) {
            promotionIds.add(promotion.getId());
        }
        List<ShopPromotionProduct> relations = promotionMapper.selectProductsByPromotionIds(promotionIds);

        // promotionId -> (productId|skuId -> relation)，便于按行 O(1) 命中
        Map<String, Map<String, ShopPromotionProduct>> relationIndex = new HashMap<>();
        if (relations != null) {
            for (ShopPromotionProduct relation : relations) {
                String skuKey = StringUtils.trimToEmpty(relation.getSkuId());
                relationIndex
                        .computeIfAbsent(relation.getPromotionId(), k -> new HashMap<>())
                        .put(relation.getProductId() + "|" + skuKey, relation);
            }
        }

        // promotions 已按 sort_order DESC 排好，逐行取第一条命中的活动即为"优先级最高"
        Map<String, ShopPromotion> promotionById = new HashMap<>();
        for (ShopPromotion promotion : promotions) {
            promotionById.put(promotion.getId(), promotion);
        }

        for (OrderCalcLine line : lines) {
            if (line.getProduct() == null) {
                continue;
            }
            ShopPromotion matched = null;
            ShopPromotionProduct matchedRelation = null;
            for (ShopPromotion promotion : promotions) {
                Map<String, ShopPromotionProduct> byKey = relationIndex.get(promotion.getId());
                if (byKey == null) {
                    continue;
                }
                // SKU 精确匹配优先于"作用于整个 SPU"的配置
                ShopPromotionProduct relation = byKey.get(line.getProduct().getId() + "|" + line.getSkuId());
                if (relation == null) {
                    relation = byKey.get(line.getProduct().getId() + "|");
                }
                if (relation != null) {
                    matched = promotion;
                    matchedRelation = relation;
                    break;
                }
            }
            if (matched == null) {
                continue;
            }
            applyPromotion(line, matched, matchedRelation);
        }
    }

    private void applyPromotion(OrderCalcLine line, ShopPromotion promotion, ShopPromotionProduct relation) {
        int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
        BigDecimal unitPrice = line.getPrice();
        BigDecimal lineAmount = line.getProductAmount();
        BigDecimal discount;

        String type = StringUtils.trimToEmpty(promotion.getType()).toUpperCase();
        BigDecimal promoPrice = relation == null ? null : relation.getPromoPrice();
        BigDecimal discountValue = promotion.getDiscountValue();

        if (promoPrice != null) {
            // 运营在关联表上显式配了活动价：以它为准（最准、最可解释）
            BigDecimal newUnit = MoneyUtils.scale(promoPrice);
            discount = MoneyUtils.subtractNotBelowZero(lineAmount,
                    MoneyUtils.multiply(newUnit, quantity));
        } else {
            switch (type) {
                case "PERCENT": {
                    // discountValue = 8.50 表示 8.5 折。缺失或非法时视为不打折，
                    // 而不是"按 0 元卖"——后者是灾难性的。
                    if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) <= 0) {
                        return;
                    }
                    BigDecimal rate = discountValue.divide(BigDecimal.TEN, 4, java.math.RoundingMode.HALF_UP);
                    if (rate.compareTo(BigDecimal.ONE) >= 0) {
                        // 折扣率 ≥ 10 折说明配置有误，按"不打折"处理并留日志，
                        // 不产生负优惠（负优惠等于给用户加钱）
                        log.warn("促销[{}]折扣率配置异常({}折)，按不打折处理", promotion.getId(), discountValue);
                        return;
                    }
                    discount = MoneyUtils.subtractNotBelowZero(lineAmount,
                            MoneyUtils.multiply(MoneyUtils.multiply(unitPrice, rate), quantity));
                    break;
                }
                case "FULL_CUT": {
                    BigDecimal threshold = promotion.getThresholdAmount();
                    if (threshold == null || lineAmount.compareTo(threshold) < 0) {
                        return;
                    }
                    // 满减金额不能超过本行金额，否则会出现负的应付金额
                    BigDecimal cut = MoneyUtils.scale(discountValue);
                    discount = cut.compareTo(lineAmount) > 0 ? lineAmount : cut;
                    break;
                }
                case "SECKILL":
                case "DIRECT":
                default: {
                    if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) <= 0) {
                        return;
                    }
                    BigDecimal newUnit = MoneyUtils.scale(unitPrice.subtract(discountValue));
                    if (newUnit.compareTo(BigDecimal.ZERO) < 0) {
                        newUnit = MoneyUtils.zero();
                    }
                    discount = MoneyUtils.subtractNotBelowZero(lineAmount,
                            MoneyUtils.multiply(newUnit, quantity));
                    break;
                }
            }
        }

        discount = MoneyUtils.subtractNotBelowZero(discount, BigDecimal.ZERO);
        if (!MoneyUtils.isPositive(discount)) {
            return;
        }

        line.setPromotionId(promotion.getId());
        line.setPromotionType(type);
        line.setPromotionTitle(promotion.getTitle());
        line.setPromotionInstanceId(promotion.getId());
        line.setPromotionDiscount(discount);
        line.setPromotionDiscountTotal(discount);
        line.setDiscountAmount(discount);
        line.setActualAmount(MoneyUtils.subtract(line.getProductAmount(), discount));
    }

    // ==================================================================
    // ③ 汇总
    // ==================================================================

    private OrderAmountSnapshot assemble(List<OrderCalcLine> lines) {
        OrderAmountSnapshot snapshot = new OrderAmountSnapshot().setLines(lines);

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal productAmount = BigDecimal.ZERO;
        BigDecimal promotionDiscount = BigDecimal.ZERO;
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal costAmount = BigDecimal.ZERO;
        int itemCount = 0;
        int totalQuantity = 0;

        for (OrderCalcLine line : lines) {
            // 不可售的行不计入金额：否则"应付 100 元里有 30 元是买不到的东西"，
            // 用户会以为自己要多付 30 元。
            if (!Boolean.TRUE.equals(line.getAvailable())) {
                continue;
            }
            itemCount++;
            totalQuantity += line.getQuantity() == null ? 0 : line.getQuantity();
            totalAmount = totalAmount.add(MoneyUtils.scale(line.getTotalAmount()));
            productAmount = productAmount.add(MoneyUtils.scale(line.getProductAmount()));
            promotionDiscount = promotionDiscount.add(MoneyUtils.scale(line.getPromotionDiscount()));
            discountAmount = discountAmount.add(MoneyUtils.scale(line.getDiscountAmount()));
            costAmount = costAmount.add(MoneyUtils.scale(line.getCostAmount()));
        }

        snapshot.setItemCount(itemCount);
        snapshot.setTotalQuantity(totalQuantity);
        snapshot.setTotalAmount(MoneyUtils.scale(totalAmount));
        snapshot.setProductAmount(MoneyUtils.scale(productAmount));
        snapshot.setPromotionDiscount(MoneyUtils.scale(promotionDiscount));
        snapshot.setCouponDiscount(MoneyUtils.zero());
        snapshot.setPointsDiscount(MoneyUtils.zero());
        snapshot.setMemberDiscount(MoneyUtils.zero());
        snapshot.setDiscountAmount(MoneyUtils.scale(discountAmount));
        snapshot.setCostAmount(MoneyUtils.scale(costAmount));
        snapshot.setTotalWeight(MoneyUtils.scale(BigDecimal.ZERO));

        BigDecimal freight = calculateFreight(lines);
        snapshot.setFreightAmount(freight);
        snapshot.setOtherFee(MoneyUtils.zero());

        BigDecimal payAmount = MoneyUtils.subtract(
                MoneyUtils.add(productAmount, freight),
                discountAmount);
        // 优惠超过商品金额 + 运费时（配置错误），应付额归零而不是负数：
        // 负的应付金额会让支付渠道直接报错，也可能被当成"退款"处理
        if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("订单应付金额计算为负（商品 {} + 运费 {} - 优惠 {}），已归零。"
                            + "这通常意味着促销配置有误，请检查活动金额。",
                    productAmount, freight, discountAmount);
            payAmount = MoneyUtils.zero();
        }
        snapshot.setPayAmount(MoneyUtils.scale(payAmount));
        snapshot.setGrossProfit(MoneyUtils.subtract(
                MoneyUtils.subtract(productAmount, discountAmount), costAmount));
        return snapshot;
    }

    /**
     * 运费计算。
     *
     * <p>当前实现是**显式的简化**：没有任何运费模板，
     * 全部商品包邮则 0，否则取配置的基础运费。
     * 刻意不编造"满 99 包邮"之类的规则——真实的运费规则一定与地区、重量、
     * 店铺策略相关，猜一个比不猜更危险（错的运费直接变成资金差异）。
     */
    private BigDecimal calculateFreight(List<OrderCalcLine> lines) {
        boolean hasChargeable = false;
        for (OrderCalcLine line : lines) {
            if (!Boolean.TRUE.equals(line.getAvailable())) {
                continue;
            }
            ShopProduct product = line.getProduct();
            if (product == null || !Boolean.TRUE.equals(product.getFreeShipping())) {
                hasChargeable = true;
                break;
            }
        }
        if (!hasChargeable) {
            return MoneyUtils.zero();
        }
        try {
            return MoneyUtils.scale(new BigDecimal(StringUtils.trimToEmpty(defaultFreight)));
        } catch (NumberFormatException e) {
            log.warn("配置项 ck.shop.order.default-freight 不是合法数字({})，本次按 0 运费处理", defaultFreight);
            return MoneyUtils.zero();
        }
    }
}
