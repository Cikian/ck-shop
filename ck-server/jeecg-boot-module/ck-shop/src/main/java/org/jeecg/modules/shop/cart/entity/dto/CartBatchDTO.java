package org.jeecg.modules.shop.cart.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 购物车批量操作入参（批量删除 / 批量清理失效）
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Schema(description = "购物车批量操作入参")
public class CartBatchDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 购物车明细ID集合。为空时接口直接报错而不是"删除全部"——
     * 传空数组多半是前端状态错乱，静默清空购物车是不可接受的破坏性行为。
     */
    @Schema(description = "购物车明细ID集合")
    private List<String> ids;

    @Schema(description = "来源")
    private String source;
}
