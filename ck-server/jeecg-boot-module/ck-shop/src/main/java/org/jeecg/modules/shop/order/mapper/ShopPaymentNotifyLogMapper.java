package org.jeecg.modules.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.jeecg.modules.shop.order.entity.ShopPaymentNotifyLog;

/**
 * <p>渠道回调流水 Mapper</p>
 *
 * <b>只插入不更新</b>：每次回调（包括重复回调）都落一条新记录，
 * 这是"渠道到底推了几次"的唯一证据。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Mapper
public interface ShopPaymentNotifyLogMapper extends BaseMapper<ShopPaymentNotifyLog> {
}
