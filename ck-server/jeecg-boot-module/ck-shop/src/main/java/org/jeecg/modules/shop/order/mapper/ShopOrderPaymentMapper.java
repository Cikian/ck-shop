package org.jeecg.modules.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.shop.order.entity.ShopOrderPayment;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * <p>
 * 支付单 Mapper。
 * </p>
 *
 * <h3>状态推进一律走 CAS</h3>
 * 支付单是"资金事实"的载体，状态只能从 INIT/PENDING 往 SUCCESS/FAILED/CLOSED 走一次。
 * 渠道会重复推送回调，没有 CAS 就会出现"同一笔回调把已关闭的支付单改回成功"
 * 或者"两次回调各加一次资金流水"。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Mapper
public interface ShopOrderPaymentMapper extends BaseMapper<ShopOrderPayment> {

    /**
     * 置为支付成功（CAS）。
     *
     * <p>只允许从 {@code INIT / PENDING} 转为 {@code SUCCESS}：
     * 已经是 SUCCESS 的再改一次会刷新 {@code pay_time} 与渠道流水号，
     * 破坏"资金流水只有一条、时间不可变"的对账前提。
     * 重复回调的正确处理是**识别为重复并直接返回成功**，而不是再写一次。
     *
     * @return 1 = 本次确实完成了状态推进（首次成功）；0 = 已经是终态（重复回调）
     */
    int markPaid(@Param("id") String id,
                 @Param("channelTradeNo") String channelTradeNo,
                 @Param("paidAmount") BigDecimal paidAmount,
                 @Param("payTime") Date payTime,
                 @Param("operator") String operator);

    /**
     * 置为终态（失败 / 关闭），仅当当前状态在允许集合内。
     *
     * @param expectStatuses 允许的前置状态集合，例如 INIT/PENDING
     * @return 受影响行数
     */
    int markTerminal(@Param("id") String id,
                     @Param("status") String status,
                     @Param("expectStatuses") List<String> expectStatuses,
                     @Param("errorCode") String errorCode,
                     @Param("errorMsg") String errorMsg,
                     @Param("operator") String operator);

    /**
     * 原子累加已退金额 —— **退款额度的真正锁**。
     *
     * <p>{@code refund_amount + delta <= amount} 是"累计退款不超过支付金额"的
     * 唯一可靠实现。不写这条条件、改成先查再判断，多个客服并发退款必然超额。
     *
     * @return 0 = 超额或支付单非 SUCCESS，调用方必须拒绝退款
     */
    int accumulateRefundAmount(@Param("id") String id,
                               @Param("delta") BigDecimal delta,
                               @Param("operator") String operator);

    /**
     * 回滚已累加的退款额（渠道退款失败时调用）。
     *
     * <p>必须先"占额度、再提交渠道"：反过来（先提交渠道、成功后再占额度）
     * 会在额度不足时出现"钱已经退出去了，但系统认为没退"的严重后果。
     * 占额度失败则直接拒绝申请，是安全的失败方向。
     * 于是渠道失败时必须把额度还回去，否则额度会被永久占用。
     */
    int releaseRefundAmount(@Param("id") String id,
                            @Param("delta") BigDecimal delta,
                            @Param("operator") String operator);

    /** 累加回调次数并记录最近一次报文（脱敏后） */
    int recordNotify(@Param("id") String id,
                     @Param("notifyTime") Date notifyTime,
                     @Param("body") String body,
                     @Param("operator") String operator);

    /**
     * 扫描"长时间处于待支付"的支付单，供补单任务主动向渠道查询。
     *
     * @param createdBefore 只取创建时间早于该时刻的单（避免查刚下单、渠道还没落账的单）
     */
    List<ShopOrderPayment> selectStalePending(@Param("createdBefore") Date createdBefore,
                                              @Param("limit") Integer limit);
}
