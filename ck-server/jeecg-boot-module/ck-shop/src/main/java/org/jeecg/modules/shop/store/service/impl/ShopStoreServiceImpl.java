package org.jeecg.modules.shop.store.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.jeecg.modules.shop.store.mapper.ShopStoreMapper;
import org.jeecg.modules.shop.store.service.IShopStoreService;
import org.springframework.beans.factory.annotation.Autowired;
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

    /** 可见域/对象级授权解析器（多店铺隔离的唯一鉴权出口） */
    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    @Override
    public String saveStore(ShopStore store) {
        sanitizeWritableFields(store);
        checkCode(store, null);
        if (StringUtils.isBlank(store.getId())) {
            // 与 JeecgBoot 其它模块一致：主键由 IdWorker 生成，避免前端漏传导致 INSERT 失败
            store.setId(IdWorker.getIdStr());
        }
        checkDepart(store.getDepartId(), store.getStatus(), null);
        checkCreateAllowed(store);
        return this.save(store) ? store.getId() : null;
    }

    /**
     * 写接口直接收实体，必须清掉"只应由服务端/删除接口维护"的字段。
     *
     * <p>否则 client 传 {@code delFlag=1} 就能绕过 {@code shop-store:delete} 软删店铺
     * （updateById 会把它当成普通字段写入）。审计字段同理：创建人/创建时间由服务端设置，
     * 客户端传什么都不能生效。
     */
    private void sanitizeWritableFields(ShopStore store) {
        if (store == null) {
            return;
        }
        store.setDelFlag(null);
        store.setCreateBy(null);
        store.setCreateTime(null);
        store.setUpdateBy(null);
        store.setUpdateTime(null);
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
        // ---- 对象级授权：只能改当前可见域内的店铺（接口可按 id 直调，列表过滤不算数） ----
        checkStoreAccessible(exist);
        // 删除标记/审计字段只由服务端维护：防止 client 传 delFlag=1 绕过 delete 权限
        sanitizeWritableFields(store);
        checkCode(store, store.getId());
        if (StringUtils.isBlank(store.getStatus())) {
            store.setStatus(exist.getStatus());
        }
        // ---- 敏感字段（绑定部门 / 域名 / 状态）只有总部管理员能改 ----
        checkSensitiveFieldsUnchanged(store, exist);

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteStore(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("缺少店铺ID");
        }
        ShopStore exist = this.getById(id);
        if (Objects.isNull(exist)) {
            return false;
        }
        checkStoreAccessible(exist);
        return this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteStores(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        // 先整体校验再删：任何一条越权就抛异常回滚，绝不出现"删了一半"的中间状态
        for (String id : ids) {
            ShopStore exist = this.getById(id);
            if (Objects.isNull(exist)) {
                continue;
            }
            checkStoreAccessible(exist);
        }
        return this.removeByIds(ids);
    }

    // ==================================================================
    // 对象级授权
    // ==================================================================

    /**
     * 店铺的对象级授权：按"店铺绑定的部门"判断是否落在当前账号可见域内。
     *
     * <p>未绑定部门的店铺只有总部管理员能碰——普通运营无法证明它属于自己的范围，
     * 按 fail-closed 处理（{@link ShopOrgScopeResolver#checkAccessible} 对 null orgCode 也会拒绝）。
     */
    private void checkStoreAccessible(ShopStore store) {
        String orgCode = StringUtils.isBlank(store.getDepartId())
                ? null : baseMapper.selectDepartOrgCodeById(store.getDepartId());
        orgScopeResolver.checkAccessible(orgCode, "店铺");
    }

    /**
     * 新建店铺：普通运营只能把店铺绑定到**本人所在部门**。
     *
     * <p>否则运营可以新建一个绑定到别部门的店铺，再去改自己的域名/状态，
     * 等于凭空造出一个"看得见但管不着"的站点。
     */
    private void checkCreateAllowed(ShopStore store) {
        if (orgScopeResolver.canManageAll()) {
            return;
        }
        String myOrg = orgScopeResolver.currentOrgCode();
        String targetOrg = StringUtils.isBlank(store.getDepartId())
                ? null : baseMapper.selectDepartOrgCodeById(store.getDepartId());
        if (StringUtils.isBlank(myOrg) || !myOrg.equals(targetOrg)) {
            throw new JeecgBootException("无权新建店铺：只能新建绑定到本人所在部门（"
                    + StringUtils.defaultIfBlank(myOrg, "未设置") + "）的店铺，"
                    + "如需为其它部门建店请联系总部管理员");
        }
    }

    /**
     * 绑定部门 / 域名 / 状态属于"改配置"而非"改文案"，只有总部管理员（{@code shop-store:manage-all}）能改。
     *
     * <p>这三个字段任何一个被普通运营改动，影响面都远超"编辑店铺名称"：
     * 换部门 = 整店数据搬到别人范围；换域名 = 前台流量被劫持到另一个站；停用 = 站点直接下线。
     */
    private void checkSensitiveFieldsUnchanged(ShopStore incoming, ShopStore exist) {
        if (orgScopeResolver.canManageAll()) {
            return;
        }
        // departId：null=未提交(沿用原值)、""=显式解绑、非空=改绑
        String incomingDepart = incoming.getDepartId() == null ? exist.getDepartId() : incoming.getDepartId();
        if (!StringUtils.equals(StringUtils.trimToNull(incomingDepart),
                StringUtils.trimToNull(exist.getDepartId()))) {
            throw new JeecgBootException("无权修改店铺的绑定部门：该操作仅限总部管理员");
        }
        if (incoming.getDomain() != null
                && !StringUtils.equals(StringUtils.trimToNull(incoming.getDomain()),
                StringUtils.trimToNull(exist.getDomain()))) {
            throw new JeecgBootException("无权修改店铺域名：该操作仅限总部管理员");
        }
        if (StringUtils.isNotBlank(incoming.getStatus())
                && !StringUtils.equals(incoming.getStatus(), exist.getStatus())) {
            throw new JeecgBootException("无权修改店铺状态：该操作仅限总部管理员");
        }
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
