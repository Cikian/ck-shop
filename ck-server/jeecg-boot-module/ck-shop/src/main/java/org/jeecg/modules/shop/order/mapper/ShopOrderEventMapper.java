package org.jeecg.modules.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.jeecg.modules.shop.order.entity.ShopOrderEvent;

/**
 * <p>订单操作流水 Mapper</p>
 *
 * <b>只允许 insert 与 select</b>：这张表是账本，
 * 任何 update/delete 都会破坏可追溯性。Mapper 继承 BaseMapper 只是拿到了
 * 插入与查询能力，业务代码里不得调用其 update/delete 方法。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Mapper
public interface ShopOrderEventMapper extends BaseMapper<ShopOrderEvent> {
}
