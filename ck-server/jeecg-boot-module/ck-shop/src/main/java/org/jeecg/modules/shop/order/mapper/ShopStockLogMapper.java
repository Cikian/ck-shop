package org.jeecg.modules.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.jeecg.modules.shop.order.entity.ShopStockLog;

/**
 * <p>库存流水 Mapper</p>
 *
 * <b>只追加</b>。库存对不上时的排查路径是：
 * 取某 SKU 最后一条流水的 {@code after_stock} 与商品表当前值比对——
 * 不等即说明有绕过本模块的写入。任何 update/delete 都会让这条路径失效。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Mapper
public interface ShopStockLogMapper extends BaseMapper<ShopStockLog> {
}
