package org.jeecg.modules.shop.favorite.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 收藏 / 取消收藏入参
 * </p>
 *
 * 只有一个商品 ID：收藏是"这个用户对这个商品"的开关，不需要 SKU、不需要数量。
 * 前端永远只传 productId，服务端自己解析出用户与店铺（绝不信前端传的 userId / storeId）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@Schema(description = "收藏/取消收藏入参")
public class FavoriteToggleDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 商品ID（spu_code 不接受：收藏必须锁定到唯一的商品主键） */
    @Schema(description = "商品ID")
    private String productId;
}
