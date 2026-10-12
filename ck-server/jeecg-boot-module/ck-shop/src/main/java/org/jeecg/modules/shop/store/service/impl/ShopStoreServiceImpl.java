package org.jeecg.modules.shop.store.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.jeecg.modules.shop.store.mapper.ShopStoreMapper;
import org.jeecg.modules.shop.store.service.IShopStoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 店铺 Service 实现
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */
@Service
public class ShopStoreServiceImpl extends ServiceImpl<ShopStoreMapper, ShopStore> implements IShopStoreService {

    /** sys_depart.org_type：2 = 部门（1 = 公司，3 = 岗位），只有部门能作为店铺的数据隔离维度 */
    private static final String ORG_TYPE_DEPARTMENT = "2";

    @Override
    public String saveStore(ShopStore store) {
        checkCode(store, null);
        if (StringUtils.isBlank(store.getId())) {
            // 与 JeecgBoot 其它模块一致：主键由 IdWorker 生成，避免前端漏传导致 INSERT 失败
            store.setId(IdWorker.getIdStr());
        }
        checkDepart(store.getDepartId(), store.getStatus(), null);
        return this.save(store) ? store.getId() : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStore(ShopStore store) {
        if (StringUtils.isBlank(store.getId())) {
            throw new JeecgBootException("缺少店铺ID");
        }
        ShopStore exist = this.getById(store.getId());
        if (Objects.isNull(exist)) {
            throw new JeecgBootException("店铺不存在或已删除!");
        }
        checkCode(store, store.getId());
        if (StringUtils.isBlank(store.getStatus())) {
            store.setStatus(exist.getStatus());
        }

        /*
         * departId 的三种语义要分清（前端据此传值）：
         *   null        → 字段未提交，沿用库中现值（普通编辑不该被迫重新选部门）
         *   ""（空串）  → **显式解绑**
         *   非空        → 绑定到该部门
         * 另外 MyBatis-Plus 的 updateById 会跳过 null 字段，所以"解绑"必须用 UpdateWrapper 显式 set null，
         * 否则用户点了清除、接口回"成功"，库里其实没变（评审发现的"静默无操作"）。
         */
        boolean explicitUnbind = store.getDepartId() != null && StringUtils.isBlank(store.getDepartId());
        String effectiveDepartId = explicitUnbind ? null
                : (store.getDepartId() != null ? store.getDepartId() : exist.getDepartId());

        checkDepart(effectiveDepartId, store.getStatus(), store.getId());

        boolean ok = this.updateById(store);
        if (explicitUnbind) {
            this.update(new LambdaUpdateWrapper<ShopStore>()
                    .eq(ShopStore::getId, store.getId())
                    .set(ShopStore::getDepartId, null));
        }
        return ok;
    }

    @Override
    public List<ShopStore> listEnabled() {
        // 带出绑定部门的 org_code（只读派生字段），前端"所属店铺"下拉据此映射到 sys_org_code
        return baseMapper.selectEnabledWithDepart();
    }

    /**
     * 校验部门绑定（最终生效值由调用方算好传进来）。
     *
     * <ol>
     *   <li><b>只能绑"部门"节点</b>：绑到公司（如 A06）会让该店的数据可见域变成
     *       {@code LIKE 'A06%'}，把所有子部门店铺的商品都捞进来，前台直接串店；</li>
     *   <li><b>启用中的店铺必须有部门</b>：否则前台三个接口都拿不到归属
     *       （现在会降级成空数据，但站点实际不可用），不如在保存时就拦住；</li>
     *   <li><b>部门 1:1</b>：同一部门不允许被两个店铺绑定。</li>
     * </ol>
     */
    private void checkDepart(String departId, String status, String selfId) {
        String trimmed = StringUtils.trimToNull(departId);
        boolean enabled = !"N".equals(status);
        if (trimmed == null) {
            if (enabled) {
                throw new JeecgBootException("启用中的店铺必须绑定部门（否则前台没有任何数据可展示）。"
                        + "如确需解绑，请先把店铺状态改为「停用」");
            }
            return;
        }
        String orgType = baseMapper.selectDepartOrgTypeById(trimmed);
        if (orgType == null) {
            throw new JeecgBootException("绑定部门不存在或已删除：" + trimmed);
        }
        if (!ORG_TYPE_DEPARTMENT.equals(orgType)) {
            throw new JeecgBootException("只能绑定到「部门」节点（该节点类型为 " + orgType
                    + "：公司/岗位）。绑到公司会让本店看到所有下级部门的商品，属于串店");
        }
        String existId = baseMapper.selectIdByDepartId(trimmed);
        if (existId != null && !existId.equals(selfId)) {
            throw new JeecgBootException("该部门已被其它店铺绑定：店铺与部门是一对一关系，请先解绑");
        }
    }

    /**
     * 校验编码唯一。
     * 刻意**把已删除行也算进来**（用裸 SQL 查，绕开 @TableLogic）：
     * 店铺编码会写进前端站点的服务端配置，一旦被回收后复用，
     * 老配置就可能指向另一个店铺——这与 spu_code 的处理原则一致。
     */
    private void checkCode(ShopStore store, String selfId) {
        if (StringUtils.isBlank(store.getCode())) {
            throw new JeecgBootException("店铺编码不能为空");
        }
        if (StringUtils.isBlank(store.getName())) {
            throw new JeecgBootException("店铺名称不能为空");
        }
        // 编码只允许字母数字下划线中划线：它要作为配置项与请求头传递
        if (!store.getCode().matches("^[A-Za-z0-9_-]{1,50}$")) {
            throw new JeecgBootException("店铺编码只能包含字母、数字、下划线和中划线");
        }
        String existId = baseMapper.selectAnyIdByCode(store.getCode());
        if (existId != null && !existId.equals(selfId)) {
            throw new JeecgBootException("店铺编码已存在：" + store.getCode());
        }
        if (StringUtils.isBlank(store.getStatus())) {
            store.setStatus("Y");
        }
        if (store.getSortOrder() == null) {
            store.setSortOrder(0);
        }
        checkDomain(store, selfId);
    }

    /**
     * 域名唯一性（为空表示不按域名解析，可重复留空）。
     * 两个店铺填同一个域名时，Host 兜底解析命中哪个店取决于数据库返回顺序 —— 随机串店。
     */
    private void checkDomain(ShopStore store, String selfId) {
        String domain = StringUtils.trimToNull(store.getDomain());
        if (domain == null) {
            return;
        }
        String existId = baseMapper.selectIdByDomain(domain);
        if (existId != null && !existId.equals(selfId)) {
            throw new JeecgBootException("该域名已被其它店铺绑定：" + domain);
        }
    }
}
