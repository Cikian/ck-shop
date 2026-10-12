package org.jeecg.modules.shop.store;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.jeecg.modules.shop.store.mapper.ShopStoreMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 后台数据可见域解析器 —— 多店铺改造的**唯一鉴权出口**。
 * </p>
 *
 * <b>为什么要有这个类：</b>
 * JeecgBoot 原生的"数据规则"挂在 {@code QueryGenerator} 上，而我们的商品列表走
 * {@code ProductQuery} DTO + 自定义 service，套不上去；schema 注释也明确说
 * "不支持在自定义 SQL 中写 {@code in #{sys_org_code}}"。
 * 因此可见域一律在服务层**显式**实现，并且只在这里实现一次——
 * 分散到各个 service 里手写 {@code LIKE} 迟早会漏掉一处，那就等于 A 店能看到 B 店的数据。
 *
 * <b>规则（与设计文档一致）：</b>
 * <ul>
 *   <li>拥有 {@code shop-store:manage-all} 权限或 {@code admin} 角色 → 不限部门（总部视角）；</li>
 *   <li>其余账号 → 只能看 {@code sys_org_code LIKE '当前部门org_code%'} 的数据。
 *       org_code 是前缀式编码（{@code A06} → {@code A06A01}），因此这条规则天然实现
 *       "本部门及其下级部门"，上级部门可以管下级部门；</li>
 *   <li><b>拿不到当前部门时必须报错，绝不退化成"不过滤"或者写入 NULL</b>——
 *       前者是越权，后者会留下无归属的脏数据。</li>
 * </ul>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */
@Slf4j
@Component
public class ShopOrgScopeResolver {

    /** 跨部门管理权限码（总部 / 管理员）。菜单里对应"不限部门(总部)"按钮权限。 */
    public static final String MANAGE_ALL_PERM = "shop-store:manage-all";

    /** 拥有该角色即视为不限部门 */
    private static final String ADMIN_ROLE = "admin";

    private final ShopStoreMapper shopStoreMapper;

    public ShopOrgScopeResolver(ShopStoreMapper shopStoreMapper) {
        this.shopStoreMapper = shopStoreMapper;
    }

    // ==================================================================
    // 基础：当前身份
    // ==================================================================

    /** 当前登录用户，未登录返回 null（公开接口走这里不会抛异常） */
    public LoginUser currentLoginUser() {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            return principal instanceof LoginUser ? (LoginUser) principal : null;
        } catch (Exception e) {
            // 无 Shiro 上下文（例如启动期/异步线程）时按未登录处理
            return null;
        }
    }

    /** 当前登录人的部门编码；取不到返回 null */
    public String currentOrgCode() {
        LoginUser user = currentLoginUser();
        return user == null ? null : StringUtils.trimToNull(user.getOrgCode());
    }

    /**
     * 是否不限部门（总部 / 管理员）。
     * 权限与角色取不到时**按"有限制"处理**（fail-closed），宁可让管理员多点一次授权，
     * 也不能因为鉴权组件异常就把全量数据放出去。
     */
    public boolean canManageAll() {
        try {
            Subject subject = SecurityUtils.getSubject();
            if (subject == null) {
                return false;
            }
            return subject.hasRole(ADMIN_ROLE) || subject.isPermitted(MANAGE_ALL_PERM);
        } catch (Exception e) {
            log.warn("判断 shop-store:manage-all 权限时异常，按无权限处理：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析当前账号的可见域。
     *
     * @return {@code null} 表示**不限**（总部/管理员）；否则返回允许的 org_code 前缀列表
     * @throws JeecgBootException 非管理员但拿不到自己的部门编码（拒绝操作，而不是不过滤）
     */
    public List<String> resolveVisibleOrgCodes() {
        if (canManageAll()) {
            return null;
        }
        String orgCode = currentOrgCode();
        if (StringUtils.isBlank(orgCode)) {
            throw new JeecgBootException("无法确定当前账号的归属部门（sys_org_code 为空），已拒绝本次操作。"
                    + "请让管理员在【系统管理 → 用户管理】中为该账号设置部门后再试");
        }
        List<String> scopes = new ArrayList<>(1);
        scopes.add(orgCode);
        return scopes;
    }

    /** 判断某个 org_code 是否落在给定前缀域内 */
    public boolean inScope(String orgCode, List<String> prefixes) {
        if (prefixes == null) {
            return true;
        }
        String target = StringUtils.trimToNull(orgCode);
        if (target == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (StringUtils.isNotBlank(prefix) && target.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验"这条数据我能碰"，越权直接抛业务异常。
     * 用于按 id 操作（改/删/详情）的场景——列表已经过滤了，但接口是可以被直接按 id 调的。
     */
    public void checkAccessible(String orgCode, String what) {
        List<String> visible = resolveVisibleOrgCodes();
        if (visible == null) {
            return;
        }
        if (!inScope(orgCode, visible)) {
            throw new JeecgBootException("无权访问该" + what + "：它不属于当前账号所在部门的数据范围");
        }
    }

    // ==================================================================
    // 归属写入
    // ==================================================================

    /**
     * 解析"这条数据最终应该归属哪个部门"，并完成越权校验。
     *
     * 语义（与 {@code 执行方案} 的决策 7 一致）：
     * <ul>
     *   <li>{@code requested} 为空 → 沿用 {@code existing}（编辑）或当前部门（新增）；</li>
     *   <li><b>运营（无 {@code shop-store:manage-all}）</b>：
     *       <ul>
     *         <li>编辑时归属必须原地不动，想改就报错——"运营不能改商品归属"是后端硬校验；</li>
     *         <li>新增时归属必须**就是本人所在部门**。注意这里用**等值**而不是前缀：
     *             前缀语义只用于"读"的可见域；写入若也用前缀，部门 {@code A06} 的账号就能
     *             带 {@code sysOrgCode=A06A01} 把数据写进**子部门所属的另一个店铺**
     *             （A06A01 是 default 店绑定的部门），前台立刻串店。</li>
     *       </ul>
     *   </li>
     *   <li><b>总部/管理员</b>：可以跨部门调整，但目标部门必须绑定到某个启用店铺。</li>
     * </ul>
     *
     * "必须绑定店铺"的校验只在归属**发生变化**（含新增）时做：
     * 若只是编辑商品主体、归属原地不动，就不该因为"历史数据恰好落在未绑定部门"而拒绝保存——
     * 那是用户没做错任何事却被挡住。
     *
     * @param requested 请求里显式指定的 org_code，可为空
     * @param existing  库里现值，新增时传 null
     * @return 最终写入的 org_code
     */
    public String resolveWritableOrgCode(String requested, String existing) {
        String current = StringUtils.trimToNull(existing);
        String target = StringUtils.trimToNull(requested);
        if (target == null) {
            target = current;
        }
        if (target == null) {
            target = currentOrgCode();
        }
        if (target == null) {
            // 纪律：判定失败必须报错，绝不能写 NULL 产生无归属脏数据
            throw new JeecgBootException("无法确定数据归属部门：当前账号没有部门（sys_org_code 为空），"
                    + "且请求未指定归属。请先由管理员为账号设置部门，或在表单中明确选择所属店铺");
        }
        boolean changed = current == null || !target.equals(current);

        if (!canManageAll()) {
            String myOrg = currentOrgCode();
            if (StringUtils.isBlank(myOrg)) {
                throw new JeecgBootException("无法确定当前账号的归属部门（sys_org_code 为空），已拒绝保存。"
                        + "请让管理员在【系统管理 → 用户管理】中为该账号设置部门后再试");
            }
            if (!target.equals(myOrg)) {
                throw new JeecgBootException(current != null
                        ? "无权修改归属：只能由总部管理员调整数据的所属店铺/部门"
                        : "无权把数据归属到部门[" + target + "]：新建数据的归属必须是本人所在部门（" + myOrg + "）");
            }
        }

        if (changed) {
            validateOrgCodeBoundToStore(target);
        }
        return target;
    }

    /**
     * 归属部门必须绑定到某个启用店铺。
     * 否则该数据在任何一个前台站点都看不到，运营会以为"保存成功了"却怎么都找不到商品。
     */
    public void validateOrgCodeBoundToStore(String orgCode) {
        if (StringUtils.isBlank(orgCode)) {
            throw new JeecgBootException("归属部门不能为空");
        }
        if (shopStoreMapper.selectIdByDepartOrgCode(orgCode) == null) {
            throw new JeecgBootException("归属部门[" + orgCode + "]尚未绑定任何启用店铺，"
                    + "该数据将无法在任何前台站点展示。请先到【店铺管理】为对应店铺绑定部门");
        }
    }

    // ==================================================================
    // 店铺 ↔ 部门
    // ==================================================================

    /**
     * 店铺 id → 该店铺绑定部门的 org_code。
     *
     * 后台"按店铺筛选"的入参是店铺 id，而归属列是 org_code，这里做一次收口转换；
     * 转换失败（店铺不存在 / 未绑定部门）一律报错，**不做"忽略条件"的兜底**——
     * 忽略条件就等于把全部店铺的数据返回给调用方。
     */
    public String resolveStoreOrgCode(String storeId) {
        if (StringUtils.isBlank(storeId)) {
            return null;
        }
        ShopStore store = shopStoreMapper.selectById(storeId);
        if (store == null) {
            throw new JeecgBootException("店铺不存在或已删除：" + storeId);
        }
        if (StringUtils.isBlank(store.getDepartId())) {
            throw new JeecgBootException("店铺[" + store.getName() + "]尚未绑定部门，无法按店铺筛选数据。"
                    + "请先到【店铺管理】为它绑定部门");
        }
        String orgCode = shopStoreMapper.selectDepartOrgCodeById(store.getDepartId());
        if (StringUtils.isBlank(orgCode)) {
            throw new JeecgBootException("店铺[" + store.getName() + "]绑定的部门已不存在，请重新绑定部门");
        }
        return orgCode;
    }

    /**
     * 店铺编码解析器拿到店铺后，取它对应的 org_code（店铺端接口用）。
     * 未绑定部门时**报错**：多店铺最危险的事故就是漏掉店铺条件。
     *
     * 需要"降级为空结果而不是整页报错"的场景请用 {@link #resolveStoreOrgCodeOrNull(ShopStore)}。
     */
    public String requireStoreOrgCode(ShopStore store) {
        String orgCode = resolveStoreOrgCodeOrNull(store);
        if (orgCode == null) {
            throw new JeecgBootException(store == null ? "无法确定店铺"
                    : "店铺[" + store.getName() + "]尚未绑定部门，前台无法展示数据。请在【店铺管理】中为它绑定部门");
        }
        return orgCode;
    }

    /**
     * 同 {@link #requireStoreOrgCode(ShopStore)}，但**解析不到时返回 null 并打 warn 日志**，
     * 由调用方降级成"空结果"。
     *
     * 为什么要有这个变体：店铺没绑部门（或绑的部门被删）属于**数据缺失**，
     * 一旦直接抛异常，前台商品/分类/轮播图三个接口会同时 500，整站打不开；
     * 对公开商城来说"空目录"远比"整站报错"可接受，而且不会泄漏任何数据（仍然是 fail-closed）。
     * 真正的配置错误（店铺编码不认识）依旧走 StoreResolver 抛错。
     */
    public String resolveStoreOrgCodeOrNull(ShopStore store) {
        if (store == null) {
            log.warn("店铺解析结果为空，前台接口将返回空数据");
            return null;
        }
        if (StringUtils.isBlank(store.getDepartId())) {
            log.warn("店铺[{}]未绑定部门，前台接口将返回空数据。请在【店铺管理】中为它绑定部门", store.getCode());
            return null;
        }
        String orgCode = shopStoreMapper.selectDepartOrgCodeById(store.getDepartId());
        if (StringUtils.isBlank(orgCode)) {
            log.warn("店铺[{}]绑定的部门已不存在（depart_id={}），前台接口将返回空数据", store.getCode(), store.getDepartId());
            return null;
        }
        return orgCode;
    }

    /**
     * 合并"店铺筛选域"与"账号可见域"，得到最终作用在 SQL 上的前缀列表。
     *
     * @param storeOrgCode 店铺绑定的部门编码，可为空（表示不按店铺筛选）
     * @return {@code null} 表示不限；空列表表示"两个域没有交集"（调用方应直接返回空结果）
     */
    public List<String> mergeScope(String storeOrgCode, List<String> visibleOrgCodes) {
        List<String> scope = new ArrayList<>(1);
        if (StringUtils.isNotBlank(storeOrgCode)) {
            scope.add(storeOrgCode);
        }
        if (visibleOrgCodes == null) {
            // 账号不限部门：是否有交集只取决于店铺筛选
            return scope.isEmpty() ? null : scope;
        }
        if (scope.isEmpty()) {
            return visibleOrgCodes;
        }
        // 交集：店铺部门必须落在账号可见域内，否则返回空（越权筛选直接查不到数据）
        List<String> intersected = new ArrayList<>(1);
        for (String code : scope) {
            if (inScope(code, visibleOrgCodes)) {
                intersected.add(code);
            }
        }
        return intersected;
    }

    /**
     * 把可见域作用到任意查询条件上（列名固定 {@code sys_org_code}）。
     * scope == null 表示不限；空列表表示"两个域没有交集"（恒假条件）。
     */
    public <T> void applyScope(QueryWrapper<T> wrapper, List<String> scope) {
        applyScope(wrapper, scope, "sys_org_code");
    }

    /**
     * 同 {@link #applyScope(QueryWrapper, List)}，可指定列名。
     * 用 likeRight（右模糊）而不是 like：org_code 是前缀式编码，
     * 右模糊既走得了索引，也不会出现 {@code '%A06A01%'} 这种跨部门误命中。
     */
    public <T> void applyScope(QueryWrapper<T> wrapper, List<String> scope, String column) {
        if (scope == null) {
            return;
        }
        if (scope.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.and(w -> {
            for (int i = 0; i < scope.size(); i++) {
                if (i > 0) {
                    w.or();
                }
                w.likeRight(column, scope.get(i));
            }
        });
    }

    /** 供测试/日志用：把可见域描述成可读文本 */
    public String describeScope(List<String> scopes) {
        return scopes == null ? "不限部门(总部)" : String.join(",", scopes);
    }
}
