package org.jeecg.modules.shop.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.jeecg.modules.shop.account.entity.ShopLoginLog;

/**
 * <p>
 * 店铺端登录来源流水 Mapper
 * </p>
 *
 * 只有 insert（+ 后台可能的查询），没有任何更新/删除：
 * 流水表被 UPDATE 过就不再是流水了。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Mapper
public interface ShopLoginLogMapper extends BaseMapper<ShopLoginLog> {
}
