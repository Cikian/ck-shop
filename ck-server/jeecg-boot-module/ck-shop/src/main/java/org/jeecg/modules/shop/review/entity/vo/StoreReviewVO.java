package org.jeecg.modules.shop.review.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 店铺端评价列表项。
 * </p>
 *
 * <b>昵称在后端就打码</b>：把完整账号发给浏览器，等于让任何人 F12 就能收集店铺用户名单。
 * 匿名评价的昵称固定为「匿名用户」，连打码后的账号也不给（否则"打码"只是看起来安全）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-21
 */

@Data
@Accessors(chain = true)
@Schema(description = "店铺端评价列表项")
public class StoreReviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "评价ID")
    private String id;

    @Schema(description = "评分 1-5")
    private Integer rating;

    @Schema(description = "评价内容；只打分没写字时为 null")
    private String content;

    @Schema(description = "展示昵称(已打码；匿名为「匿名用户」)")
    private String username;

    @Schema(description = "是否匿名")
    private Boolean anonymous;

    @Schema(description = "下单时的规格文案")
    private String skuName;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "评价时间")
    private Date createTime;
}
