package org.jeecg.modules.shop.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.order.entity.ShopOrder;
import org.jeecg.modules.shop.order.entity.ShopOrderEvent;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 订单操作流水 Service。
 * </p>
 *
 * <h3>为什么单独抽一个 Service</h3>
 * 订单、支付、退款、定时任务四个模块都要写流水。如果每个模块自己
 * {@code mapper.insert(event)}，那么"操作者类型填什么""详情 JSON 放什么"
 * 就会各写一套，最后流水表里出现四种风格，排查时反而更难读。
 * 收口到这里，格式就统一了。
 *
 * <h3>写失败怎么办</h3>
 * 流水写入**随主流程事务一起回滚**（与库存流水不同）。
 * 理由：状态变更与"记录这次变更"必须是原子的——
 * 如果状态变了却没有流水，这张订单就出现了无法解释的状态跳跃，
 * 而这恰恰是审计最不能接受的。宁可让用户重试一次下单，
 * 也不能留下"改了但没记录"的痕迹。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
public interface IShopOrderEventService extends IService<ShopOrderEvent> {

    /** 记录一次状态变更（订单状态 + 支付状态） */
    void recordStatusChange(ShopOrder order,
                            String eventType,
                            String fromStatus,
                            String toStatus,
                            String fromPayStatus,
                            String toPayStatus,
                            String operatorType,
                            String operatorName,
                            String remark,
                            BigDecimal amount);

    /** 记录一次状态变更，并附带明细 JSON */
    void recordStatusChange(ShopOrder order,
                            String eventType,
                            String fromStatus,
                            String toStatus,
                            String fromPayStatus,
                            String toPayStatus,
                            String operatorType,
                            String operatorName,
                            String remark,
                            BigDecimal amount,
                            String detail);

    /**
     * 记录一次"非状态变更"的事件（备注、补单尝试、回调拒绝等）。
     *
     * <p>这类事件同样必须留痕：后台"只是改了个备注"看起来无害，
     * 但如果这个备注是"已电话确认可退"，它就是后续退款决策的依据。
     */
    void recordNote(ShopOrder order,
                    String eventType,
                    String operatorType,
                    String operatorName,
                    String remark,
                    String detail);

    /** 按订单ID取全部流水（时间正序，便于还原轨迹） */
    List<ShopOrderEvent> listByOrderId(String orderId);

    /**
     * 取**买家可见**的流水节点。
     *
     * <p>只保留订单生命周期节点：下单、支付、发货、收货、关闭、退款。
     * 后台备注、人工补单等内部操作不下发——
     * 买家看到"客服于 xx 改过备注"只会产生新的疑问与投诉。
     */
    List<ShopOrderEvent> listBuyerVisible(String orderId);
}
