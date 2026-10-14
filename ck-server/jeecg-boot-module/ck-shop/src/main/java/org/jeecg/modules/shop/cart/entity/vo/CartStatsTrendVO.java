package org.jeecg.modules.shop.cart.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 后台加购统计 · 按日趋势
 * </p>
 *
 * <b>刻意补齐"零加购的那些天"</b>：SQL 的 GROUP BY 不会返回没有数据的日期，
 * 前端直接画折线会出现"日期跳档"，看不出真实趋势。因此补齐动作在 Service 里做，
 * 返回的列表长度恒等于统计天数、日期连续。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Schema(description = "加购趋势（按日）")
public class CartStatsTrendVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "日期 yyyy-MM-dd")
    private String statDate;

    @Schema(description = "当日加购次数")
    private Long addTimes;

    @Schema(description = "当日加购件数")
    private Long addQuantity;

    @Schema(description = "当日加购用户数（去重）")
    private Long addUsers;

    @Schema(description = "当日涉及商品数（去重）")
    private Long addProducts;
}
