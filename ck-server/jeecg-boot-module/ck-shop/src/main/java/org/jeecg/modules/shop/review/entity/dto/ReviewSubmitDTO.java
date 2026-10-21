package org.jeecg.modules.shop.review.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 提交评价入参。
 * </p>
 *
 * 只允许提交这三样东西：**哪一行订单明细**、打几分、写什么。
 * 没有 productId / storeId / userId —— 这些一律由服务端从订单明细反查，
 * 让前端传就等着被伪造"给别店商品刷 5 星"。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-21
 */

@Data
@Accessors(chain = true)
@Schema(description = "提交评价入参")
public class ReviewSubmitDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "订单明细ID(来自待评价列表)")
    private String orderItemId;

    @Schema(description = "评分 1-5")
    private Integer rating;

    @Schema(description = "评价内容，可空，最长 500 字")
    private String content;

    @Schema(description = "是否匿名，默认否")
    private Boolean anonymous;
}
