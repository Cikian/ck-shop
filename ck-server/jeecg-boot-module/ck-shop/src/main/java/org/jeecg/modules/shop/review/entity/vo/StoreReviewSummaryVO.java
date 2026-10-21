package org.jeecg.modules.shop.review.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 商品评价汇总（评分 + 条数 + 星级分布）。
 * </p>
 *
 * 三份数据出自**同一次聚合**：分别查三次的话，用户会看到"4.5 分 / 12 条评价 / 分布加起来 11 条"
 * 这种自相矛盾的组合（中间有人提交了一条评价）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-21
 */

@Data
@Accessors(chain = true)
@Schema(description = "商品评价汇总")
public class StoreReviewSummaryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "平均评分 0.0-5.0；无评价时为 0")
    private BigDecimal ratingAvg;

    @Schema(description = "评价条数")
    private Integer reviewCount;

    @Schema(description = "星级分布(恒定 5 项，5→1，无评价的档位 count=0)")
    private List<RatingBucketVO> distribution = new ArrayList<>();

    /**
     * 单个星级的评价条数。
     */
    @Data
    @Accessors(chain = true)
    @Schema(description = "星级分布项")
    public static class RatingBucketVO implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "星级 1-5")
        private Integer rating;

        @Schema(description = "该星级的评价条数")
        private Integer count;
    }
}
