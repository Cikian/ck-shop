package org.jeecg.modules.shop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.shop.order.constant.RefundOrderStatus;
import org.jeecg.modules.shop.order.entity.ShopOrderItem;
import org.jeecg.modules.shop.order.entity.ShopOrderRefund;
import org.jeecg.modules.shop.order.entity.ShopOrderRefundItem;
import org.jeecg.modules.shop.order.entity.vo.OrderRefundVO;
import org.jeecg.modules.shop.order.mapper.ShopOrderRefundItemMapper;
import org.jeecg.modules.shop.order.mapper.ShopOrderRefundMapper;
import org.jeecg.modules.shop.order.util.MoneyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 退款单的**只读查询与展示映射**支撑组件。
 *
 * <p>为什么单独拆出来：{@code ShopRefundServiceImpl} 原本 1100+ 行，把"申请/审核/退款提交"
 * 这类有副作用的流程，与"查退款单 / 查明细行 / 组装 VO / 算单行可退"这类**无副作用**逻辑混在一起。
 * 这里只搬后者 —— 纯搬运，公共契约不变：接口方法仍由 ShopRefundServiceImpl 实现并委托。
 *
 * <p>本类不做任何写操作。
 */
@Component
public class ShopRefundSupport {

    @Autowired
    private ShopOrderRefundMapper refundMapper;

    @Autowired
    private ShopOrderRefundItemMapper refundItemMapper;

    /** 按退款单号查（退款单号是业务唯一键） */
    public ShopOrderRefund getByRefundNo(String refundNo) {
        if (StringUtils.isBlank(refundNo)) {
            return null;
        }
        return refundMapper.selectOne(new LambdaQueryWrapper<ShopOrderRefund>()
                .eq(ShopOrderRefund::getRefundNo, refundNo)
                .last("LIMIT 1"));
    }

    /** 退款单的明细行 */
    public List<ShopOrderRefundItem> listItems(String refundId) {
        if (StringUtils.isBlank(refundId)) {
            return new ArrayList<>();
        }
        return refundItemMapper.selectList(new LambdaQueryWrapper<ShopOrderRefundItem>()
                .eq(ShopOrderRefundItem::getRefundId, refundId));
    }

    /** 批量转 VO（不带明细行，明细在 toVO 内部按需查） */
    public List<OrderRefundVO> toVOs(List<ShopOrderRefund> refunds) {
        List<OrderRefundVO> list = new ArrayList<>();
        if (refunds == null) {
            return list;
        }
        for (ShopOrderRefund refund : refunds) {
            list.add(toVO(refund, null));
        }
        return list;
    }

    /**
     * 单条转 VO。
     *
     * <p>注意 {@code refundTypeLabel} 是**写死的中文**（"全额退款"/"部分退款"），
     * 与"所有标签都用后端字段"的约定不同 —— 这是既有实现，本次纯搬运不改语义；
     * 若要改，应连同后端字典一起做（属于待决策项）。
     */
    public OrderRefundVO toVO(ShopOrderRefund refund, List<ShopOrderRefundItem> items) {
        if (refund == null) {
            return null;
        }
        OrderRefundVO vo = new OrderRefundVO();
        org.springframework.beans.BeanUtils.copyProperties(refund, vo);
        vo.setStatusLabel(RefundOrderStatus.of(refund.getStatus()).getLabel());
        vo.setRefundTypeLabel("FULL".equals(refund.getRefundType()) ? "全额退款" : "部分退款");
        vo.setItems(items == null ? listItems(refund.getId()) : items);
        return vo;
    }

    /** 单个订单行还能退多少（已退金额与负值都归零） */
    public BigDecimal lineRefundable(ShopOrderItem item) {
        BigDecimal refundable = MoneyUtils.subtract(
                MoneyUtils.scale(item.getActualAmount()), MoneyUtils.scale(item.getRefundAmount()));
        return refundable.compareTo(BigDecimal.ZERO) < 0 ? MoneyUtils.zero() : refundable;
    }
}
