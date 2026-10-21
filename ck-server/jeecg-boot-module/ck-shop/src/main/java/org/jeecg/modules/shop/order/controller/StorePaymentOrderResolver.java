package org.jeecg.modules.shop.order.controller;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.order.entity.ShopOrder;
import org.jeecg.modules.shop.order.service.IShopOrderService;
import org.jeecg.modules.shop.order.service.OrderContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 店铺端支付接口的订单归属校验：把"前端传来的订单号"换成"确认属于当前买家+当前店铺的订单 ID"。
 * </p>
 *
 * <h3>为什么按订单号取支付单是错的</h3>
 * 直接按订单号查支付单，只要猜到一个订单号就能看到别人的支付单号与渠道流水号；
 * 更严重的是 {@code mock-confirm}/{@code query} 这类接口会以"订单号"为参数去推进别人的支付。
 * 因此这里统一走订单服务（带 userId + storeId 校验）拿订单，再把 {@code order.getId()}
 * 交给支付服务。所有支付接口共用这一份校验，避免某个接口漏写。
 *
 * @author Cikian
 * @since 2026-10-22
 */
@Component
public class StorePaymentOrderResolver {

    @Autowired
    private IShopOrderService orderService;

    /**
     * 校验订单归属并返回订单 ID。
     *
     * @throws JeecgBootException 订单不存在、不属于当前用户或不属于当前店铺
     */
    public String requireOrderId(String orderNo, OrderContext context) {
        if (StringUtils.isBlank(orderNo)) {
            throw new JeecgBootException("缺少订单号");
        }
        ShopOrder order = orderService.getByOrderNo(orderNo);
        if (order == null
                || context == null
                || !context.getUserId().equals(order.getUserId())
                || !context.getStoreId().equals(order.getStoreId())) {
            // 不区分"订单不存在"和"不是你的订单"，避免被用来探测订单号
            throw new JeecgBootException("订单不存在或无权操作");
        }
        return order.getId();
    }
}
