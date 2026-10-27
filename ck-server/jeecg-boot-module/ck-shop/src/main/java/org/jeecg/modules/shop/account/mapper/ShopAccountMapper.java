package org.jeecg.modules.shop.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.shop.account.entity.ShopAccountUser;

import java.util.List;

/**
 * <p>
 * 店铺端账号 Mapper —— 直接操作 {@code sys_user} / {@code sys_role} / {@code sys_user_role}
 * </p>
 *
 * <h3>为什么不用 {@code ISysUserService} / {@code SysUser} 实体</h3>
 * <b>ck-shop 模块的编译期依赖里没有 {@code jeecg-system-biz}</b>
 * （只有 {@code jeecg-boot-base-core} 与 {@code ck-system}），
 * 因此 {@code org.jeecg.modules.system.entity.SysUser}、
 * {@code ISysUserService}、{@code SysUserRoleMapper} 在本模块**根本不可见**。
 * （已通过 {@code mvn dependency:build-classpath} 确认：classpath 上没有任何
 * jeecg-system-biz 制品。）
 *
 * <p>可行的三条路：
 * <ol>
 *   <li>给 ck-shop 加 {@code jeecg-system-biz} 依赖 —— <b>被写入范围禁止</b>
 *       （会改动共享模块的依赖图，且系统模块并非为店铺端设计）；</li>
 *   <li>把店铺端账号逻辑写进 jeecg-system-biz —— <b>被写入范围禁止</b>；</li>
 *   <li><b>（本实现）</b>在 ck-shop 内自带一个只映射"店铺端真正需要的那几列"的
 *       轻量实体 {@link ShopAccountUser} + 本 Mapper，用显式 SQL 读写。</li>
 * </ol>
 *
 * <p>方案 3 的代价是"sys_user 的列在两处各写了一份"，好处是**边界清晰**：
 * 店铺端只碰 username / password / salt / email / realname / avatar / status /
 * del_flag / org_code / register_store_* / google_sub / pwd_set / create_time，
 * 管理端字段（工号、岗位、租户、签名…）在这里完全不存在，
 * 也就不可能被误写。这与"店铺端是独立子系统"的定位是一致的。
 *
 * <p>⚠️ 迁移脚本新增列后，本 Mapper 的 SQL 与 {@link ShopAccountUser} 必须同步更新，
 * 两者任一滞后都会立刻表现为"字段查不到"。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Mapper
public interface ShopAccountMapper extends BaseMapper<ShopAccountUser> {

    // ==================================================================
    // 查询
    // ==================================================================

    /**
     * 按邮箱查账号（**仅未删除**）。
     *
     * <p>过滤 {@code del_flag = 0} 与
     * {@code SysUserMapper.getUserByEmail}（{@code where email = ? and del_flag = 0}）同口径。
     */
    ShopAccountUser selectByEmail(@Param("email") String email);

    /**
     * 按邮箱查 id，**不过滤 del_flag**。
     *
     * <p>用于注册前的唯一性预检：{@code uniq_sys_user_email} 是普通唯一索引，
     * 已软删除的账号仍然占着那个邮箱。若这里过滤了 del_flag，
     * 就会出现"预检说没占用 → 真去 INSERT 撞唯一索引报 500"这种最差的组合。
     */
    String selectIdByEmailAny(@Param("email") String email);

    /** 按 username 查 id（用于 username 冲突检测，含已删除：唯一索引同样不区分 del_flag） */
    String selectIdByUsernameAny(@Param("username") String username);

    /** 按 id 查账号（仅未删除） */
    ShopAccountUser selectByIdActive(@Param("id") String id);

    /** 按 Google sub 查 id，含已删除 */
    String selectIdByGoogleSub(@Param("googleSub") String googleSub);

    // ==================================================================
    // 写入
    // ==================================================================

    /**
     * 注册 / 自动注册建号。
     *
     * <p>用显式列名的 INSERT（而不是 {@code insert(实体)}）：
     * 店铺端只允许写自己那几列，走实体会把 {@code ShopAccountUser} 里的 null 字段
     * 也一并写成 NULL，未来加字段时容易误清数据。显式列名让"这个接口能写什么"一目了然。
     *
     * @return 受影响行数
     */
    int insertCustomer(ShopAccountUser user);

    /** 写入注册来源店铺（仅注册那一刻调用一次，不覆盖已有值） */
    int updateRegisterStore(@Param("userId") String userId,
                            @Param("storeCode") String storeCode,
                            @Param("storeId") String storeId);

    /** 绑定 / 解绑 Google 标识（null 表示解绑） */
    int updateGoogleSub(@Param("userId") String userId,
                        @Param("googleSub") String googleSub);

    /** 更新"是否由用户设置过密码" */
    int updatePwdSet(@Param("userId") String userId,
                     @Param("pwdSet") Integer pwdSet);

    /**
     * 更新密码 + salt + pwd_set + last_pwd_update_time，单条 SQL。
     *
     * <p>单条而非"先 updateById 再 updatePwdSet"：分两条会留下
     * "密码换了但 pwd_set 还是 0"的中间态（用户能登进来、前端却一直显示"设置密码"）。
     */
    int updateCredential(@Param("userId") String userId,
                         @Param("password") String password,
                         @Param("salt") String salt,
                         @Param("pwdSet") Integer pwdSet);

    /**
     * 保存个人资料（昵称 + 手机号）。
     *
     * <p>只写这两列：邮箱是登录标识、username 不可改、密码另走 {@code updateCredential}。
     * 显式列名让"这个接口能改什么"在 SQL 层面一目了然。</p>
     *
     * <p>{@code phone} 传 null 表示清空（写 NULL，而不是空串）——
     * {@code uniq_sys_user_phone} 是唯一索引，多个账号写空串会互相冲突。</p>
     *
     * @return 受影响行数（0 表示用户不存在或已注销）
     */
    int updateProfile(@Param("userId") String userId,
                      @Param("realname") String realname,
                      @Param("phone") String phone);

    /**
     * 更新头像（null 表示恢复默认：无头像）。
     *
     * @return 受影响行数（0 表示用户不存在或已注销）
     */
    int updateAvatar(@Param("userId") String userId,
                     @Param("avatar") String avatar);

    // ==================================================================
    // 角色
    // ==================================================================

    /** 该用户是否拥有指定 role_code 的角色（>0 表示有） */
    int countUserRoleByRoleCode(@Param("userId") String userId,
                                @Param("roleCode") String roleCode);

    /** 按 role_code 取角色 id；null 表示角色还没建（迁移脚本没跑） */
    String selectRoleIdByRoleCode(@Param("roleCode") String roleCode);

    /** 该用户的全部 role_code（仅用于日志，便于回答"这账号到底有什么角色"） */
    List<String> selectRoleCodesByUserId(@Param("userId") String userId);

    /** 绑定角色 */
    int insertUserRole(@Param("id") String id,
                       @Param("userId") String userId,
                       @Param("roleId") String roleId);
}
