package org.jeecg.modules.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.jeecg.modules.shop.order.entity.ShopOrderRefund;

/**
 * <p>退款单 Mapper</p>
 *
 * 退款额度校验**不在这里**，而在支付单上的原子累加
 * （见 {@code ShopOrderPaymentMapper#accumulateRefundAmount}）——
 * 额度天然属于支付单，放在退款单上是查不到"一共退了多少"的。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Mapper
public interface ShopOrderRefundMapper extends BaseMapper<ShopOrderRefund> {
}
