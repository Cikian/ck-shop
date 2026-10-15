package org.jeecg.modules.shop.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 店铺端买家账号 —— {@code sys_user} 的**店铺端视图**
 * </p>
 *
 * <h3>为什么要另立一个实体，而不是用管理端的 {@code SysUser}</h3>
 * <b>ck-shop 模块编译期看不到 {@code jeecg-system-biz}</b>
 * （依赖只有 {@code jeecg-boot-base-core} + {@code ck-system}，
 * 已用 {@code mvn dependency:build-classpath} 确认），
 * 所以 {@code org.jeecg.modules.system.entity.SysUser} 在本模块不可引用；
 * 而给 ck-shop 加 jeecg-system-biz 依赖、或把代码写进 jeecg-system-biz，
 * 都超出了本次改造的写入范围。
 *
 * <p>因此这里做一个**只含店铺端必需列**的映射：
 * 管理端字段（工号、岗位、租户、个性签名、部门岗位…）在这张视图里根本不存在，
 * 也就不可能被店铺端代码误读或误写。
 *
 * <h3>字段与 sys_user 列的对应</h3>
 * <ul>
 *   <li>{@code orgCode} ← {@code sys_org_code}：买家**留空**（契约决策 3）；</li>
 *   <li>{@code delFlag} ← {@code del_flag}：0 正常 / 1 已删除；</li>
 *   <li>{@code status} ← {@code status}：1 正常 / 2 冻结；</li>
 *   <li>{@code registerStoreCode} / {@code registerStoreId}：迁移脚本新增；</li>
 *   <li>{@code googleSub}：迁移脚本新增，独立于 {@code third_id}；</li>
 *   <li>{@code pwdSet}：迁移脚本新增，1 用户设过密码 / 0 系统随机占位。</li>
 * </ul>
 *
 * <h3>关于 {@code @TableLogic}</h3>
 * <b>刻意不加</b>。理由：注册前的"邮箱是否已被占用"必须能查到**已软删除**的行
 * （{@code uniq_sys_user_email} 不区分 del_flag）。若打了 {@code @TableLogic}，
 * MyBatis-Plus 会自动给所有查询追加 {@code del_flag = 0}，
 * 我们就再也查不到那些"已删除但占着邮箱"的行 —— 而这恰恰是最需要预检的情况。
 * 删除状态由每条 SQL 自己显式控制，见 {@code ShopAccountMapper.xml}。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@TableName("sys_user")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "店铺端买家账号(sys_user 店铺端视图)")
public class ShopAccountUser implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    /** 登录账号：系统生成，{@code u_} + 8 位小写字母数字，永不可改 */
    @Schema(description = "登录账号(系统生成,不可改)")
    private String username;

    /** 昵称：默认取邮箱 @ 前缀，用户可在个人中心改 */
    @Schema(description = "昵称")
    private String realname;

    /**
     * 密码哈希（{@code PasswordUtil.encrypt(username, 明文, salt)}）。
     * ⚠️ 绝不能进任何响应体 —— VO 转换时逐字段挑选，不整体透传实体。
     */
    @Schema(description = "密码哈希(禁止外传)")
    private String password;

    /** md5 密码盐（8 位随机串） */
    @Schema(description = "密码盐(禁止外传)")
    private String salt;

    @Schema(description = "头像")
    private String avatar;

    /** sys_user 里 email 是 varchar(45)，写入前需注意长度 */
    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "电话")
    private String phone;

    /** 归属部门编码。买家**留空**：C 端用户不属于任何店铺部门 */
    @Schema(description = "归属部门编码(买家留空)")
    private String orgCode;

    /** 1 正常 / 2 冻结 */
    @Schema(description = "状态(1正常 2冻结)")
    private Integer status;

    /** 0 正常 / 1 已删除 */
    @Schema(description = "删除标记(0正常 1已删除)")
    private Integer delFlag;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;

    @Schema(description = "最后一次修改密码时间")
    private Date lastPwdUpdateTime;

    // ------------------------------------------------------------------
    // 迁移脚本 V3.9.5_12 新增的店铺端专有列
    // ------------------------------------------------------------------

    /** 注册来源店铺编码（仅注册时写一次，用于营销归因，不作为隔离依据） */
    @Schema(description = "注册来源店铺编码")
    private String registerStoreCode;

    /** 注册来源店铺ID */
    @Schema(description = "注册来源店铺ID")
    private String registerStoreId;

    /**
     * Google 账号唯一标识(sub)。
     * 独立列而**不复用** {@code third_id}：后者是 JeecgBoot 原生第三方登录用的，
     * 历史数据里已有值，混用会出现两个系统抢同一列。
     */
    @Schema(description = "Google 账号唯一标识(sub)")
    private String googleSub;

    /**
     * 用户是否设置过密码：1 是 / 0 系统随机占位。
     *
     * <p>自动注册（Google / 邮箱验证码首次登录）的用户有密码哈希但**无人知道明文**，
     * 因此不能用"password 是否为空"判断用户有没有密码。
     */
    @Schema(description = "是否由用户设置过密码(1是 0系统随机占位)")
    private Integer pwdSet;
}
