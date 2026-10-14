package org.jeecg.modules.shop.promotion.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 促销活动冲突检测结果 VO
 * </p>
 *
 * 一行 = "某活动 在 某商品 上与本次要保存的活动时间窗重叠"。
 * 前端据此提示"这个商品在 X 活动里已经占用了这段时间"，
 * 因此必须带上**活动信息 + 商品信息**两块，只回活动 id 运营看不懂。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@Schema(description = "促销活动冲突检测结果")
public class PromotionConflictVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "冲突活动ID")
    private String promotionId;

    @Schema(description = "冲突活动编码")
    private String promotionCode;

    @Schema(description = "冲突活动名称")
    private String promotionTitle;

    @Schema(description = "冲突活动类型")
    private String type;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "冲突活动开始时间")
    private Date startTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "冲突活动结束时间")
    private Date endTime;

    @Schema(description = "冲突商品ID")
    private String productId;

    @Schema(description = "冲突商品名称")
    private String productTitle;

    @Schema(description = "冲突活动所属店铺ID")
    private String storeId;

    @Schema(description = "冲突活动所属店铺名称")
    private String storeName;
}
