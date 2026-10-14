package org.jeecg.modules.shop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.shop.order.constant.OperatorType;
import org.jeecg.modules.shop.order.constant.OrderEventType;
import org.jeecg.modules.shop.order.entity.ShopOrder;
import org.jeecg.modules.shop.order.entity.ShopOrderEvent;
import org.jeecg.modules.shop.order.mapper.ShopOrderEventMapper;
import org.jeecg.modules.shop.order.service.IShopOrderEventService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 订单操作流水实现。
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Slf4j
@Service
public class ShopOrderEventServiceImpl
        extends ServiceImpl<ShopOrderEventMapper, ShopOrderEvent>
        implements IShopOrderEventService {

    /**
     * 买家可见的事件白名单。
     *
     * <p>用白名单而不是黑名单：新增一种内部事件（例如"风控标记""人工改价"）时，
     * 黑名单方案会**默认泄露**给买家，而白名单只会默认隐藏。
     * 涉及"哪些信息能给用户看"的场合，默认隐藏才是安全的方向。
     */
    private static final Set<String> BUYER_VISIBLE_TYPES = new HashSet<>(Arrays.asList(
            OrderEventType.CREATE,
            OrderEventType.PAY_REQUEST,
            OrderEventType.PAY_SUCCESS,
            OrderEventType.PAY_FAIL,
            OrderEventType.CANCEL,
            OrderEventType.CLOSE,
            OrderEventType.SHIP,
            OrderEventType.RECEIVE,
            OrderEventType.REFUND_APPLY,
            OrderEventType.REFUND_AUDIT,
            OrderEventType.REFUND_SUCCESS,
            OrderEventType.REFUND_FAIL
    ));

    @Override
    public void recordStatusChange(ShopOrder order, String eventType,
                                   String fromStatus, String toStatus,
                                   String fromPayStatus, String toPayStatus,
                                   String operatorType, String operatorName,
                                   String remark, BigDecimal amount) {
        recordStatusChange(order, eventType, fromStatus, toStatus, fromPayStatus, toPayStatus,
                operatorType, operatorName, remark, amount, null);
    }

    @Override
    public void recordStatusChange(ShopOrder order, String eventType,
                                   String fromStatus, String toStatus,
                                   String fromPayStatus, String toPayStatus,
                                   String operatorType, String operatorName,
                                   String remark, BigDecimal amount, String detail) {
        ShopOrderEvent event = baseEvent(order, eventType, operatorType, operatorName, remark, detail);
        event.setFromStatus(fromStatus)
                .setToStatus(toStatus)
                .setFromPayStatus(fromPayStatus)
                .setToPayStatus(toPayStatus)
                .setAmount(amount);
        insertEvent(event, order, eventType);
    }

    @Override
    public void recordNote(ShopOrder order, String eventType, String operatorType,
                           String operatorName, String remark, String detail) {
        ShopOrderEvent event = baseEvent(order, eventType, operatorType, operatorName, remark, detail);
        // 注记类事件不改变状态，但把**当前**状态一并记下：
        // 事后看流水时才知道"改这条备注时订单处于什么状态"
        if (order != null) {
            event.setFromStatus(order.getOrderStatus())
                    .setToStatus(order.getOrderStatus())
                    .setFromPayStatus(order.getPayStatus())
                    .setToPayStatus(order.getPayStatus());
        }
        insertEvent(event, order, eventType);
    }

    @Override
    public List<ShopOrderEvent> listByOrderId(String orderId) {
        if (StringUtils.isBlank(orderId)) {
            return java.util.Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<ShopOrderEvent>()
                .eq(ShopOrderEvent::getOrderId, orderId)
                .orderByAsc(ShopOrderEvent::getCreateTime)
                .orderByAsc(ShopOrderEvent::getId));
    }

    @Override
    public List<ShopOrderEvent> listBuyerVisible(String orderId) {
        List<ShopOrderEvent> all = listByOrderId(orderId);
        java.util.List<ShopOrderEvent> visible = new java.util.ArrayList<>(all.size());
        for (ShopOrderEvent event : all) {
            if (BUYER_VISIBLE_TYPES.contains(event.getEventType())) {
                visible.add(event);
            }
        }
        return visible;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private ShopOrderEvent baseEvent(ShopOrder order, String eventType, String operatorType,
                                     String operatorName, String remark, String detail) {
        ShopOrderEvent event = new ShopOrderEvent()
                .setEventType(eventType)
                .setOperatorType(StringUtils.defaultIfBlank(operatorType, OperatorType.SYSTEM))
                .setOperatorName(operatorName)
                .setRemark(truncate(remark, 500))
                .setDetail(detail)
                .setCreateTime(new Date());
        if (order != null) {
            event.setOrderId(order.getId())
                    .setOrderNo(order.getOrderNo())
                    .setStoreId(order.getStoreId())
                    .setStoreOrgCode(order.getStoreOrgCode());
            // 操作者ID：管理员/用户场景写用户ID，渠道场景写渠道名，系统写 SYSTEM
            event.setOperatorId(StringUtils.defaultIfBlank(operatorName, event.getOperatorType()));
        }
        return event;
    }

    /**
     * 落库。
     *
     * <p><b>失败必须抛出去</b>：这里与库存流水不同——状态已经改了却没有流水，
     * 会留下"无法解释的状态跳跃"，而审计最不能接受的就是这个。
     * 让事务回滚、用户重试，比留下一条没有记录的变更要安全得多。
     */
    private void insertEvent(ShopOrderEvent event, ShopOrder order, String eventType) {
        if (order == null || StringUtils.isBlank(order.getId())) {
            throw new IllegalStateException("记录订单流水时缺少订单信息：" + eventType);
        }
        save(event);
    }

    /** 备注类字段有长度上限，超长直接截断而不是让整个下单/发货失败 */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
