package org.jeecg.modules.shop.cart.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 后台加购统计 · 总览
 * </p>
 *
 * 口径说明（避免"运营看到的数字和产品理解的不一样"）：
 * <ul>
 *   <li>{@code addTimes} 加购次数：统计区间内"点击加入购物车"的动作次数，
 *       包含首次加入（ADD）与同 SKU 再次累加（INCR）；改数量/删除不计入；</li>
 *   <li>{@code addQuantity} 加购件数：上述动作携带的数量之和；</li>
 *   <li>{@code addUsers} 加购用户数：去重后的用户数（同一人反复加购只算 1）；</li>
 *   <li>{@code addProducts} / {@code addSkus}：去重后的商品数 / SKU 组合数；</li>
 *   <li>{@code cart*} 系列是**当前时点**的购物车快照，不受统计区间影响。</li>
 * </ul>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Schema(description = "加购统计总览")
public class CartStatsOverviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "加购次数（ADD + INCR 动作数）")
    private Long addTimes;

    @Schema(description = "加购件数")
    private Long addQuantity;

    @Schema(description = "加购用户数（去重）")
    private Long addUsers;

    @Schema(description = "涉及商品数（去重）")
    private Long addProducts;

    @Schema(description = "涉及SKU数（去重，无规格商品不计入）")
    private Long addSkus;

    @Schema(description = "人数转化参考：人均加购件数")
    private java.math.BigDecimal quantityPerUser;

    @Schema(description = "当前购物车条目数（快照）")
    private Long cartItems;

    @Schema(description = "当前购物车件数（快照）")
    private Long cartQuantity;

    @Schema(description = "当前购物车涉及用户数（快照）")
    private Long cartUsers;

    @Schema(description = "统计区间开始 yyyy-MM-dd HH:mm:ss")
    private String rangeStart;

    @Schema(description = "统计区间结束 yyyy-MM-dd HH:mm:ss")
    private String rangeEnd;
}
