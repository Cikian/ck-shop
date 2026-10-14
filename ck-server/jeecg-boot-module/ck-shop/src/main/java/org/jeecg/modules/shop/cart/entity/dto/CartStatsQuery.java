package org.jeecg.modules.shop.cart.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 加购统计查询条件（后台）
 * </p>
 *
 * 所有字段都可空：不传 storeId / orgCode 表示"我有权限看到的全部店铺"，
 * 时间不传则默认统计最近 30 天（趋势图默认最近 14 天）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Schema(description = "加购统计查询条件")
public class CartStatsQuery implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "店铺ID（空=全部可见店铺）")
    private String storeId;

    @Schema(description = "开始日期 yyyy-MM-dd（含当天 00:00:00）")
    private String startDate;

    @Schema(description = "结束日期 yyyy-MM-dd（含当天 23:59:59）")
    private String endDate;

    @Schema(description = "趋势统计天数，默认 14，最大 90")
    private Integer days;

    @Schema(description = "排行榜取前 N 条，默认 10，最大 100")
    private Integer limit;

    @Schema(description = "商品标题模糊搜索")
    private String keyword;

    @Schema(description = "明细页码，从 1 开始")
    private Integer pageNo;

    @Schema(description = "明细分页大小，默认 20")
    private Integer pageSize;
}
