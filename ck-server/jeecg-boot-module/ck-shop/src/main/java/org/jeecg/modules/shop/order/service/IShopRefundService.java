package org.jeecg.modules.shop.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.order.entity.ShopOrderRefund;
import org.jeecg.modules.shop.order.entity.dto.RefundApplyDTO;
import org.jeecg.modules.shop.order.entity.dto.RefundAuditDTO;
import org.jeecg.modules.shop.order.entity.dto.RefundQuery;
import org.jeecg.modules.shop.order.entity.vo.OrderRefundVO;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * <p>
 * 退款 Service。
 * </p>
 *
 * <h3>本模块最重要的两条约束</h3>
 * <ol>
 *   <li><b>退款额度在支付单上加锁</b>：{@code UPDATE ... WHERE refund_amount + delta <= amount}。
 *       这是"并发退款超额"的根治手段，任何"先查 sum 再判断"的写法在同一场景下都会超额。</li>
 *   <li><b>先占额度、再提交渠道</b>：顺序颠倒会在额度不足时出现
 *       "钱已经退出去了，但系统认为没退"——资金已经离开账户，无法挽回。
 *       占额度失败就直接拒绝申请，这是安全的失败方向。</li>
 * </ol>
 *
 * <h3>与支付模块的依赖方向</h3>
 * 退款模块**直接使用网关与支付单 Mapper**，不依赖 {@code IShopPaymentService}；
 * 反过来支付模块的回调处理会调用本模块的
 * {@link #applyChannelRefundResult}。保持单向依赖，避免循环依赖
 * 与"退款成功到底该谁更新订单"的职责模糊。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
public interface IShopRefundService extends IService<ShopOrderRefund> {

    /** 买家申请退款（金额由服务端计算，请求体里没有金额字段） */
    OrderRefundVO apply(OrderContext context, RefundApplyDTO dto);

    /** 买家撤销退款申请（仅待审核可撤销） */
    void withdraw(OrderContext context, String refundNo);

    /** 后台审核：通过则占额度并提交渠道退款；拒绝则记录原因 */
    void audit(RefundAuditDTO dto, String operator);

    /** 后台重试渠道退款（针对受理中/失败的退款单） */
    void retryChannelRefund(String refundNo, String operator);

    /** 买家查看某订单的退款单 */
    List<OrderRefundVO> listByOrderNo(OrderContext context, String orderNo);

    /** 后台退款单分页 */
    Page<OrderRefundVO> adminPage(RefundQuery query);

    /** 后台退款单详情 */
    OrderRefundVO adminDetail(String refundNo);

    /** 按退款单号查询 */
    ShopOrderRefund getByRefundNo(String refundNo);

    /** 该订单当前还可退多少（人民币）。**不含归属校验**：买家侧申请退款也走这个计算 */
    BigDecimal refundableAmount(String orderNo);

    /**
     * 后台口径的"该订单还可退多少"：先校验该订单在当前登录人的可见组织范围内。
     *
     * <p>为什么单独给一个方法而不是直接给 refundableAmount 加校验：
     * 买家侧「申请退款」也调用同一个金额计算，那里的归属规则是"订单属于当前会员"，
     * 与后台的「可见组织范围」不是一回事。混在一起必然有一边被破坏。
     */
    BigDecimal adminRefundableAmount(String orderNo);

    /**
     * 把渠道退款结果落到退款单与订单上。
     *
     * <p>回调与主动查询**共用这一处实现**：两条路径各写一遍必然分叉，
     * 然后就会出现"回调进来时库存回补了、查询进来时没回补"这种对不上的情况。
     *
     * @param success         渠道是否确认退款成功
     * @param channelRefundNo 渠道退款流水号
     * @param refundTime      渠道退款到账时间
     * @param failReason      失败原因
     * @param operator        操作者（回调传渠道名，查询传 SYSTEM）
     */
    void applyChannelRefundResult(ShopOrderRefund refund, boolean success,
                                  String channelRefundNo, Date refundTime,
                                  String failReason, String operator);
}
