package org.jeecg.modules.shop.account.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 重置 / 修改密码返回值
 * </p>
 *
 * 契约 2.6 / 2.7 都要求 {@code { success: boolean }}。
 *
 * 注意这里**始终返回 true**（失败走 Result.error，不会走到这个 VO）：
 * 它存在的意义是让前端有个稳定的解构目标（`const { success } = res.result`），
 * 而不是表达"操作可能部分成功"。密码更新是单条 SQL，没有中间态。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@Accessors(chain = true)
@Schema(description = "密码操作返回值")
public class PasswordOpVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "是否成功")
    private Boolean success;
}
