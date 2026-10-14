package org.jeecg.modules.shop.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.jeecg.modules.shop.order.entity.ShopUserAddress;

/**
 * <p>用户收货地址 Mapper</p>
 *
 * 无自定义 SQL：地址簿的查询条件简单（user_id + del_flag），
 * MyBatis-Plus 的 wrapper 足够，写 XML 反而多一层维护成本。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Mapper
public interface ShopUserAddressMapper extends BaseMapper<ShopUserAddress> {
}
