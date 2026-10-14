package org.jeecg.modules.shop.cart.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 删除购物车单条入参
 * </p>
 *
 * 用 JSON body 而不是 URL 参数：BFF 层对 POST 的签名只覆盖 body（与登录接口同一形状），
 * 少一种参数位置就少一处"签名对不上"的排查成本。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Schema(description = "删除购物车单条入参")
public class CartRemoveDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "购物车明细ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemId;

    @Schema(description = "来源")
    private String source;
}
