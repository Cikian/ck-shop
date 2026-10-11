package org.jeecg.modules.shop.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.store.entity.ShopStore;

/**
 * <p>
 * 店铺 Mapper
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Mapper
public interface ShopStoreMapper extends BaseMapper<ShopStore> {

    /**
     * 按编码取**启用中**的店铺。
     * 停用/已删除的店铺必须解析不到——否则前台会静默展示一个本应下线的站点。
     */
    @Select("SELECT * FROM ck_shop_store WHERE code = #{code} AND del_flag = 0 AND status = 'Y' LIMIT 1")
    ShopStore selectEnabledByCode(@Param("code") String code);

    /**
     * 按域名取**启用中**的店铺（Host 兜底解析用）。
     */
    @Select("SELECT * FROM ck_shop_store WHERE domain = #{domain} AND del_flag = 0 AND status = 'Y' LIMIT 1")
    ShopStore selectEnabledByDomain(@Param("domain") String domain);

    /**
     * 编码是否已被占用（含已删除行）。
     * 与 spu_code 同理：编码会被写进前端站点配置，不应被回收后再指向另一个店铺。
     */
    @Select("SELECT id FROM ck_shop_store WHERE code = #{code} LIMIT 1")
    String selectAnyIdByCode(@Param("code") String code);
}
