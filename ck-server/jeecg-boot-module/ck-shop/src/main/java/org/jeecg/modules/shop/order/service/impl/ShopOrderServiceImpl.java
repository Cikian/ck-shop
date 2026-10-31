package org.jeecg.modules.shop.order.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.cart.mapper.ShopCartItemMapper;
import org.jeecg.modules.shop.order.calc.OrderAmountCalculator;
import org.jeecg.modules.shop.order.calc.OrderAmountSnapshot;
import org.jeecg.modules.shop.order.calc.OrderCalcLine;
import org.jeecg.modules.shop.order.constant.OperatorType;
import org.jeecg.modules.shop.order.constant.OrderEventType;
import org.jeecg.modules.shop.order.constant.OrderStatus;
import org.jeecg.modules.shop.order.constant.PayStatus;
import org.jeecg.modules.shop.order.constant.RefundStatus;
import org.jeecg.modules.shop.order.constant.ShopOrderConst;
import org.jeecg.modules.shop.order.entity.ShopOrder;
import org.jeecg.modules.shop.order.entity.ShopOrderEvent;
import org.jeecg.modules.shop.order.entity.ShopOrderItem;
import org.jeecg.modules.shop.order.entity.ShopOrderPayment;
import org.jeecg.modules.shop.order.entity.ShopOrderRefund;
import org.jeecg.modules.shop.order.entity.ShopPromotionUserQuota;
import org.jeecg.modules.shop.order.entity.ShopUserAddress;
import org.jeecg.modules.shop.order.entity.dto.OrderPreviewDTO;
import org.jeecg.modules.shop.order.entity.dto.OrderQuery;
import org.jeecg.modules.shop.order.entity.dto.OrderShipDTO;
import org.jeecg.modules.shop.order.entity.dto.OrderSubmitDTO;
import org.jeecg.modules.shop.order.entity.vo.AddressVO;
import org.jeecg.modules.shop.order.entity.vo.AdminOrderDetailVO;
import org.jeecg.modules.shop.order.entity.vo.AdminOrderItemVO;
import org.jeecg.modules.shop.order.entity.vo.AdminOrderListVO;
import org.jeecg.modules.shop.order.entity.vo.OrderAmountVO;
import org.jeecg.modules.shop.order.entity.vo.OrderEventVO;
import org.jeecg.modules.shop.order.entity.vo.OrderPaymentVO;
import org.jeecg.modules.shop.order.entity.vo.OrderPreviewVO;
import org.jeecg.modules.shop.order.entity.vo.OrderRefundVO;
import org.jeecg.modules.shop.order.entity.vo.OrderSubmitVO;
import org.jeecg.modules.shop.order.entity.vo.StoreOrderDetailVO;
import org.jeecg.modules.shop.order.entity.vo.StoreOrderItemVO;
import org.jeecg.modules.shop.order.entity.vo.StoreOrderListVO;
import org.jeecg.modules.shop.order.mapper.ShopOrderItemMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderRefundMapper;
import org.jeecg.modules.shop.order.mapper.ShopPromotionUserQuotaMapper;
import org.jeecg.modules.shop.order.service.IShopOrderEventService;
import org.jeecg.modules.shop.order.service.IShopOrderService;
import org.jeecg.modules.shop.order.service.IShopPaymentService;
import org.jeecg.modules.shop.order.service.IShopStockService;
import org.jeecg.modules.shop.order.service.IShopUserAddressService;
import org.jeecg.modules.shop.order.service.OrderContext;
import org.jeecg.modules.shop.order.statemachine.OrderStateMachine;
import org.jeecg.modules.shop.order.util.MoneyUtils;
import org.jeecg.modules.shop.order.util.OrderNoGenerator;
import org.jeecg.modules.shop.order.util.OrderTimeRange;
import org.jeecg.modules.shop.order.util.PriceTokenUtils;
import org.jeecg.modules.shop.order.util.ShopAddressTextUtils;
import org.jeecg.modules.shop.order.util.ShopMaskUtils;
import org.jeecg.modules.shop.promotion.entity.ShopPromotion;
import org.jeecg.modules.shop.promotion.mapper.ShopPromotionMapper;
import org.jeecg.modules.shop.region.entity.RegionResolution;
import org.jeecg.modules.shop.region.service.IShopRegionService;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 订单服务实现。
 * </p>
 *
 * <h3>下单为什么必须是一个事务</h3>
 * "创建订单 + 写明细快照 + 预占库存 + 占用秒杀额度 + 写操作流水 + 清购物车"
 * 六件事必须同生共死。分成多个事务最典型的后果是
 * "库存占了但没有订单"——这些预占永远不会被释放，
 * 商品会慢慢变成"显示有货但下不了单"。
 *
 * <h3>所有状态变更都用 CAS</h3>
 * {@code UPDATE ... WHERE id = ? AND order_status = ? AND version = ?}，
 * 影响 0 行即视为并发冲突并报错。绝不允许"读出来判断一下再写回去"。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Slf4j
@Service
public class ShopOrderServiceImpl
        extends ServiceImpl<ShopOrderMapper, ShopOrder>
        implements IShopOrderService {

    @Autowired
    private ShopOrderMapper orderMapper;

    @Autowired
    private ShopOrderItemMapper orderItemMapper;

    @Autowired
    private ShopOrderRefundMapper refundMapper;

    @Autowired
    private ShopPromotionUserQuotaMapper quotaMapper;

    @Autowired
    private ShopPromotionMapper promotionMapper;

    @Autowired
    private org.jeecg.modules.shop.category.mapper.ShopCategoryMapper categoryMapper;

    @Autowired
    private ShopCartItemMapper cartItemMapper;

    @Autowired
    private OrderAmountCalculator amountCalculator;

    @Autowired
    private IShopStockService stockService;

    @Autowired
    private IShopOrderEventService eventService;

    @Autowired
    private IShopUserAddressService addressService;

    /**
     * 详情页要把地址快照里的当地文字现算成访客语言的整串（只读，不落库）。
     * 直接依赖 region 服务而不是回查地址簿：订单是快照，地址簿可能已经被改成别的样子了。
     */
    @Autowired
    private IShopRegionService regionService;

    @Autowired
    private IShopPaymentService paymentService;

    @Autowired
    private PriceTokenUtils priceTokenUtils;

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    /** 订单查询条件装配（关键字/状态/渠道/时间区间），从本类拆出 */
    @Autowired
    private ShopOrderQueryAssembler queryAssembler;

    /** 支付超时时间（分钟） */
    @Value("${ck.shop.order.pay-timeout-minutes:30}")
    private int payTimeoutMinutes;

    /** 关单前是否必须向渠道确认支付结果。默认 true；渠道查询接口故障时可临时关闭 */
    @Value("${ck.shop.order.timeout-close.require-query:true}")
    private boolean requireQueryBeforeClose;

    // ==================================================================
    // 结算预览
    // ==================================================================

    @Override
    public OrderPreviewVO preview(OrderContext context, OrderPreviewDTO dto) {
        OrderAmountSnapshot snapshot = amountCalculator.calculate(context,
                dto == null ? null : dto.getItems());
        return buildPreviewVO(context, snapshot);
    }

    private OrderPreviewVO buildPreviewVO(OrderContext context, OrderAmountSnapshot snapshot) {
        List<StoreOrderItemVO> items = new ArrayList<>();
        boolean allAvailable = true;
        for (OrderCalcLine line : snapshot.getLines()) {
            StoreOrderItemVO vo = new StoreOrderItemVO()
                    .setProductId(line.getProduct() == null ? null : line.getProduct().getId())
                    .setSkuId(line.getSkuId())
                    .setSkuCode(line.getSku() == null ? null : line.getSku().getSkuCode())
                    .setProductName(line.getProduct() == null ? null : line.getProduct().getSpuCode())
                    .setSkuName(line.getSku() == null ? null : line.getSku().getCombinationName())
                    .setProductImage(line.getSku() != null && StringUtils.isNotBlank(line.getSku().getImageUrl())
                            ? line.getSku().getImageUrl()
                            : (line.getProduct() == null ? null : line.getProduct().getMainImage()))
                    .setUnit(line.getProduct() == null ? null : line.getProduct().getUnit())
                    .setOriginalPrice(line.getOriginalPrice())
                    .setPrice(line.getPrice())
                    .setQuantity(line.getQuantity())
                    .setTotalAmount(line.getTotalAmount())
                    .setProductAmount(line.getProductAmount())
                    .setDiscountAmount(line.getDiscountAmount())
                    .setActualAmount(line.getActualAmount())
                    .setPromotionTitle(line.getPromotionTitle())
                    .setPromotionType(line.getPromotionType())
                    .setAvailable(line.getAvailable())
                    .setInvalidReason(line.getInvalidReason())
                    .setStock(line.getStock());
            items.add(vo);
            if (!Boolean.TRUE.equals(line.getAvailable())) {
                allAvailable = false;
            }
        }

        // 商品名称取翻译表的展示名（若存在），失败不影响结算
        fillProductNames(items);

        OrderAmountVO amount = new OrderAmountVO()
                .setTotalAmount(snapshot.getTotalAmount())
                .setProductAmount(snapshot.getProductAmount())
                .setPromotionDiscount(snapshot.getPromotionDiscount())
                .setCouponDiscount(snapshot.getCouponDiscount())
                .setPointsDiscount(snapshot.getPointsDiscount())
                .setMemberDiscount(snapshot.getMemberDiscount())
                .setDiscountAmount(snapshot.getDiscountAmount())
                .setFreightAmount(snapshot.getFreightAmount())
                .setOtherFee(snapshot.getOtherFee())
                .setPayAmount(snapshot.getPayAmount())
                .setItemCount(snapshot.getItemCount())
                .setTotalQuantity(snapshot.getTotalQuantity());

        String digest = priceTokenUtils.digestItems(snapshot.canonicalItems());
        String token = priceTokenUtils.issue(context.getUserId(), context.getStoreId(),
                snapshot.getPayAmount(), digest);
        long expireAt = System.currentTimeMillis()
                + Math.max(60, ShopOrderConst.PRICE_TOKEN_TTL_SECONDS) * 1000L;

        return new OrderPreviewVO()
                .setItems(items)
                .setAmount(amount)
                .setPriceToken(token)
                .setPriceTokenExpireAt(expireAt)
                .setHasInvalidItem(!allAvailable)
                .setAllAvailable(allAvailable);
    }

    /**
     * 用商品翻译表的标题补全展示名。
     *
     * <p>失败时**保持 SPU 编码**继续：结算页显示编码总比整页报错好。
     * 商品展示名的权威来源是翻译表，但它是"展示信息"，
     * 不该成为下单能力的单点依赖。
     */
    private void fillProductNames(List<StoreOrderItemVO> items) {
        if (items.isEmpty()) {
            return;
        }
        List<String> productIds = new ArrayList<>();
        for (StoreOrderItemVO item : items) {
            if (StringUtils.isNotBlank(item.getProductId())) {
                productIds.add(item.getProductId());
            }
        }
        Map<String, String> titleMap = loadProductTitles(productIds);
        for (StoreOrderItemVO item : items) {
            String title = titleMap.get(item.getProductId());
            if (StringUtils.isNotBlank(title)) {
                item.setProductName(title);
            }
        }
    }

    /**
     * 批量取商品展示标题（zh 翻译行）。
     *
     * <p>一次 IN 查询而不是逐条查：结算页往往十几行，N+1 会让首屏明显变慢。
     * 失败时返回空 Map 并打 WARN——结算能力不能因为翻译表异常而不可用。
     */
    private Map<String, String> loadProductTitles(List<String> productIds) {
        Map<String, String> map = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return map;
        }
        try {
            List<org.jeecg.modules.shop.product.entity.ShopProductTranslation> translations =
                    promotionMapper.selectProductTitles(productIds);
            if (translations != null) {
                for (org.jeecg.modules.shop.product.entity.ShopProductTranslation translation : translations) {
                    if (StringUtils.isNotBlank(translation.getProductId())
                            && StringUtils.isNotBlank(translation.getTitle())) {
                        map.put(translation.getProductId(), translation.getTitle());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询商品展示标题失败，将使用 SPU 编码兜底：{}", e.getMessage());
        }
        return map;
    }

    /** 批量取分类名称（快照进订单明细，让"按类目统计"不必回表 join 商品表） */
    private Map<String, String> loadCategoryNames(List<String> categoryIds) {
        Map<String, String> map = new HashMap<>();
        if (categoryIds == null || categoryIds.isEmpty()) {
            return map;
        }
        try {
            List<org.jeecg.modules.shop.category.entity.ShopCategory> categories =
                    categoryMapper.selectBatchIds(categoryIds);
            if (categories != null) {
                for (org.jeecg.modules.shop.category.entity.ShopCategory category : categories) {
                    if (StringUtils.isNotBlank(category.getId()) && StringUtils.isNotBlank(category.getName())) {
                        map.put(category.getId(), category.getName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询分类名称失败，订单明细的分类名称将为空（不影响按 category_id 统计）：{}",
                    e.getMessage());
        }
        return map;
    }

    // ==================================================================
    // 提交订单
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitVO submit(OrderContext context, OrderSubmitDTO dto) {
        if (context == null || StringUtils.isBlank(context.getUserId())) {
            throw new JeecgBootException("请先登录后再下单");
        }
        if (dto == null) {
            throw new JeecgBootException("下单参数缺失");
        }
        String idempotentKey = StringUtils.trimToNull(dto.getIdempotentKey());
        if (idempotentKey == null || idempotentKey.length() > 64) {
            // 不提供"没传就自动生成"的兜底：那等于永远不幂等，
            // 而用户连点两次就会真的下两单
            throw new JeecgBootException("缺少下单幂等键，请刷新页面后重试");
        }

        // ① 幂等前置：命中就直接返回原订单，不做任何写操作
        ShopOrder existed = orderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getIdempotentKey, idempotentKey)
                .last("LIMIT 1"));
        if (existed != null) {
            if (!existed.getUserId().equals(context.getUserId())) {
                // 幂等键撞到了别人的订单：说明幂等键被复用或伪造，必须显式拒绝，
                // 绝不能把别人的订单号返回给当前用户
                throw new JeecgBootException("下单请求异常，请刷新页面后重试");
            }
            log.info("命中下单幂等键，返回已存在订单：orderNo={}, key={}", existed.getOrderNo(), idempotentKey);
            return toSubmitVO(existed, true);
        }

        // ② 服务端重新算钱（唯一可信来源）
        OrderAmountSnapshot snapshot = amountCalculator.calculate(context, dto.getItems());
        if (!amountCalculator.allAvailable(snapshot)) {
            throw new JeecgBootException("部分商品无法结算：" + amountCalculator.firstInvalidReason(snapshot));
        }
        if (!MoneyUtils.isPositive(snapshot.getPayAmount())) {
            throw new JeecgBootException("订单应付金额为 0，无法下单。请检查商品价格或促销配置");
        }

        // ③ 验价：令牌 + 重算结果双重比对
        verifyPriceToken(context, dto.getPriceToken(), snapshot);

        // ④ 解析收货地址（快照）
        ShopUserAddress address = addressService.loadForOrder(context.getUserId(), dto.getAddressId());
        if (address == null) {
            validateInlineAddress(dto);
        }

        // ⑤ 组装订单
        String orderNo = generateUniqueOrderNo(context.getStoreId());
        Date now = new Date();
        Date payDeadline = new Date(now.getTime() + resolvePayTimeoutMinutes() * 60_000L);

        ShopOrder order = new ShopOrder()
                .setOrderNo(orderNo)
                .setSplitFlag(0)
                .setIdempotentKey(idempotentKey)
                .setStoreId(context.getStoreId())
                .setStoreName(context.getStoreName())
                .setStoreOrgCode(context.getStoreOrgCode())
                .setUserId(context.getUserId())
                .setUsername(context.getUsername())
                .setOrderStatus(OrderStatus.WAIT_PAY.name())
                .setPayStatus(PayStatus.UNPAID.name())
                .setRefundStatus(RefundStatus.NONE.name())
                .setVersion(0)
                .setTotalAmount(snapshot.getTotalAmount())
                .setProductAmount(snapshot.getProductAmount())
                .setPromotionDiscount(snapshot.getPromotionDiscount())
                .setCouponDiscount(snapshot.getCouponDiscount())
                .setPointsDiscount(snapshot.getPointsDiscount())
                .setMemberDiscount(snapshot.getMemberDiscount())
                .setDiscountAmount(snapshot.getDiscountAmount())
                .setFreightAmount(snapshot.getFreightAmount())
                .setOtherFee(snapshot.getOtherFee())
                .setPayAmount(snapshot.getPayAmount())
                .setPaidAmount(MoneyUtils.zero())
                .setRefundAmount(MoneyUtils.zero())
                .setCostAmount(snapshot.getCostAmount())
                .setGrossProfit(snapshot.getGrossProfit())
                .setCurrency("CNY")
                .setItemCount(snapshot.getItemCount())
                .setTotalQuantity(snapshot.getTotalQuantity())
                .setTotalWeight(snapshot.getTotalWeight())
                .setPayDeadline(payDeadline)
                .setOrderSource(normalizeSource(dto.getOrderSource(), context.getOrderSource()))
                .setClientIp(context.getClientIp())
                .setUserAgent(StringUtils.left(context.getUserAgent(), 500))
                .setReferrer(StringUtils.left(
                        StringUtils.defaultIfBlank(dto.getReferrer(), context.getReferrer()), 500))
                // channel_code / utm_source 都是 varchar(64)：渠道参数来自投放链接，长度不可控，一律截断
                .setChannelCode(StringUtils.left(
                        StringUtils.defaultIfBlank(dto.getChannelCode(), context.getChannelCode()), 64))
                .setUtmSource(StringUtils.left(
                        StringUtils.defaultIfBlank(dto.getUtmSource(), context.getUtmSource()), 64))
                .setBuyerRemark(StringUtils.left(StringUtils.trimToNull(dto.getBuyerRemark()), 500))
                .setDelFlag(0)
                .setCreateBy(context.getUsername())
                .setCreateTime(now)
                .setUpdateBy(context.getUsername())
                .setUpdateTime(now);

        applyAddressSnapshot(order, address, dto);

        // ⑥ 落库（幂等键唯一索引是最后一道防线）
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 并发重复提交：另一个线程刚插入了同一幂等键的订单。
            // 这里必须重新读出来返回，而不是报错——用户的本意就是下一单。
            ShopOrder concurrent = orderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                    .eq(ShopOrder::getIdempotentKey, idempotentKey)
                    .last("LIMIT 1"));
            if (concurrent == null) {
                throw e;
            }
            log.info("并发重复提交命中幂等键，返回已存在订单：orderNo={}", concurrent.getOrderNo());
            return toSubmitVO(concurrent, true);
        }

        // ⑦ 明细快照（商品名/分类名在下单这一刻复制，之后商品改名不影响历史订单）
        List<String> productIds = new ArrayList<>();
        List<String> categoryIds = new ArrayList<>();
        for (OrderCalcLine line : snapshot.getLines()) {
            if (line.getProduct() != null) {
                productIds.add(line.getProduct().getId());
                if (StringUtils.isNotBlank(line.getProduct().getCategoryId())) {
                    categoryIds.add(line.getProduct().getCategoryId());
                }
            }
        }
        Map<String, String> titleMap = loadProductTitles(productIds);
        Map<String, String> categoryMap = loadCategoryNames(categoryIds);

        List<ShopOrderItem> items = new ArrayList<>(snapshot.getLines().size());
        for (OrderCalcLine line : snapshot.getLines()) {
            items.add(buildOrderItem(order, line, now, context, titleMap, categoryMap));
        }
        for (ShopOrderItem item : items) {
            orderItemMapper.insert(item);
        }

        // ⑧ 预占库存（任一行失败即抛异常，整单回滚）
        stockService.occupy(context, snapshot, orderNo);

        // ⑨ 秒杀限量与限购额度
        occupySeckill(context, snapshot, orderNo, items);

        // ⑩ 操作流水
        eventService.recordStatusChange(order, OrderEventType.CREATE, null,
                OrderStatus.WAIT_PAY.name(), null, PayStatus.UNPAID.name(),
                OperatorType.USER, context.getUsername(),
                "创建订单，应付 " + snapshot.getPayAmount().toPlainString() + " 元",
                snapshot.getPayAmount(),
                buildCreateDetail(snapshot, context));

        // ⑪ 清理已下单的购物车条目（只清选中项，不能清空整个购物车）
        clearOrderedCartItems(context, dto.getCartItemIds());

        // ⑫ 地址使用统计
        if (address != null) {
            addressService.markUsed(context.getUserId(), address.getId());
        }

        return toSubmitVO(order, false);
    }

    private void verifyPriceToken(OrderContext context, String token, OrderAmountSnapshot snapshot) {
        PriceTokenUtils.PriceTokenPayload payload =
                priceTokenUtils.verify(token, context.getUserId(), context.getStoreId());
        String currentDigest = priceTokenUtils.digestItems(snapshot.canonicalItems());
        if (!StringUtils.equals(payload.getItemsDigest(), currentDigest)) {
            throw new JeecgBootException("结算商品已发生变化，请返回购物车重新确认后下单");
        }
        BigDecimal tokenAmount = MoneyUtils.scale(new BigDecimal(payload.getPayAmount()));
        if (!MoneyUtils.eq(tokenAmount, snapshot.getPayAmount())) {
            // 促销结束 / 商品调价 —— 必须让用户重新确认，不能静默按新价成交，
            // 也不能迁就旧价少收钱（那是资金损失）
            throw new JeecgBootException("商品价格或优惠已发生变化（原 "
                    + tokenAmount.toPlainString() + " 元，现 "
                    + snapshot.getPayAmount().toPlainString()
                    + " 元），请返回购物车重新确认后下单");
        }
    }

    private void validateInlineAddress(OrderSubmitDTO dto) {
        if (StringUtils.isBlank(dto.getReceiverName()) || StringUtils.isBlank(dto.getReceiverPhone())
                || StringUtils.isBlank(dto.getReceiverAddress())) {
            throw new JeecgBootException("请选择收货地址，或完整填写收货人、手机号与详细地址");
        }
        /*
         * 长度校验必须在下单前做：receiver_name/receiver_phone/receiver_address 是
         * varchar(50/30/255)，超长会直接抛 DataIntegrityViolationException，
         * 用户看到的是 500「系统异常」，整笔订单在事务里回滚。
         * 这里按列宽明确拒绝并给出可读文案（不静默截断收货信息）。
         */
        requireMaxLength("收货人姓名", dto.getReceiverName(), 50);
        requireMaxLength("收货人手机号", dto.getReceiverPhone(), 30);
        requireMaxLength("收货详细地址", dto.getReceiverAddress(), 255);
        // 手填国名落 receiver_country（varchar(100)）：与地址簿 AddressDTO.countryName 同一套口径，
        // 超长显式拒绝，不静默截断（截断后的国名在面单上是错的）
        requireMaxLength("手填国家/地区名称", dto.getReceiverCountryName(), 100);
    }

    private void requireMaxLength(String label, String value, int maxLength) {
        if (value != null && value.trim().length() > maxLength) {
            throw new JeecgBootException(label + "不能超过 " + maxLength + " 个字符");
        }
    }

    private void applyAddressSnapshot(ShopOrder order, ShopUserAddress address, OrderSubmitDTO dto) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (address != null) {
            /*
             * 地址簿虽然是共享表，但订单列宽更窄（name 50 / phone 30 / address 255），
             * 历史数据或其它入口写入的超长值不能把下单打成 500 —— 这里按订单列宽截断，
             * 完整地址仍保存在 addressSnapshotJson 里可追溯。
             */
            order.setReceiverName(StringUtils.left(address.getReceiverName(), 50))
                    .setReceiverPhone(StringUtils.left(address.getReceiverPhone(), 30))
                    // 地区码与国名整份拷贝：订单是快照，用户后来把地址簿改成别的国家也不影响这一单
                    .setReceiverCountryCode(address.getCountryCode())
                    .setReceiverCountry(StringUtils.left(address.getCountryName(), 100))
                    .setReceiverProvinceCode(address.getProvinceCode())
                    .setReceiverCityId(address.getCityId())
                    .setReceiverProvince(address.getProvince())
                    .setReceiverCity(address.getCity())
                    .setReceiverDistrict(address.getDistrict())
                    .setReceiverAddress(StringUtils.left(address.getDetailAddress(), 255))
                    .setReceiverPostalCode(address.getPostalCode())
                    .setReceiverTag(address.getAddressTag());
            snapshot.put("source", "addressBook");
            snapshot.put("addressId", address.getId());
            snapshot.put("receiverName", address.getReceiverName());
            snapshot.put("receiverPhone", address.getReceiverPhone());
            snapshot.put("countryCode", address.getCountryCode());
            snapshot.put("countryName", address.getCountryName());
            snapshot.put("provinceCode", address.getProvinceCode());
            snapshot.put("cityId", address.getCityId());
            snapshot.put("province", address.getProvince());
            snapshot.put("city", address.getCity());
            snapshot.put("district", address.getDistrict());
            snapshot.put("detailAddress", address.getDetailAddress());
            snapshot.put("postalCode", address.getPostalCode());
            snapshot.put("addressTag", address.getAddressTag());
        } else {
            /*
             * 内联地址（不改地址簿）：走与地址簿**完全同一套**级联校验与取名规则。
             * 如果这里另写一套，同一份输入在"存地址簿再下单"和"直接下单"两种路径下
             * 会落到不同的 province 文字/码上，对账时根本看不出哪个是对的。
             */
            RegionResolution region = addressService.resolveRegionForOrder(
                    dto.getReceiverCountryCode(), dto.getReceiverProvinceCode(), dto.getReceiverCityId());
            String province = region != null && StringUtils.isNotBlank(region.getProvinceName())
                    ? region.getProvinceName() : StringUtils.trimToNull(dto.getReceiverProvince());
            String city = region != null && StringUtils.isNotBlank(region.getCityName())
                    ? region.getCityName() : StringUtils.trimToNull(dto.getReceiverCity());
            /*
             * 国名与地址簿同一套优先级：有码 → 只认 region 解析出的当地语言国名（忽略前端传的，
             * 否则"码=TH、名=美国"这种自相矛盾的面单能被前端造出来）；
             * 无码但有手填名 → 存手填名（手填国家时前端给不出 ISO 码，这是唯一来源）；
             * 都空 → NULL（老形态）。
             */
            String countryName = region != null && StringUtils.isNotBlank(region.getCountryName())
                    ? region.getCountryName()
                    : StringUtils.left(StringUtils.trimToNull(dto.getReceiverCountryName()), 100);
            order.setReceiverName(StringUtils.trim(dto.getReceiverName()))
                    .setReceiverPhone(StringUtils.trim(dto.getReceiverPhone()))
                    .setReceiverCountryCode(region == null ? null : region.getCountryCode())
                    .setReceiverCountry(countryName)
                    .setReceiverProvinceCode(region == null ? null : region.getProvinceCode())
                    .setReceiverCityId(region == null ? null : region.getCityId())
                    .setReceiverProvince(province)
                    .setReceiverCity(city)
                    .setReceiverDistrict(StringUtils.trimToNull(dto.getReceiverDistrict()))
                    .setReceiverAddress(StringUtils.trim(dto.getReceiverAddress()))
                    .setReceiverPostalCode(StringUtils.trimToNull(dto.getReceiverPostalCode()))
                    .setReceiverTag(null);
            snapshot.put("source", "inline");
            snapshot.put("receiverName", dto.getReceiverName());
            snapshot.put("receiverPhone", dto.getReceiverPhone());
            snapshot.put("countryCode", region == null ? null : region.getCountryCode());
            // 写解析后的最终国名（含手填），不写前端原始入参：快照要能回答"面单上到底印了什么"
            snapshot.put("countryName", countryName);
            snapshot.put("provinceCode", region == null ? null : region.getProvinceCode());
            snapshot.put("cityId", region == null ? null : region.getCityId());
            snapshot.put("province", province);
            snapshot.put("city", city);
            snapshot.put("district", dto.getReceiverDistrict());
            snapshot.put("detailAddress", dto.getReceiverAddress());
            snapshot.put("postalCode", dto.getReceiverPostalCode());
        }
        snapshot.put("snapshotTime", new Date().getTime());
        // 完整地址 JSON 快照：用户之后改了地址簿，这里仍然是下单当时的样子
        order.setAddressSnapshot(JSON.toJSONString(snapshot));
    }

    private ShopOrderItem buildOrderItem(ShopOrder order, OrderCalcLine line, Date now,
                                         OrderContext context,
                                         Map<String, String> titleMap,
                                         Map<String, String> categoryMap) {
        String productId = line.getProduct().getId();
        String displayName = titleMap.get(productId);
        if (StringUtils.isBlank(displayName)) {
            // 没有翻译行时用 SPU 编码兜底：有编码总比空白强，运营能据此定位商品
            displayName = line.getProduct().getSpuCode();
        }
        return new ShopOrderItem()
                .setOrderId(order.getId())
                .setOrderNo(order.getOrderNo())
                .setStoreId(order.getStoreId())
                .setStoreOrgCode(order.getStoreOrgCode())
                .setUserId(order.getUserId())
                .setProductId(productId)
                .setSkuId(StringUtils.trimToEmpty(line.getSkuId()))
                .setSpuCode(line.getProduct().getSpuCode())
                .setSkuCode(line.getSku() == null ? null : line.getSku().getSkuCode())
                .setProductName(displayName)
                .setSkuName(line.getSku() == null ? null : line.getSku().getCombinationName())
                .setProductImage(line.getSku() != null && StringUtils.isNotBlank(line.getSku().getImageUrl())
                        ? line.getSku().getImageUrl() : line.getProduct().getMainImage())
                .setUnit(line.getProduct().getUnit())
                .setCategoryId(line.getProduct().getCategoryId())
                .setCategoryName(categoryMap.get(line.getProduct().getCategoryId()))
                .setWeight(MoneyUtils.scale(BigDecimal.ZERO))
                .setOriginalPrice(line.getOriginalPrice())
                .setPrice(line.getPrice())
                .setQuantity(line.getQuantity())
                .setTotalAmount(line.getTotalAmount())
                .setProductAmount(line.getProductAmount())
                .setDiscountAmount(line.getDiscountAmount())
                .setActualAmount(line.getActualAmount())
                .setCostPrice(line.getCostPrice())
                .setCostAmount(line.getCostAmount())
                .setPromotionId(line.getPromotionId())
                .setPromotionType(line.getPromotionType())
                .setPromotionTitle(line.getPromotionTitle())
                .setPromotionDiscount(line.getPromotionDiscount())
                .setPromotionInstanceId(line.getPromotionInstanceId())
                .setRefundStatus(RefundStatus.NONE.name())
                .setRefundQuantity(0)
                .setRefundAmount(MoneyUtils.zero())
                .setDelFlag(0)
                .setCreateBy(context.getUsername())
                .setCreateTime(now)
                .setUpdateBy(context.getUsername())
                .setUpdateTime(now);
    }

    /**
     * 占用秒杀限量与限购额度。
     *
     * <p>顺序刻意是"先限购、后限量"：限购是**用户维度**的资格（每人 N 件），
     * 限量是**商品维度**的库存。先检查资格再占库存，
     * 资格不足时不会白白占掉一件限量库存又马上还回去。
     */
    private void occupySeckill(OrderContext context, OrderAmountSnapshot snapshot,
                               String orderNo, List<ShopOrderItem> items) {
        Map<String, Integer> quantityByPromotion = new LinkedHashMap<>();
        for (OrderCalcLine line : snapshot.getLines()) {
            if (line.getPromotionId() == null) {
                continue;
            }
            if (!"SECKILL".equalsIgnoreCase(line.getPromotionType())) {
                continue;
            }
            quantityByPromotion.merge(line.getPromotionId(),
                    line.getQuantity() == null ? 0 : line.getQuantity(), Integer::sum);
        }
        if (quantityByPromotion.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Integer> entry : quantityByPromotion.entrySet()) {
            String promotionId = entry.getKey();
            int quantity = entry.getValue();
            ShopPromotion promotion = promotionMapper.selectById(promotionId);
            if (promotion == null) {
                // 活动在下单过程中被删除：视为无活动，不阻断下单
                // （用户看到的价格已经在算价阶段锁定，这里只是限量控制）
                log.warn("下单时促销活动已不存在：promotionId={}, orderNo={}", promotionId, orderNo);
                continue;
            }
            if (promotion.getLimitPerUser() != null && promotion.getLimitPerUser() > 0) {
                // 先确保额度行存在（幂等），再做带条件的原子累加
                quotaMapper.seedQuota(IdWorker.getIdStr(), promotionId, context.getUserId(),
                        context.getStoreId());
                int rows = quotaMapper.tryAccumulate(promotionId, context.getUserId(),
                        quantity, promotion.getLimitPerUser());
                if (rows == 0) {
                    throw new JeecgBootException("该秒杀活动每人限购 "
                            + promotion.getLimitPerUser() + " 件，您已达到上限");
                }
            }
            if (promotion.getSeckillStock() != null) {
                if (!stockService.occupySeckill(promotionId, quantity, orderNo, context.getUsername())) {
                    throw new JeecgBootException("秒杀商品已抢光，请关注下一场活动");
                }
            }
        }
    }

    /**
     * 清零购物车中**已下单**的条目。
     *
     * <p>只清 {@code cartItemIds} 指定的条目，绝不调用"清空购物车"：
     * 用户勾选了 3 件下单，购物车里另外 5 件没被选中的商品被顺手删掉，
     * 是购物车功能里最常见也最让用户恼火的 bug。
     */
    private void clearOrderedCartItems(OrderContext context, List<String> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (String id : cartItemIds) {
            if (StringUtils.isNotBlank(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        try {
            cartItemMapper.softDeleteByIds(context.getUserId(), context.getStoreId(), ids,
                    context.getUsername());
        } catch (Exception e) {
            // 清购物车失败**不影响下单结果**：订单已经创建、库存已经预占，
            // 因为清购物车失败而回滚订单，用户会看到"下单失败"却产生了订单，更糟
            log.warn("清理已下单的购物车条目失败（不影响下单）：userId={}, ids={}, err={}",
                    context.getUserId(), ids, e.getMessage());
        }
    }

    private String buildCreateDetail(OrderAmountSnapshot snapshot, OrderContext context) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("payAmount", snapshot.getPayAmount());
        detail.put("productAmount", snapshot.getProductAmount());
        detail.put("discountAmount", snapshot.getDiscountAmount());
        detail.put("freightAmount", snapshot.getFreightAmount());
        detail.put("totalQuantity", snapshot.getTotalQuantity());
        detail.put("clientIp", context.getClientIp());
        return JSON.toJSONString(detail);
    }

    private OrderSubmitVO toSubmitVO(ShopOrder order, boolean reused) {
        return new OrderSubmitVO()
                .setOrderNo(order.getOrderNo())
                .setOrderStatus(order.getOrderStatus())
                .setOrderStatusLabel(OrderStatus.labelOf(order.getOrderStatus()))
                .setPayAmount(MoneyUtils.scale(order.getPayAmount()))
                .setPayDeadline(order.getPayDeadline())
                .setRemainPaySeconds(remainSeconds(order.getPayDeadline()))
                .setCreateTime(order.getCreateTime())
                .setIdempotentReused(reused);
    }

    private String generateUniqueOrderNo(String storeId) {
        // 唯一性由数据库唯一索引保证；这里只生成，冲突时由 insert 抛 DuplicateKeyException。
        // 32 位随机空间 + 毫秒时间戳，实际冲突概率极低。
        return OrderNoGenerator.orderNo(storeId);
    }

    private int resolvePayTimeoutMinutes() {
        int minutes = payTimeoutMinutes;
        if (minutes < ShopOrderConst.MIN_PAY_TIMEOUT_MINUTES) {
            minutes = ShopOrderConst.MIN_PAY_TIMEOUT_MINUTES;
        }
        if (minutes > ShopOrderConst.MAX_PAY_TIMEOUT_MINUTES) {
            minutes = ShopOrderConst.MAX_PAY_TIMEOUT_MINUTES;
        }
        return minutes;
    }

    private String normalizeSource(String requested, String fallback) {
        String source = StringUtils.trimToEmpty(StringUtils.defaultIfBlank(requested, fallback)).toLowerCase();
        switch (source) {
            case ShopOrderConst.SOURCE_WEB:
            case ShopOrderConst.SOURCE_H5:
            case ShopOrderConst.SOURCE_APP:
            case ShopOrderConst.SOURCE_API:
                return source;
            default:
                return ShopOrderConst.SOURCE_API;
        }
    }

    private Long remainSeconds(Date deadline) {
        if (deadline == null) {
            return null;
        }
        return Math.max((deadline.getTime() - System.currentTimeMillis()) / 1000L, 0L);
    }

    // ==================================================================
    // 前台查询
    // ==================================================================

    @Override
    public Page<StoreOrderListVO> storePage(OrderContext context, OrderQuery query) {
        if (context == null || StringUtils.isBlank(context.getUserId())) {
            throw new JeecgBootException("请先登录");
        }
        OrderQuery condition = query == null ? new OrderQuery() : query;
        LambdaQueryWrapper<ShopOrder> wrapper = new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getUserId, context.getUserId())
                .eq(ShopOrder::getStoreId, context.getStoreId())
                // 买家侧**永远不显示归档订单**：归档是运营的列表清理动作，
                // 对买家来说"我的订单不见了"是无法接受的
                .eq(ShopOrder::getDelFlag, 0);
        queryAssembler.applyCommonFilters(wrapper, condition, false);
        wrapper.orderByDesc(ShopOrder::getCreateTime);

        Page<ShopOrder> entityPage = page(new Page<>(normalizePageNo(condition.getPageNo()),
                normalizePageSize(condition.getPageSize())), wrapper);
        return convertStorePage(entityPage);
    }

    private Page<StoreOrderListVO> convertStorePage(Page<ShopOrder> entityPage) {
        Page<StoreOrderListVO> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(),
                entityPage.getTotal());
        List<ShopOrder> orders = entityPage.getRecords();
        Map<String, List<ShopOrderItem>> itemMap = loadItems(orders);
        List<StoreOrderListVO> records = new ArrayList<>(orders.size());
        for (ShopOrder order : orders) {
            records.add(toStoreListVO(order, itemMap.get(order.getId())));
        }
        result.setRecords(records);
        return result;
    }

    private Map<String, List<ShopOrderItem>> loadItems(List<ShopOrder> orders) {
        Map<String, List<ShopOrderItem>> map = new HashMap<>();
        if (orders == null || orders.isEmpty()) {
            return map;
        }
        List<String> ids = new ArrayList<>(orders.size());
        for (ShopOrder order : orders) {
            ids.add(order.getId());
        }
        // 一次 IN 查询取全，杜绝 N+1
        List<ShopOrderItem> items = orderItemMapper.selectByOrderIds(ids);
        if (items != null) {
            for (ShopOrderItem item : items) {
                map.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
            }
        }
        return map;
    }

    private StoreOrderListVO toStoreListVO(ShopOrder order, List<ShopOrderItem> items) {
        boolean canPay = OrderStatus.WAIT_PAY.name().equals(order.getOrderStatus());
        boolean canCancel = canPay;
        boolean canReceive = OrderStatus.SHIPPED.name().equals(order.getOrderStatus());
        boolean canRefund = !PayStatus.UNPAID.name().equals(order.getPayStatus())
                && !RefundStatus.APPLYING.name().equals(order.getRefundStatus())
                && MoneyUtils.isPositive(MoneyUtils.subtract(order.getPaidAmount(), order.getRefundAmount()));

        return new StoreOrderListVO()
                .setOrderNo(order.getOrderNo())
                .setOrderStatus(order.getOrderStatus())
                .setOrderStatusLabel(OrderStatus.labelOf(order.getOrderStatus()))
                .setPayStatus(order.getPayStatus())
                .setRefundStatus(order.getRefundStatus())
                .setStoreName(order.getStoreName())
                .setPayAmount(MoneyUtils.scale(order.getPayAmount()))
                .setPaidAmount(MoneyUtils.scale(order.getPaidAmount()))
                .setRefundAmount(MoneyUtils.scale(order.getRefundAmount()))
                .setDiscountAmount(MoneyUtils.scale(order.getDiscountAmount()))
                .setFreightAmount(MoneyUtils.scale(order.getFreightAmount()))
                .setTotalQuantity(order.getTotalQuantity())
                .setItemCount(order.getItemCount())
                .setCreateTime(order.getCreateTime())
                .setPayDeadline(order.getPayDeadline())
                .setPayTime(order.getPayTime())
                .setRemainPaySeconds(canPay ? remainSeconds(order.getPayDeadline()) : null)
                .setLogisticsCompany(order.getLogisticsCompany())
                .setLogisticsNo(order.getLogisticsNo())
                .setCancelReason(order.getCancelReason())
                .setReceiverName(order.getReceiverName())
                // 买家看自己的订单：返回完整手机号。号码是他自己填的，
                // 脱敏只会让他无法核对收货信息
                .setReceiverPhone(order.getReceiverPhone())
                .setBuyerRemark(order.getBuyerRemark())
                .setArchived(order.getDelFlag() != null && order.getDelFlag() == 1)
                .setCanPay(canPay)
                .setCanCancel(canCancel)
                .setCanReceive(canReceive)
                .setCanRefund(canRefund)
                .setItems(toStoreItemVOs(items));
    }

    private List<StoreOrderItemVO> toStoreItemVOs(List<ShopOrderItem> items) {
        List<StoreOrderItemVO> list = new ArrayList<>();
        if (items == null) {
            return list;
        }
        for (ShopOrderItem item : items) {
            list.add(new StoreOrderItemVO()
                    .setId(item.getId())
                    .setProductId(item.getProductId())
                    .setSkuId(item.getSkuId())
                    .setSkuCode(item.getSkuCode())
                    .setProductName(item.getProductName())
                    .setSkuName(item.getSkuName())
                    .setProductImage(item.getProductImage())
                    .setUnit(item.getUnit())
                    .setCategoryName(item.getCategoryName())
                    .setOriginalPrice(item.getOriginalPrice())
                    .setPrice(item.getPrice())
                    .setQuantity(item.getQuantity())
                    .setTotalAmount(item.getTotalAmount())
                    .setProductAmount(item.getProductAmount())
                    .setDiscountAmount(item.getDiscountAmount())
                    .setActualAmount(item.getActualAmount())
                    .setPromotionTitle(item.getPromotionTitle())
                    .setPromotionType(item.getPromotionType())
                    .setRefundStatus(item.getRefundStatus())
                    .setRefundQuantity(item.getRefundQuantity())
                    .setRefundAmount(item.getRefundAmount()));
        }
        return list;
    }

    @Override
    public StoreOrderDetailVO storeDetail(OrderContext context, String orderNo, String locale) {
        ShopOrder order = requireOwnedOrder(context, orderNo);
        StoreOrderDetailVO vo = new StoreOrderDetailVO();
        StoreOrderListVO base = toStoreListVO(order,
                orderItemMapper.selectByOrderIds(java.util.Collections.singletonList(order.getId())));
        copyInto(vo, base);
        vo.setReceiverCountryCode(order.getReceiverCountryCode())
                .setReceiverCountry(order.getReceiverCountry())
                .setReceiverProvinceCode(order.getReceiverProvinceCode())
                .setReceiverCityId(order.getReceiverCityId())
                .setReceiverProvince(order.getReceiverProvince())
                .setReceiverCity(order.getReceiverCity())
                .setReceiverDistrict(order.getReceiverDistrict())
                .setReceiverAddress(order.getReceiverAddress())
                .setReceiverPostalCode(order.getReceiverPostalCode())
                .setReceiverTag(order.getReceiverTag())
                .setTotalAmount(MoneyUtils.scale(order.getTotalAmount()))
                .setProductAmount(MoneyUtils.scale(order.getProductAmount()))
                .setPromotionDiscount(MoneyUtils.scale(order.getPromotionDiscount()))
                .setPayChannel(order.getPayChannel())
                .setPayType(order.getPayType())
                .setShipRemark(order.getShipRemark());
        // 地址整串：拼装规则与地址簿共用一份实现，数据源只有订单快照列（不回查地址簿）
        fillReceiverAddressText(vo, order, locale);

        vo.setPayments(toPaymentVOs(paymentService.listByOrderId(order.getId())));
        vo.setRefunds(toRefundVOs(refundMapper.selectList(new LambdaQueryWrapper<ShopOrderRefund>()
                .eq(ShopOrderRefund::getOrderId, order.getId())
                .orderByAsc(ShopOrderRefund::getCreateTime))));
        // 买家只看生命周期节点，不看后台内部操作
        vo.setEvents(toEventVOs(eventService.listBuyerVisible(order.getId())));
        return vo;
    }

    /**
     * 补订单详情上的两串地址（当地语言面单串 + 访客语言展示串）。
     *
     * <h3>为什么只能取订单快照列</h3>
     * 订单是快照：用户下单后把地址簿改成别的国家，这一单的面单文案也不能变。
     * 所以这里一个字都不回查地址簿，只读 {@code receiver_*} 列。
     *
     * <h3>译名为什么是现算的</h3>
     * 快照存的是当地文字（快递员要看的写法），访客语言要靠
     * {@code receiverCountryCode / receiverProvinceCode / receiverCityId} 反查
     * {@code translations} 现算；<b>查不到就回退快照原文</b>（宁可给当地文字，
     * 也不能给 null 让前端显示空白——空白的地址等于没法核对）。
     */
    private void fillReceiverAddressText(StoreOrderDetailVO vo, ShopOrder order, String locale) {
        boolean international = ShopAddressTextUtils.hasCountry(
                order.getReceiverCountryCode(), order.getReceiverCountry());
        vo.setReceiverFullAddress(international
                ? ShopAddressTextUtils.joinInternational(order.getReceiverAddress(), order.getReceiverDistrict(),
                        order.getReceiverCity(), order.getReceiverProvince(),
                        order.getReceiverPostalCode(), order.getReceiverCountry())
                : ShopAddressTextUtils.joinLegacy(order.getReceiverProvince(), order.getReceiverCity(),
                        order.getReceiverDistrict(), order.getReceiverAddress()));
        if (!international) {
            // 老订单没有国家信息，也就没有码可反查译文：展示串就是面单串，与改造前完全一致
            vo.setReceiverFullAddressLocalized(vo.getReceiverFullAddress());
            return;
        }
        vo.setReceiverFullAddressLocalized(ShopAddressTextUtils.joinInternational(
                order.getReceiverAddress(), order.getReceiverDistrict(),
                resolveCityDisplay(order, locale), resolveProvinceDisplay(order, locale),
                order.getReceiverPostalCode(), resolveCountryDisplay(order, locale)));
    }

    /** 国名展示：有码查译文，查不到（或没码，例如手填国名）就用快照原文 */
    private String resolveCountryDisplay(ShopOrder order, String locale) {
        String code = StringUtils.trimToNull(order.getReceiverCountryCode());
        if (code == null || StringUtils.isBlank(locale)) {
            return order.getReceiverCountry();
        }
        Map<String, String> names = regionService.countryDisplayNames(
                java.util.Collections.singletonList(code), locale);
        // 译文表的 key 是大写 ISO 码，快照列如果存过小写（历史脏数据）也能命中
        String translated = names.get(code);
        if (translated == null) {
            translated = names.get(code.toUpperCase(java.util.Locale.ROOT));
        }
        return StringUtils.defaultIfBlank(translated, order.getReceiverCountry());
    }

    private String resolveProvinceDisplay(ShopOrder order, String locale) {
        String code = StringUtils.trimToNull(order.getReceiverProvinceCode());
        if (code == null || StringUtils.isBlank(locale)) {
            return order.getReceiverProvince();
        }
        String translated = regionService.stateDisplayNames(
                java.util.Collections.singletonList(code), locale).get(code);
        return StringUtils.defaultIfBlank(translated, order.getReceiverProvince());
    }

    private String resolveCityDisplay(ShopOrder order, String locale) {
        Integer cityId = order.getReceiverCityId();
        if (cityId == null || StringUtils.isBlank(locale)) {
            return order.getReceiverCity();
        }
        String translated = regionService.cityDisplayNames(
                java.util.Collections.singletonList(cityId), locale).get(cityId);
        return StringUtils.defaultIfBlank(translated, order.getReceiverCity());
    }

    /** 把列表 VO 的公共字段搬到详情 VO（手动复制而非继承反射，字段变动时编译期就能发现漏改） */
    private void copyInto(StoreOrderDetailVO detail, StoreOrderListVO base) {
        detail.setOrderNo(base.getOrderNo())
                .setOrderStatus(base.getOrderStatus())
                .setOrderStatusLabel(base.getOrderStatusLabel())
                .setPayStatus(base.getPayStatus())
                .setRefundStatus(base.getRefundStatus())
                .setStoreName(base.getStoreName())
                .setPayAmount(base.getPayAmount())
                .setPaidAmount(base.getPaidAmount())
                .setRefundAmount(base.getRefundAmount())
                .setDiscountAmount(base.getDiscountAmount())
                .setFreightAmount(base.getFreightAmount())
                .setTotalQuantity(base.getTotalQuantity())
                .setItemCount(base.getItemCount())
                .setCreateTime(base.getCreateTime())
                .setPayDeadline(base.getPayDeadline())
                .setPayTime(base.getPayTime())
                .setRemainPaySeconds(base.getRemainPaySeconds())
                .setLogisticsCompany(base.getLogisticsCompany())
                .setLogisticsNo(base.getLogisticsNo())
                .setCancelReason(base.getCancelReason())
                .setReceiverName(base.getReceiverName())
                .setReceiverPhone(base.getReceiverPhone())
                .setBuyerRemark(base.getBuyerRemark())
                .setArchived(base.getArchived())
                .setCanPay(base.getCanPay())
                .setCanCancel(base.getCanCancel())
                .setCanReceive(base.getCanReceive())
                .setCanRefund(base.getCanRefund())
                .setItems(base.getItems());
    }

    // ==================================================================
    // 前台操作
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByBuyer(OrderContext context, String orderNo, String reason) {
        ShopOrder order = requireOwnedOrder(context, orderNo);
        OrderStatus current = OrderStatus.of(order.getOrderStatus());
        if (current != OrderStatus.WAIT_PAY) {
            // 已支付订单不允许买家自助取消：钱已经收了，直接取消会产生
            // "订单关了钱没退"的资金事故。已付款的诉求走退款流程。
            throw new JeecgBootException(PayStatus.UNPAID.name().equals(order.getPayStatus())
                    ? "订单当前状态不允许取消"
                    : "订单已支付，如需退款请使用【申请退款】功能");
        }
        doCancel(order, org.jeecg.modules.shop.order.constant.OrderEvent.USER_CANCEL,
                "USER", context.getUsername(), StringUtils.left(StringUtils.defaultIfBlank(reason, "用户取消"), 255));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(OrderContext context, String orderNo) {
        ShopOrder order = requireOwnedOrder(context, orderNo);
        OrderStatus from = OrderStatus.of(order.getOrderStatus());
        OrderStatus to = OrderStateMachine.next(from, org.jeecg.modules.shop.order.constant.OrderEvent.CONFIRM_RECEIVE);
        Date now = new Date();
        int rows = orderMapper.update(new ShopOrder()
                        .setOrderStatus(to.name())
                        .setReceiveTime(now)
                        .setFinishTime(now)
                        .setUpdateBy(context.getUsername())
                        .setUpdateTime(now),
                new LambdaUpdateWrapper<ShopOrder>()
                        .eq(ShopOrder::getId, order.getId())
                        .eq(ShopOrder::getOrderStatus, from.name())
                        .eq(ShopOrder::getVersion, order.getVersion())
                        .setSql("version = version + 1"));
        if (rows == 0) {
            throw new JeecgBootException("订单状态已变化，请刷新后重试");
        }
        ShopOrder latest = orderMapper.selectById(order.getId());
        eventService.recordStatusChange(latest, OrderEventType.RECEIVE, from.name(), to.name(),
                latest.getPayStatus(), latest.getPayStatus(),
                OperatorType.USER, context.getUsername(), "买家确认收货", null);
    }

    // ==================================================================
    // 后台查询
    // ==================================================================

    @Override
    public Page<AdminOrderListVO> adminPage(OrderQuery query) {
        OrderQuery condition = query == null ? new OrderQuery() : query;
        List<String> scope = resolveScope(condition.getStoreId());
        LambdaQueryWrapper<ShopOrder> wrapper = new LambdaQueryWrapper<>();
        applyOrgScope(wrapper, scope);
        queryAssembler.applyCommonFilters(wrapper, condition, true);
        wrapper.orderByDesc(ShopOrder::getCreateTime);

        Page<ShopOrder> entityPage = page(new Page<>(normalizePageNo(condition.getPageNo()),
                normalizePageSize(condition.getPageSize())), wrapper);

        Page<AdminOrderListVO> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(),
                entityPage.getTotal());
        List<ShopOrder> orders = entityPage.getRecords();
        Map<String, List<ShopOrderItem>> itemMap = loadItems(orders);
        // 「待处理退款单数」一次性批量查出来。
        // 下面若每单各查一次就是 N+1：一页 100 单 = 100 次 COUNT，
        // 这是后台订单列表最容易被压垮的地方。
        Map<String, Integer> pendingRefundMap = loadPendingRefundCounts(orders);
        List<AdminOrderListVO> records = new ArrayList<>(orders.size());
        for (ShopOrder order : orders) {
            records.add(toAdminListVO(order, itemMap.get(order.getId()),
                    pendingRefundMap.getOrDefault(order.getId(), 0)));
        }
        result.setRecords(records);
        return result;
    }

    /**
     * 批量统计这些订单各自的「待处理（APPLYING）退款单」数量。
     *
     * <p>用一条 {@code IN (...)} 查询替代"每单一次 COUNT"，消除后台列表的 N+1；
     * 只 select order_id，不取整行，进一步减少回传数据量。
     */
    private Map<String, Integer> loadPendingRefundCounts(List<ShopOrder> orders) {
        Map<String, Integer> counts = new HashMap<>();
        if (orders == null || orders.isEmpty()) {
            return counts;
        }
        List<String> orderIds = new ArrayList<>(orders.size());
        for (ShopOrder order : orders) {
            if (order.getId() != null) {
                orderIds.add(order.getId());
            }
        }
        if (orderIds.isEmpty()) {
            return counts;
        }
        List<ShopOrderRefund> pending = refundMapper.selectList(new LambdaQueryWrapper<ShopOrderRefund>()
                .select(ShopOrderRefund::getOrderId)
                .in(ShopOrderRefund::getOrderId, orderIds)
                .eq(ShopOrderRefund::getStatus,
                        org.jeecg.modules.shop.order.constant.RefundOrderStatus.APPLYING.name()));
        for (ShopOrderRefund refund : pending) {
            counts.merge(refund.getOrderId(), 1, Integer::sum);
        }
        return counts;
    }

    private AdminOrderListVO toAdminListVO(ShopOrder order, List<ShopOrderItem> items) {
        // 单条路径（后台订单详情）走这里：只有一单，查一次不算 N+1
        Long pending = refundMapper.selectCount(new LambdaQueryWrapper<ShopOrderRefund>()
                .eq(ShopOrderRefund::getOrderId, order.getId())
                .eq(ShopOrderRefund::getStatus,
                        org.jeecg.modules.shop.order.constant.RefundOrderStatus.APPLYING.name()));
        return toAdminListVO(order, items, pending == null ? 0 : pending.intValue());
    }

    private AdminOrderListVO toAdminListVO(ShopOrder order, List<ShopOrderItem> items,
                                           int pendingRefundCount) {
        AdminOrderListVO vo = new AdminOrderListVO();
        copyOrderFields(order, vo);
        vo.setOrderStatusLabel(OrderStatus.labelOf(order.getOrderStatus()))
                .setPayStatusLabel(payStatusLabel(order.getPayStatus()))
                .setRefundStatusLabel(refundStatusLabel(order.getRefundStatus()))
                .setItems(toAdminItemVOs(items));
        // 后台列表脱敏：列表会被投屏、截图、多人查看，完整手机号没有必要出现在这里。
        // 需要完整号码时走"发货详情"，那条路径有权限校验与操作流水。
        vo.setReceiverPhone(ShopMaskUtils.maskPhone(order.getReceiverPhone()));
        vo.setPendingRefundCount(Math.max(pendingRefundCount, 0));
        return vo;
    }

    /** 把订单实体的字段整体搬到后台 VO（后台可见全部经营字段，包括成本与毛利） */
    private void copyOrderFields(ShopOrder order, AdminOrderListVO vo) {
        vo.setId(order.getId())
                .setOrderNo(order.getOrderNo())
                .setParentOrderNo(order.getParentOrderNo())
                .setSplitFlag(order.getSplitFlag())
                .setIdempotentKey(order.getIdempotentKey())
                .setStoreId(order.getStoreId())
                .setStoreName(order.getStoreName())
                .setStoreOrgCode(order.getStoreOrgCode())
                .setUserId(order.getUserId())
                .setUsername(order.getUsername())
                .setOrderStatus(order.getOrderStatus())
                .setPayStatus(order.getPayStatus())
                .setRefundStatus(order.getRefundStatus())
                .setVersion(order.getVersion())
                .setTotalAmount(order.getTotalAmount())
                .setProductAmount(order.getProductAmount())
                .setPromotionDiscount(order.getPromotionDiscount())
                .setCouponDiscount(order.getCouponDiscount())
                .setPointsDiscount(order.getPointsDiscount())
                .setMemberDiscount(order.getMemberDiscount())
                .setFreightAmount(order.getFreightAmount())
                .setOtherFee(order.getOtherFee())
                .setDiscountAmount(order.getDiscountAmount())
                .setPayAmount(order.getPayAmount())
                .setPaidAmount(order.getPaidAmount())
                .setRefundAmount(order.getRefundAmount())
                .setCostAmount(order.getCostAmount())
                .setGrossProfit(order.getGrossProfit())
                .setCurrency(order.getCurrency())
                .setItemCount(order.getItemCount())
                .setTotalQuantity(order.getTotalQuantity())
                .setTotalWeight(order.getTotalWeight())
                .setReceiverName(order.getReceiverName())
                .setReceiverPhone(order.getReceiverPhone())
                // 地区快照：后台对账/物流对接要看的是"这单寄到哪个国家的哪个城市"，
                // 只给省市区文字没法做国家维度统计
                .setReceiverCountryCode(order.getReceiverCountryCode())
                .setReceiverCountry(order.getReceiverCountry())
                .setReceiverProvinceCode(order.getReceiverProvinceCode())
                .setReceiverCityId(order.getReceiverCityId())
                .setReceiverProvince(order.getReceiverProvince())
                .setReceiverCity(order.getReceiverCity())
                .setReceiverDistrict(order.getReceiverDistrict())
                .setReceiverAddress(order.getReceiverAddress())
                .setReceiverPostalCode(order.getReceiverPostalCode())
                .setReceiverTag(order.getReceiverTag())
                .setAddressSnapshot(order.getAddressSnapshot())
                .setPayChannel(order.getPayChannel())
                .setPayType(order.getPayType())
                .setPaymentNo(order.getPaymentNo())
                .setPayDeadline(order.getPayDeadline())
                .setPayTime(order.getPayTime())
                .setShipTime(order.getShipTime())
                .setReceiveTime(order.getReceiveTime())
                .setCancelTime(order.getCancelTime())
                .setCloseTime(order.getCloseTime())
                .setFinishTime(order.getFinishTime())
                .setLogisticsCompany(order.getLogisticsCompany())
                .setLogisticsNo(order.getLogisticsNo())
                .setShipRemark(order.getShipRemark())
                .setCancelType(order.getCancelType())
                .setCancelReason(order.getCancelReason())
                .setOrderSource(order.getOrderSource())
                .setClientIp(order.getClientIp())
                .setUserAgent(order.getUserAgent())
                .setReferrer(order.getReferrer())
                .setChannelCode(order.getChannelCode())
                .setUtmSource(order.getUtmSource())
                .setBuyerRemark(order.getBuyerRemark())
                .setSellerRemark(order.getSellerRemark())
                .setCreateBy(order.getCreateBy())
                .setCreateTime(order.getCreateTime())
                .setUpdateBy(order.getUpdateBy())
                .setUpdateTime(order.getUpdateTime())
                .setDelFlag(order.getDelFlag());
    }

    private List<AdminOrderItemVO> toAdminItemVOs(List<ShopOrderItem> items) {
        List<AdminOrderItemVO> list = new ArrayList<>();
        if (items == null) {
            return list;
        }
        for (ShopOrderItem item : items) {
            AdminOrderItemVO vo = new AdminOrderItemVO();
            org.springframework.beans.BeanUtils.copyProperties(item, vo);
            vo.setRefundStatusLabel(refundStatusLabel(item.getRefundStatus()));
            // 本行可退金额由服务端算好下发，避免前端自己减算错
            vo.setRefundableAmount(MoneyUtils.subtract(
                    MoneyUtils.scale(item.getActualAmount()), MoneyUtils.scale(item.getRefundAmount())));
            list.add(vo);
        }
        return list;
    }

    @Override
    public AdminOrderDetailVO adminDetail(String orderNo) {
        ShopOrder order = getByOrderNo(orderNo);
        if (order == null) {
            throw new JeecgBootException("订单不存在：" + orderNo);
        }
        orgScopeResolver.checkAccessible(order.getStoreOrgCode(), "订单");

        AdminOrderListVO base = toAdminListVO(order,
                orderItemMapper.selectByOrderIds(java.util.Collections.singletonList(order.getId())));
        AdminOrderDetailVO vo = new AdminOrderDetailVO();
        org.springframework.beans.BeanUtils.copyProperties(base, vo);
        // 详情回填**完整收货手机号**：列表脱敏是为了防投屏/截图泄露，
        // 而详情页（shop-order:detail 权限）是商家发货时真正要打电话的那条路径——
        // 这里继续给掩码会导致**商家根本拿不到收货人电话，发不了货**。
        // 因此脱敏规则的边界是"列表脱敏、详情明文"，并且两条路径的权限码不同。
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setPayments(toPaymentVOs(paymentService.listByOrderId(order.getId())));
        vo.setRefunds(toRefundVOs(refundMapper.selectList(new LambdaQueryWrapper<ShopOrderRefund>()
                .eq(ShopOrderRefund::getOrderId, order.getId())
                .orderByAsc(ShopOrderRefund::getCreateTime))));
        // 后台看**全部**流水，包括内部操作（改备注、人工补单、掉单告警）
        vo.setEvents(toEventVOs(eventService.listByOrderId(order.getId())));
        vo.setAddressSnapshotJson(order.getAddressSnapshot());
        return vo;
    }

    private List<OrderPaymentVO> toPaymentVOs(List<ShopOrderPayment> payments) {
        List<OrderPaymentVO> list = new ArrayList<>();
        if (payments == null) {
            return list;
        }
        for (ShopOrderPayment payment : payments) {
            OrderPaymentVO vo = new OrderPaymentVO();
            org.springframework.beans.BeanUtils.copyProperties(payment, vo);
            vo.setStatusLabel(paymentStatusLabel(payment.getStatus()));
            vo.setRefundableAmount(paymentService.refundableAmount(payment));
            list.add(vo);
        }
        return list;
    }

    private List<OrderRefundVO> toRefundVOs(List<ShopOrderRefund> refunds) {
        List<OrderRefundVO> list = new ArrayList<>();
        if (refunds == null) {
            return list;
        }
        for (ShopOrderRefund refund : refunds) {
            OrderRefundVO vo = new OrderRefundVO();
            org.springframework.beans.BeanUtils.copyProperties(refund, vo);
            vo.setStatusLabel(refundOrderStatusLabel(refund.getStatus()));
            vo.setRefundTypeLabel(org.jeecg.modules.shop.order.constant.RefundType
                    .of(refund.getRefundType()).getLabel());
            list.add(vo);
        }
        return list;
    }

    private List<OrderEventVO> toEventVOs(List<ShopOrderEvent> events) {
        List<OrderEventVO> list = new ArrayList<>();
        if (events == null) {
            return list;
        }
        for (ShopOrderEvent event : events) {
            OrderEventVO vo = new OrderEventVO();
            org.springframework.beans.BeanUtils.copyProperties(event, vo);
            vo.setEventTypeLabel(eventTypeLabel(event.getEventType()));
            vo.setOperatorTypeLabel(operatorTypeLabel(event.getOperatorType()));
            vo.setOccurredTime(event.getCreateTime());
            list.add(vo);
        }
        return list;
    }

    // ==================================================================
    // 后台操作
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ship(String orderNo, OrderShipDTO dto) {
        if (dto == null) {
            throw new JeecgBootException("发货参数缺失");
        }
        ShopOrder order = requireAdminOrder(orderNo);
        boolean hasCompany = StringUtils.isNotBlank(dto.getLogisticsCompany());
        boolean hasNo = StringUtils.isNotBlank(dto.getLogisticsNo());
        if (hasCompany != hasNo) {
            // 只填一个会让买家拿着空单号去查物流，客服必然收到投诉
            throw new JeecgBootException("物流公司与物流单号需要同时填写，或同时留空（虚拟发货）");
        }

        OrderStatus from = OrderStatus.of(order.getOrderStatus());
        OrderStatus to = OrderStateMachine.next(from, org.jeecg.modules.shop.order.constant.OrderEvent.SHIP);
        Date now = new Date();
        int rows = orderMapper.update(new ShopOrder()
                        .setOrderStatus(to.name())
                        .setShipTime(now)
                        .setLogisticsCompany(StringUtils.trimToNull(dto.getLogisticsCompany()))
                        .setLogisticsNo(StringUtils.trimToNull(dto.getLogisticsNo()))
                        .setShipRemark(StringUtils.left(StringUtils.trimToNull(dto.getShipRemark()), 255))
                        .setUpdateBy(currentOperator())
                        .setUpdateTime(now),
                new LambdaUpdateWrapper<ShopOrder>()
                        .eq(ShopOrder::getId, order.getId())
                        .eq(ShopOrder::getOrderStatus, from.name())
                        .eq(ShopOrder::getVersion, order.getVersion())
                        .setSql("version = version + 1"));
        if (rows == 0) {
            // CAS 失败：另一个客服刚发过货。重复发货是真实的运营事故（同一批货发两次）
            throw new JeecgBootException("订单状态已变化（可能已被其他客服发货），请刷新后重试");
        }
        ShopOrder latest = orderMapper.selectById(order.getId());
        eventService.recordStatusChange(latest, OrderEventType.SHIP, from.name(), to.name(),
                latest.getPayStatus(), latest.getPayStatus(), OperatorType.ADMIN, currentOperator(),
                "商家发货"
                        + (hasNo ? "，物流：" + dto.getLogisticsCompany() + " " + dto.getLogisticsNo() : "（无需物流）"),
                null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminClose(String orderNo, String reason) {
        ShopOrder order = requireAdminOrder(orderNo);
        OrderStatus current = OrderStatus.of(order.getOrderStatus());
        if (current.isFinal()) {
            throw new JeecgBootException("订单已处于终态[" + current.getLabel() + "]，无需关闭");
        }
        if ("WAIT_PAY".equals(order.getOrderStatus())) {
            doCancel(order, org.jeecg.modules.shop.order.constant.OrderEvent.ADMIN_CANCEL,
                    "ADMIN", currentOperator(),
                    StringUtils.left(StringUtils.defaultIfBlank(reason, "管理员关闭"), 255));
            return;
        }
        // 已支付：**必须先全额退款**，否则会出现"订单关了钱没退"
        BigDecimal paid = MoneyUtils.scale(order.getPaidAmount());
        BigDecimal refunded = MoneyUtils.scale(order.getRefundAmount());
        if (MoneyUtils.lt(refunded, paid)) {
            throw new JeecgBootException("订单已收款 " + paid.toPlainString()
                    + " 元，已退 " + refunded.toPlainString()
                    + " 元。关闭已支付订单前必须先完成全额退款，否则会出现【订单已关闭但资金未退回】的事故");
        }
        doCancel(order, org.jeecg.modules.shop.order.constant.OrderEvent.ADMIN_CANCEL,
                "ADMIN", currentOperator(),
                StringUtils.left(StringUtils.defaultIfBlank(reason, "管理员关闭（已全额退款）"), 255));
    }

    /** 取消/关闭订单的统一实现：CAS 改状态 → 释放库存 → 关闭支付单 → 写流水 */
    private void doCancel(ShopOrder order, org.jeecg.modules.shop.order.constant.OrderEvent event,
                          String operatorType, String operator, String reason) {
        OrderStatus from = OrderStatus.of(order.getOrderStatus());
        OrderStatus to = OrderStateMachine.next(from, event);
        boolean timeout = event == org.jeecg.modules.shop.order.constant.OrderEvent.TIMEOUT_CLOSE;
        Date now = new Date();

        ShopOrder patch = new ShopOrder()
                .setOrderStatus(to.name())
                .setCancelType(timeout ? "TIMEOUT" : ("ADMIN".equals(operatorType) ? "ADMIN" : "USER"))
                .setCancelReason(reason)
                .setFinishTime(now)
                .setUpdateBy(operator)
                .setUpdateTime(now);
        if (timeout) {
            patch.setCloseTime(now);
        } else {
            patch.setCancelTime(now);
        }

        int rows = orderMapper.update(patch, new LambdaUpdateWrapper<ShopOrder>()
                .eq(ShopOrder::getId, order.getId())
                .eq(ShopOrder::getOrderStatus, from.name())
                .eq(ShopOrder::getVersion, order.getVersion())
                .setSql("version = version + 1"));
        if (rows == 0) {
            // CAS 失败最可能的原因是"用户最后一秒付款成功了"——此时绝不能关单
            throw new JeecgBootException("订单状态已变化（可能刚刚支付成功），请刷新页面查看最新状态");
        }

        List<ShopOrderItem> items = orderItemMapper.selectByOrderIds(
                java.util.Collections.singletonList(order.getId()));
        // 释放预占：商品回到可售
        stockService.release(order.getOrderNo(), items, operator, timeout ? "超时关闭释放库存" : "取消订单释放库存");
        // 归还秒杀限量
        releaseSeckill(items, order.getOrderNo(), operator);

        // 关闭未结算的支付单（含渠道侧）
        List<ShopOrderPayment> settleable = paymentService.listSettleable(order.getId());
        for (ShopOrderPayment payment : settleable) {
            try {
                paymentService.closePayment(payment, reason, operator);
            } catch (Exception e) {
                // 支付单关闭失败不阻断关单：本地订单已经关闭，支付单最坏情况
                // 是用户后来付成功 → 走掉单告警流程人工处理，比"订单永远卡住"可控
                log.error("关闭支付单失败（订单已关闭）：orderNo={}, paymentNo={}, err={}",
                        order.getOrderNo(), payment.getPaymentNo(), e.getMessage());
            }
        }

        ShopOrder latest = orderMapper.selectById(order.getId());
        eventService.recordStatusChange(latest,
                timeout ? OrderEventType.CLOSE : OrderEventType.CANCEL,
                from.name(), to.name(),
                latest.getPayStatus(), latest.getPayStatus(),
                operatorType, operator, reason, null);
    }

    private void releaseSeckill(List<ShopOrderItem> items, String orderNo, String operator) {
        if (items == null) {
            return;
        }
        Map<String, Integer> byPromotion = new LinkedHashMap<>();
        for (ShopOrderItem item : items) {
            if (StringUtils.isBlank(item.getPromotionId())) {
                continue;
            }
            if (!"SECKILL".equalsIgnoreCase(StringUtils.trimToEmpty(item.getPromotionType()))) {
                continue;
            }
            byPromotion.merge(item.getPromotionId(),
                    item.getQuantity() == null ? 0 : item.getQuantity(), Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : byPromotion.entrySet()) {
            // 只归还"限量"部分；限购额度**故意不归还**——
            // 秒杀是"抢到即占用"的语义，允许归还会让"抢满→取消→再抢"成为绕过手段
            stockService.releaseSeckill(entry.getKey(), entry.getValue(), orderNo, operator);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSellerRemark(String orderNo, String remark) {
        ShopOrder order = requireAdminOrder(orderNo);
        String trimmed = StringUtils.left(StringUtils.trimToNull(remark), 500);
        orderMapper.update(new ShopOrder()
                        .setSellerRemark(trimmed)
                        .setUpdateBy(currentOperator())
                        .setUpdateTime(new Date()),
                new LambdaUpdateWrapper<ShopOrder>().eq(ShopOrder::getId, order.getId()));
        ShopOrder latest = orderMapper.selectById(order.getId());
        // 备注也要留痕：如果这条备注是"已电话确认可退"，它就是后续退款决策的依据
        eventService.recordNote(latest, OrderEventType.REMARK, OperatorType.ADMIN, currentOperator(),
                "修改卖家备注：" + StringUtils.defaultString(trimmed), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(String orderNo) {
        ShopOrder order = requireAdminOrder(orderNo);
        // 状态限制写在 SQL 的 WHERE 里，任何调用路径都绕不过去
        int rows = orderMapper.archiveById(order.getId(), currentOperator());
        if (rows == 0) {
            throw new JeecgBootException("只有【已取消】或【已关闭】的订单可以归档。"
                    + "当前状态为[" + OrderStatus.labelOf(order.getOrderStatus())
                    + "]，有效订单在逻辑层面也禁止删除");
        }
        ShopOrder latest = orderMapper.selectById(order.getId());
        eventService.recordNote(latest, OrderEventType.ARCHIVE, OperatorType.ADMIN, currentOperator(),
                "订单归档（数据保留，仅从默认列表隐藏；严禁物理删除）", null);
    }

    @Override
    public ShopOrder getByOrderNo(String orderNo) {
        if (StringUtils.isBlank(orderNo)) {
            return null;
        }
        return orderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getOrderNo, orderNo)
                .last("LIMIT 1"));
    }

    // ==================================================================
    // 超时关单
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeIfOverdue(ShopOrder order) {
        if (order == null || !OrderStatus.WAIT_PAY.name().equals(order.getOrderStatus())) {
            return false;
        }
        if (order.getPayDeadline() == null || order.getPayDeadline().after(new Date())) {
            return false;
        }

        // 四层兜底的"主动查询"：关单前先问渠道"这笔到底付没付"。
        // 用户最后一秒支付、回调还在路上时，这一步能救回一笔真实的掉单。
        if (requireQueryBeforeClose) {
            List<ShopOrderPayment> settleable = paymentService.listSettleable(order.getId());
            for (ShopOrderPayment payment : settleable) {
                if (paymentService.queryAndSettle(payment, "SYSTEM")) {
                    log.info("超时关单前查询到支付成功，已转为待发货，不关单：orderNo={}", order.getOrderNo());
                    return false;
                }
            }
        }

        ShopOrder latest = orderMapper.selectById(order.getId());
        if (latest == null || !OrderStatus.WAIT_PAY.name().equals(latest.getOrderStatus())) {
            return false;
        }
        try {
            doCancel(latest, org.jeecg.modules.shop.order.constant.OrderEvent.TIMEOUT_CLOSE,
                    OperatorType.SYSTEM, "SYSTEM", "超过支付时限未支付，系统自动关闭");
            return true;
        } catch (JeecgBootException e) {
            // CAS 失败 = 刚好被支付了或被人工处理了，属于正常竞态，不算失败
            log.info("超时关单跳过（订单状态已变化）：orderNo={}, reason={}", order.getOrderNo(), e.getMessage());
            return false;
        }
    }

    // ==================================================================
    // 公共过滤与工具
    // ==================================================================


    private List<String> resolveScope(String storeId) {
        List<String> visible = orgScopeResolver.resolveVisibleOrgCodes();
        if (StringUtils.isBlank(storeId)) {
            return visible;
        }
        String storeOrgCode = orgScopeResolver.resolveStoreOrgCode(storeId);
        return orgScopeResolver.mergeScope(storeOrgCode, visible);
    }

    private void applyOrgScope(LambdaQueryWrapper<ShopOrder> wrapper, List<String> scope) {
        if (scope == null) {
            return;
        }
        if (scope.isEmpty()) {
            // 两个可见域没有交集：恒假条件，直接查不到数据（fail-closed）
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(w -> {
            for (int i = 0; i < scope.size(); i++) {
                if (i > 0) {
                    w.or();
                }
                w.likeRight(ShopOrder::getStoreOrgCode, scope.get(i));
            }
        });
    }

    private ShopOrder requireOwnedOrder(OrderContext context, String orderNo) {
        if (context == null || StringUtils.isBlank(context.getUserId())) {
            throw new JeecgBootException("请先登录");
        }
        if (StringUtils.isBlank(orderNo)) {
            throw new JeecgBootException("缺少订单号");
        }
        ShopOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getOrderNo, orderNo)
                .eq(ShopOrder::getUserId, context.getUserId())
                .eq(ShopOrder::getStoreId, context.getStoreId())
                .last("LIMIT 1"));
        if (order == null) {
            // 不区分"不存在"和"不是你的"，避免被用来探测订单号
            throw new JeecgBootException("订单不存在或无权操作");
        }
        return order;
    }

    private ShopOrder requireAdminOrder(String orderNo) {
        ShopOrder order = getByOrderNo(orderNo);
        if (order == null) {
            throw new JeecgBootException("订单不存在：" + orderNo);
        }
        orgScopeResolver.checkAccessible(order.getStoreOrgCode(), "订单");
        return order;
    }

    private String currentOperator() {
        org.jeecg.common.system.vo.LoginUser user = orgScopeResolver.currentLoginUser();
        return user == null ? "SYSTEM" : user.getUsername();
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        // 单页上限 100：防止有人用 pageSize=100000 把整表拉下来打爆内存与网络
        return Math.min(pageSize, 100);
    }

    // ==================================================================
    // 标签
    // ==================================================================

    private String payStatusLabel(String status) {
        PayStatus value = PayStatus.parse(status);
        return value == null ? status : value.getLabel();
    }

    private String refundStatusLabel(String status) {
        RefundStatus value = RefundStatus.parse(status);
        return value == null ? status : value.getLabel();
    }

    private String refundOrderStatusLabel(String status) {
        org.jeecg.modules.shop.order.constant.RefundOrderStatus value =
                org.jeecg.modules.shop.order.constant.RefundOrderStatus.parse(status);
        return value == null ? status : value.getLabel();
    }

    private String paymentStatusLabel(String status) {
        org.jeecg.modules.shop.order.constant.PaymentStatus value =
                org.jeecg.modules.shop.order.constant.PaymentStatus.parse(status);
        return value == null ? status : value.getLabel();
    }

    private String eventTypeLabel(String type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case OrderEventType.CREATE: return "创建订单";
            case OrderEventType.PAY_REQUEST: return "发起支付";
            case OrderEventType.PAY_SUCCESS: return "支付成功";
            case OrderEventType.PAY_FAIL: return "支付失败";
            case OrderEventType.PAY_QUERY: return "支付查询";
            case OrderEventType.CANCEL: return "取消订单";
            case OrderEventType.CLOSE: return "关闭订单";
            case OrderEventType.SHIP: return "发货";
            case OrderEventType.RECEIVE: return "确认收货";
            case OrderEventType.ARCHIVE: return "订单归档";
            case OrderEventType.REMARK: return "修改备注";
            case OrderEventType.REFUND_APPLY: return "申请退款";
            case OrderEventType.REFUND_AUDIT: return "退款审核";
            case OrderEventType.REFUND_SUCCESS: return "退款成功";
            case OrderEventType.REFUND_FAIL: return "退款失败";
            case OrderEventType.COMPENSATE: return "补偿处理";
            default: return type;
        }
    }

    private String operatorTypeLabel(String type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case OperatorType.USER: return "买家";
            case OperatorType.ADMIN: return "管理员";
            case OperatorType.SYSTEM: return "系统";
            case OperatorType.CHANNEL: return "支付渠道";
            default: return type;
        }
    }
}