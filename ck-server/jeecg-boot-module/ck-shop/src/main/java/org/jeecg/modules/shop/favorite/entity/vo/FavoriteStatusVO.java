package org.jeecg.modules.shop.favorite.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 收藏状态（商品详情页心形按钮的数据源）
 * </p>
 *
 * 为什么把「是否已收藏」和「收藏总数」放在同一个出参里：
 * 详情页上这两个数字必须**同源同刻**——分两次请求会出现
 * "心形亮了但收藏数是切换前的旧值"这种一闪而过的错位。
 * 因此收藏 / 取消收藏接口也直接返回这个对象，前端一次拿到最终状态。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@Accessors(chain = true)
@Schema(description = "收藏状态")
public class FavoriteStatusVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 商品ID */
    @Schema(description = "商品ID")
    private String productId;

    /** 当前登录用户是否已收藏该商品 */
    @Schema(description = "是否已收藏")
    private Boolean favorited;

    /**
     * 商品收藏总数（ck_shop_product.favorite_count，全局口径、不只算当前用户）。
     * 为 0 表示没有数据，前端按项目约定整块不渲染。
     */
    @Schema(description = "收藏总数")
    private Integer favoriteCount;
}
