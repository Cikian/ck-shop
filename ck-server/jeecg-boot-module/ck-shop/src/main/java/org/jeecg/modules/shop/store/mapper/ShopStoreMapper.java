package org.jeecg.modules.shop.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.shop.store.entity.ShopStore;

import java.util.List;

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

    /**
     * 全部启用店铺，并带出**绑定部门的 org_code**（只读派生字段 departOrgCode）。
     * 前端"所属店铺"下拉需要它把店铺映射成商品的 sys_org_code，而不是再开一个部门接口。
     */
    @Select("SELECT s.*, d.org_code AS departOrgCode FROM ck_shop_store s "
            + "LEFT JOIN sys_depart d ON d.id = s.depart_id "
            + "WHERE s.del_flag = 0 AND s.status = 'Y' "
            + "ORDER BY s.sort_order ASC, s.create_time ASC")
    List<ShopStore> selectEnabledWithDepart();

    /**
     * 按绑定部门的 org_code 反查启用中的店铺 id（用于校验"归属部门必须属于某个店铺"）。
     * 返回 null 表示该部门没有绑定任何启用店铺。
     */
    @Select("SELECT s.id FROM ck_shop_store s "
            + "INNER JOIN sys_depart d ON d.id = s.depart_id "
            + "WHERE s.del_flag = 0 AND s.status = 'Y' AND d.org_code = #{orgCode} LIMIT 1")
    String selectIdByDepartOrgCode(@Param("orgCode") String orgCode);

    /**
     * 部门 id → 部门编码。找不到（不存在/已删除）返回 null。
     */
    @Select("SELECT d.org_code FROM sys_depart d WHERE d.id = #{departId} AND d.del_flag = 0 LIMIT 1")
    String selectDepartOrgCodeById(@Param("departId") String departId);

    /**
     * 部门 id → 部门类型（sys_depart.org_type：1-公司 2-部门 3-岗位）。找不到返回 null。
     * 店铺只能绑"部门"节点：绑到公司会让可见域变成 {@code LIKE '公司org_code%'}，把下级部门店铺的商品全捞进来。
     */
    @Select("SELECT d.org_type FROM sys_depart d WHERE d.id = #{departId} AND d.del_flag = 0 LIMIT 1")
    String selectDepartOrgTypeById(@Param("departId") String departId);

    /**
     * 域名已被哪个**未删除**的店铺绑定（域名兜底解析必须唯一，否则命中哪个店取决于返回顺序）。
     */
    @Select("SELECT id FROM ck_shop_store WHERE del_flag = 0 AND domain = #{domain} LIMIT 1")
    String selectIdByDomain(@Param("domain") String domain);

    /**
     * 部门已被哪个未删除的店铺绑定（店铺 ↔ 部门 1:1，不允许两个店铺绑同一个部门）。
     */
    @Select("SELECT id FROM ck_shop_store WHERE del_flag = 0 AND depart_id = #{departId} LIMIT 1")
    String selectIdByDepartId(@Param("departId") String departId);
}
