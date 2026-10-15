package org.jeecg.modules.shop.account.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 登录/注册成功后的用户信息（随 token 一起返回）
 * </p>
 *
 * 契约里 {@code Result<{ token, userInfo, storeCode }>} / {@code { token, userInfo }}
 * 里的 {@code userInfo} 就是这个对象。
 *
 * ⚠️ **不含 password / salt**：这个结构会被序列化进 HTTP 响应体，
 * 一旦带上密码哈希，它就同时出现在浏览器内存、BFF 日志、以及任何抓包记录里。
 * 这里逐字段显式列出，就是为了让"加字段"变成一次有意识的决定。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "登录用户信息")
public class LoginUserVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private String id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "昵称/真实姓名")
    private String realname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "是否已由用户设置过密码")
    private Boolean hasPassword;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;
}
