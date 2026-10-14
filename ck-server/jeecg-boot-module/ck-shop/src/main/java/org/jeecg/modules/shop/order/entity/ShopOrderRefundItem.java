package org.jeecg.modules.shop.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * <p>
 * 退款明细（ck_shop_order_refund_item）—— 按订单行退款
 * </p>
 *
 * 支撑"一单多件只退其中一件"的部分退款，也让退款金额可以逐行核对：
 * 退款总额 == 各行退款金额之和，任何不一致都能立刻发现。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@TableName("ck_shop_order_refund_item")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "退款明细")
public class ShopOrderRefundItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Schema(description = "退款单ID")
    private String refundId;

    @Schema(description = "退款单号")
    private String refundNo;

    @Schema(description = "订单ID")
    private String orderId;

    @Schema(description = "订单明细ID")
    private String orderItemId;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "SKU组合ID; 无规格为空串")
    private String skuId;

    @Schema(description = "商品名称快照")
    private String productName;

    @Schema(description = "规格快照")
    private String skuName;

    @Schema(description = "退款单价(成交价快照)")
    private BigDecimal price;

    @Schema(description = "退款数量")
    private Integer quantity;

    @Schema(description = "本行退款金额")
    private BigDecimal refundAmount;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建日期")
    private Date createTime;
}
