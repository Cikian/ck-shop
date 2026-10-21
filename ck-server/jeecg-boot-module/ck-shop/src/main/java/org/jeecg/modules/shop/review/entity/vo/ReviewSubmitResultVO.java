package org.jeecg.modules.shop.review.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 提交评价的返回结果：新建的那条评价 + 商品最新的真实汇总。
 * </p>
 *
 * 一并返回汇总，是为了让前端**不用再发一次请求**就能把"评分 4.5、12 条评价"更新成最新值；
 * 而且这个汇总与刚插入的评价出自同一个事务，不存在"评价列表多了一条、评分还是旧值"的窗口。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-21
 */

@Data
@Accessors(chain = true)
@Schema(description = "提交评价结果")
public class ReviewSubmitResultVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "新建的评价")
    private StoreReviewVO review;

    @Schema(description = "商品最新的真实评价汇总")
    private StoreReviewSummaryVO summary;
}
