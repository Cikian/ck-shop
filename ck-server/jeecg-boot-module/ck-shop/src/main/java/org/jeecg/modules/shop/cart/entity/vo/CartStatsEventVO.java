package org.jeecg.modules.shop.cart.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 后台加购统计 · 加购明细（流水）
 * </p>
 *
 * 用于"总览数字对不上时能一路查到具体某一条"，运营/开发排查都靠它。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Schema(description = "加购明细")
public class CartStatsEventVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "流水ID")
    private String id;

    @Schema(description = "用户账号")
    private String username;

    @Schema(description = "店铺名称")
    private String storeName;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "商品标题")
    private String title;

    @Schema(description = "SPU编码")
    private String spuCode;

    @Schema(description = "SKU组合ID；无规格商品为空串")
    private String skuId;

    @Schema(description = "规格文案")
    private String skuName;

    @Schema(description = "本次数量")
    private Integer quantity;

    @Schema(description = "事件类型：ADD/INCR/UPDATE/REMOVE/CLEAR")
    private String eventType;

    @Schema(description = "来源：detail/list/cart/api")
    private String source;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "发生时间")
    private Date createTime;
}
