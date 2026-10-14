package org.jeecg.modules.shop.order.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.order.calc.OrderAmountSnapshot;
import org.jeecg.modules.shop.order.calc.OrderCalcLine;
import org.jeecg.modules.shop.order.constant.StockChangeType;
import org.jeecg.modules.shop.order.entity.ShopOrderItem;
import org.jeecg.modules.shop.order.entity.ShopStockLog;
import org.jeecg.modules.shop.order.mapper.ShopStockLogMapper;
import org.jeecg.modules.shop.order.mapper.ShopStockMapper;
import org.jeecg.modules.shop.order.service.IShopStockService;
import org.jeecg.modules.shop.order.service.OrderContext;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.ShopProductSkuCombination;
import org.jeecg.modules.shop.product.mapper.ShopProductMapper;
import org.jeecg.modules.shop.product.mapper.ShopProductSkuCombinationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * <p>
 * 库存服务实现。
 * </p>
 *
 * <h3>核心思想：让数据库来做判断</h3>
 * 每一类变动都是一条**带条件的 UPDATE**，服务层只负责"解释影响行数的含义"：
 * <pre>
 *   影响 1 行 → 成功
 *   影响 0 行 → 条件不成立（库存不够 / 已被释放 / 已扣过）
 * </pre>
 * 服务层不参与"够不够"的判断，因为那是竞态的根源。
 *
 * <h3>before/after 双写的由来</h3>
 * 每条流水都记录变动前后的 stock 与 frozen_stock。做法是**更新后回读一次**，
 * 再由变动方向反推 before（例如 OCCUPY 的 beforeFrozen = afterFrozen − qty）。
 * 这样只需要一次额外 SELECT，而不是"先 SELECT 加锁再 UPDATE"——
 * 后者会让热点商品的库存行被长时间持有锁，大促时直接打爆数据库
 * （文档 §3「热点商品/热点账户：行锁竞争」）。
 *
 * <p>代价是：极端并发下回读到的值可能已经包含了别人的变动，
 * 于是流水的 before/after 不再是"精确的相邻状态"。
 * 这不影响排查——排查关心的是"变动的方向与总量"，
 * 而且**真正权威的比对方式是"最后一条流水的 after_stock 与商品表当前值是否一致"**，
 * 这个性质不受影响。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Slf4j
@Service
public class ShopStockServiceImpl implements IShopStockService {

    @Autowired
    private ShopStockMapper stockMapper;

    @Autowired
    private ShopStockLogMapper stockLogMapper;

    @Autowired
    private ShopProductMapper productMapper;

    @Autowired
    private ShopProductSkuCombinationMapper skuMapper;

    // ==================================================================
    // 预占
    // ==================================================================

    @Override
    public void occupy(OrderContext context, OrderAmountSnapshot snapshot, String orderNo) {
        if (snapshot == null || snapshot.getLines() == null || snapshot.getLines().isEmpty()) {
            throw new JeecgBootException("订单明细为空，无法预占库存");
        }
        for (OrderCalcLine line : snapshot.getLines()) {
            if (line.getProduct() == null) {
                continue;
            }
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            if (quantity <= 0) {
                continue;
            }
            String productId = line.getProduct().getId();
            String skuId = StringUtils.trimToEmpty(line.getSkuId());
            String productName = line.getProduct().getSpuCode();

            int affected;
            if (StringUtils.isBlank(skuId)) {
                affected = stockMapper.occupyProductStock(productId, quantity);
            } else {
                affected = stockMapper.occupySkuStock(skuId, quantity);
            }
            if (affected != 1) {
                // 影响 0 行有三种可能：库存不足 / 商品或规格已下架 / 已删除。
                // 逐个回查给出**准确**原因——"库存不足"和"商品已下架"对用户
                // 是完全不同的行动指引（前者等补货，后者换商品）。
                throw new JeecgBootException(buildOccupyFailureMessage(line, productName));
            }
            writeLog(context.getStoreId(), productId, skuId, StockChangeType.OCCUPY, quantity,
                    0, quantity, orderNo, null, context.getUsername(),
                    "下单预占库存");
        }
    }

    private String buildOccupyFailureMessage(OrderCalcLine line, String productName) {
        String skuId = StringUtils.trimToEmpty(line.getSkuId());
        ShopProduct product = productMapper.selectById(line.getProduct().getId());
        if (product == null || (product.getDelFlag() != null && product.getDelFlag() == 1)) {
            return "商品[" + productName + "]已不存在，请重新选择";
        }
        if (!"Y".equals(product.getStatus())) {
            return "商品[" + productName + "]已下架，请重新选择";
        }
        if (StringUtils.isNotBlank(skuId)) {
            ShopProductSkuCombination sku = skuMapper.selectById(skuId);
            if (sku == null || (sku.getDelFlag() != null && sku.getDelFlag() == 1)) {
                return "商品[" + productName + "]的所选规格已删除，请重新选择";
            }
            if (!"Y".equals(sku.getSaleable())) {
                return "商品[" + productName + "]的所选规格已停售，请重新选择";
            }
            int remaining = remaining(sku.getStock(), sku.getFrozenStock());
            return "商品[" + productName + "]库存不足，当前仅剩 " + remaining + " 件";
        }
        int remaining = remaining(product.getStock(), product.getFrozenStock());
        return "商品[" + productName + "]库存不足，当前仅剩 " + remaining + " 件";
    }

    // ==================================================================
    // 释放
    // ==================================================================

    @Override
    public void release(String orderNo, List<ShopOrderItem> items, String operator, String reason) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (ShopOrderItem item : items) {
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            if (quantity <= 0) {
                continue;
            }
            String skuId = StringUtils.trimToEmpty(item.getSkuId());
            int affected = StringUtils.isBlank(skuId)
                    ? stockMapper.releaseProductStock(item.getProductId(), quantity)
                    : stockMapper.releaseSkuStock(skuId, quantity);
            if (affected == 0) {
                // 幂等：预占已经释放过（用户点两次取消、定时任务与用户操作撞车）。
                // 不报错，但记一条 WARN —— 如果这个警告持续出现，
                // 说明有别的路径在重复释放，需要查。
                log.warn("释放库存预占时影响 0 行（可能已释放过）：orderNo={}, productId={}, skuId={}, qty={}",
                        orderNo, item.getProductId(), skuId, quantity);
                continue;
            }
            writeLog(item.getStoreId(), item.getProductId(), skuId, StockChangeType.RELEASE, quantity,
                    0, -quantity, orderNo, null, operator, reason);
        }
    }

    // ==================================================================
    // 实扣
    // ==================================================================

    @Override
    public void deduct(String orderNo, List<ShopOrderItem> items, String bizId, String operator) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (ShopOrderItem item : items) {
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            if (quantity <= 0) {
                continue;
            }
            String skuId = StringUtils.trimToEmpty(item.getSkuId());
            int affected = StringUtils.isBlank(skuId)
                    ? stockMapper.deductProductStock(item.getProductId(), quantity)
                    : stockMapper.deductSkuStock(skuId, quantity);
            if (affected == 0) {
                // 这是**必须告警**的异常：钱收了，库存却没扣。
                // 预占被别处释放（定时任务误判超时、人工改数据）才会走到这里。
                // 不能静默：静默的后果是这件货还能再卖一次，属于超卖。
                log.error("【库存异常】支付成功后扣减库存失败，可能已被超卖！"
                                + "orderNo={}, productId={}, skuId={}, qty={}, paymentNo={}。"
                                + "请检查该订单的库存流水是否出现预先 RELEASE 的记录。",
                        orderNo, item.getProductId(), skuId, quantity, bizId);
                throw new JeecgBootException("订单[" + orderNo + "]的库存扣减异常，"
                        + "该商品可能已售罄。系统已拦截本次操作以避免超卖，请联系管理员处理。");
            }
            if (StringUtils.isNotBlank(skuId)) {
                // 多规格商品：必须同步 SPU 的汇总库存与累计销量，否则商品列表页
                // 会一直显示下单前的库存与销量（"列表有货、选规格没货"）。
                syncProductSummary(item.getProductId(), quantity, orderNo);
            }
            writeLog(item.getStoreId(), item.getProductId(), skuId, StockChangeType.DEDUCT, quantity,
                    -quantity, -quantity, orderNo, bizId, operator, "支付成功扣减库存");
        }
    }

    // ==================================================================
    // 回补
    // ==================================================================

    @Override
    public void restore(String orderNo, List<ShopOrderItem> items, String bizId, String operator) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (ShopOrderItem item : items) {
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            if (quantity <= 0) {
                continue;
            }
            String skuId = StringUtils.trimToEmpty(item.getSkuId());
            int affected = StringUtils.isBlank(skuId)
                    ? stockMapper.restoreProductStock(item.getProductId(), quantity)
                    : stockMapper.restoreSkuStock(skuId, quantity);
            if (affected == 0) {
                // 商品被删掉了（运营删除已下架商品）时的回补会失败。
                // 不抛异常阻断退款流程：退款是**对用户资金的承诺**，
                // 优先级高于库存数字的准确性。这里记 ERROR 让人工补录。
                log.error("【库存异常】退款回补库存失败，商品或规格可能已被删除："
                                + "orderNo={}, productId={}, skuId={}, qty={}, refundBiz={}。请人工核对库存。",
                        orderNo, item.getProductId(), skuId, quantity, bizId);
                continue;
            }
            if (StringUtils.isNotBlank(skuId)) {
                // 回补后同样要重算 SPU 汇总库存（销量不回滚，见 increaseProductSales 注释）
                syncProductSummary(item.getProductId(), 0, orderNo);
            }
            writeLog(item.getStoreId(), item.getProductId(), skuId, StockChangeType.RESTORE, quantity,
                    quantity, 0, orderNo, bizId, operator, "退款回补库存");
        }
    }

    // ==================================================================
    // 秒杀限量
    // ==================================================================

    @Override
    public boolean occupySeckill(String promotionId, int quantity, String orderNo, String operator) {
        if (StringUtils.isBlank(promotionId) || quantity <= 0) {
            return true;
        }
        int affected = stockMapper.occupyPromotionStock(promotionId, quantity);
        if (affected == 0) {
            // 影响 0 行有两种含义：已抢光，或该活动**不限量**（seckill_stock 为 NULL）。
            // 必须先读出活动来区分，否则"不限量活动"会被误判成"已抢光"，
            // 导致所有不限量的秒杀活动都下不了单。
            org.jeecg.modules.shop.promotion.entity.ShopPromotion promotion =
                    promotionMapper.selectById(promotionId);
            if (promotion != null && promotion.getSeckillStock() == null) {
                return true;
            }
            return false;
        }
        return true;
    }

    @Override
    public void releaseSeckill(String promotionId, int quantity, String orderNo, String operator) {
        if (StringUtils.isBlank(promotionId) || quantity <= 0) {
            return;
        }
        int affected = stockMapper.releasePromotionStock(promotionId, quantity);
        if (affected == 0) {
            // 不限量活动（seckill_stock 为 NULL）没有可归还的量，属于正常情况
            log.debug("归还秒杀限量影响 0 行（活动可能不限量）：promotionId={}, qty={}", promotionId, quantity);
        }
    }

    @Autowired
    private org.jeecg.modules.shop.promotion.mapper.ShopPromotionMapper promotionMapper;

    // ==================================================================
    // SPU 汇总同步
    // ==================================================================

    /**
     * 同步多规格商品在 SPU 上的汇总库存与累计销量。
     *
     * <p>{@code ck_shop_product.stock} 与 {@code sales_count} 是**汇总字段**
     * （商品列表页展示、按销量排序都用它们）。SKU 维度的库存变动如果不同步这两个字段，
     * 会出现两类用户可见的问题：
     * <ul>
     *   <li>列表页显示"有货"，点进详情选规格才发现售罄——用户会当成系统故障；</li>
     *   <li>多规格商品在"按销量排序"里永远排最后，因为它的销量一直是 0。</li>
     * </ul>
     *
     * <p>同步失败**不回滚主流程**（库存与资金才是主线），但会打 ERROR：
     * 汇总字段偏了可以事后重算，订单与资金错了无法挽回。
     *
     * @param quantity 需要累加到 SPU 销量的数量；0 表示只重算库存（退款回补场景）
     */
    private void syncProductSummary(String productId, int quantity, String orderNo) {
        try {
            stockMapper.syncProductStockFromSku(productId);
            if (quantity > 0) {
                stockMapper.increaseProductSales(productId, quantity);
            }
        } catch (Exception e) {
            log.error("【库存异常】同步 SPU 汇总库存/销量失败（不影响订单，但列表页库存可能不准）："
                            + "orderNo={}, productId={}, qty={}, err={}",
                    orderNo, productId, quantity, e.getMessage(), e);
        }
    }

    // ==================================================================
    // 流水
    // ==================================================================

    /**
     * 写一条库存流水。
     *
     * <p>流水写入失败**不允许**影响主流程：库存已经改了，因为记日志失败而回滚，
     * 会让用户下单失败却不知道为什么；而流水缺失只是排查时少一条线索。
     * 因此这里捕获异常只记 ERROR。
     *
     * <p>唯一的例外是"更新后回读失败"——那说明数据库连接已经有问题，
     * 此时连主流程也保不住，异常会自然向上抛。
     */
    private void writeLog(String storeId, String productId, String skuId, String changeType,
                          int quantity, int stockDelta, int frozenDelta,
                          String orderNo, String bizId, String operator, String remark) {
        try {
            int afterStock = 0;
            int afterFrozen = 0;
            ShopProduct product = productMapper.selectById(productId);
            if (StringUtils.isBlank(skuId)) {
                if (product != null) {
                    afterStock = product.getStock() == null ? 0 : product.getStock();
                    afterFrozen = product.getFrozenStock() == null ? 0 : product.getFrozenStock();
                }
            } else {
                ShopProductSkuCombination sku = skuMapper.selectById(skuId);
                if (sku != null) {
                    afterStock = sku.getStock() == null ? 0 : sku.getStock();
                    afterFrozen = sku.getFrozenStock() == null ? 0 : sku.getFrozenStock();
                }
            }
            ShopStockLog logEntry = new ShopStockLog()
                    .setStoreId(storeId)
                    .setProductId(productId)
                    .setSkuId(StringUtils.trimToEmpty(skuId))
                    .setChangeType(changeType)
                    .setQuantity(quantity)
                    .setStockDelta(stockDelta)
                    .setFrozenDelta(frozenDelta)
                    // before 由 after 反推：避免"先加锁读取再更新"带来的行锁竞争
                    .setAfterStock(afterStock)
                    .setBeforeStock(afterStock - stockDelta)
                    .setAfterFrozen(afterFrozen)
                    .setBeforeFrozen(afterFrozen - frozenDelta)
                    .setOrderNo(orderNo)
                    .setBizId(bizId)
                    .setOperator(operator)
                    .setRemark(remark)
                    .setCreateTime(new Date());
            stockLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("写库存流水失败（库存已变更，主流程继续）：orderNo={}, productId={}, skuId={}, type={}, err={}",
                    orderNo, productId, skuId, changeType, e.getMessage(), e);
        }
    }

    private int remaining(Integer stock, Integer frozen) {
        int s = stock == null ? 0 : stock;
        int f = frozen == null ? 0 : frozen;
        return Math.max(s - f, 0);
    }
}
