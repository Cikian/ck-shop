package org.jeecg.modules.shop.cart.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 购物车角标（极轻量）
 * </p>
 *
 * 顶部导航每个页面都要显示购物车数量，因此单独给一个只走两条聚合 SQL 的接口，
 * 不加载明细、不做商品 JOIN。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Schema(description = "购物车角标")
public class StoreCartCountVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "明细条目数")
    private Long totalItems;

    @Schema(description = "商品总件数")
    private Long totalQuantity;
}
