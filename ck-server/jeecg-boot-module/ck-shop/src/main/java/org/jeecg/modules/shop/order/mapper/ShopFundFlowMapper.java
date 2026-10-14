package org.jeecg.modules.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.jeecg.modules.shop.order.entity.ShopFundFlow;

/**
 * <p>资金流水 Mapper</p>
 *
 * <b>只追加</b>。重复入账由唯一索引
 * {@code uk_fund_biz(biz_type, channel, channel_trade_no)} 在数据库层兜底——
 * 业务层幂等一旦因为新渠道、新回调路径而失效，这里是最后一道防线。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Mapper
public interface ShopFundFlowMapper extends BaseMapper<ShopFundFlow> {
}
