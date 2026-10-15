package org.jeecg.modules.shop.account.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 店铺端用户信息（轻量）
 * </p>
 *
 * 契约 2.8 的字段清单：id / username / realname / email / avatar / phone /
 * hasPassword / registerStoreCode / createTime。
 *
 * <b>为什么不直接返回 SysUser 实体：</b>
 * {@code SysUser} 上有 salt、password、thirdId、tenantId、workNo、departIds 等
 * 一大堆管理端字段。虽然 password/salt 打了 {@code @JsonProperty(WRITE_ONLY)}，
 * 但其余字段（工号、负责部门、租户）对 C 端用户毫无意义却会泄露管理端结构。
 * 店铺端用一个只含 9 个字段的 VO，输出面**永远**是确定的 ——
 * 将来给 SysUser 加字段也不会意外泄漏到这里。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@Accessors(chain = true)
@Schema(description = "店铺端用户信息")
public class ShopProfileVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private String id;

    /** 系统生成、永不可改（u_ + 8 位小写字母数字） */
    @Schema(description = "登录账号(系统生成,不可改)")
    private String username;

    @Schema(description = "昵称/真实姓名")
    private String realname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "电话")
    private String phone;

    /**
     * 是否**由用户自己设置过**密码。
     *
     * 前端据此决定显示「设置密码」还是「修改密码」——
     * 不能靠 password 是否为空判断：自动注册的账号也有密码哈希（系统随机占位）。
     */
    @Schema(description = "是否已由用户设置过密码")
    private Boolean hasPassword;

    @Schema(description = "注册来源店铺编码")
    private String registerStoreCode;

    @Schema(description = "创建时间")
    private String createTime;
}
