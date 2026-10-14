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
import java.util.Date;

/**
 * <p>
 * 库存流水（ck_shop_stock_log）—— 只追加
 * </p>
 *
 * <h3>这张表的唯一目的：让"库存对不上"变成一道可以解的题</h3>
 * 库存问题的可怕之处在于**发现得太晚**：运营看到商品页显示有货、下单时却报没货，
 * 或者明明卖完了还能一直卖。此时库里只剩一个数字，谁改的都查不出来。
 *
 * <p>每条流水都同时记录变动前后的 {@code stock} 与 {@code frozen_stock}，因此：
 * <ul>
 *   <li>取某 SKU 的最后一条流水，其 {@code after_stock} 必须等于商品表当前 {@code stock}，
 *       不等就说明有**绕过本模块**的写入（直连 SQL、其它系统），可以立刻发现；</li>
 *   <li>把某订单的全部流水按时间排序，就能还原"预占 → 实扣 → 回补"是否配对。
 *       如果只看到 OCCUPY 没有对应的 DEDUCT/RELEASE，说明有订单卡在中间状态，
 *       那就是库存被永久占用的根因。</li>
 * </ul>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@TableName("ck_shop_stock_log")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "库存流水")
public class ShopStockLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Schema(description = "店铺ID")
    private String storeId;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "SKU组合ID; 无规格为空串")
    private String skuId;

    @Schema(description = "变动类型: OCCUPY/RELEASE/DEDUCT/RESTORE/ADJUST")
    private String changeType;

    @Schema(description = "变动数量(恒为正, 方向由 changeType 决定)")
    private Integer quantity;

    @Schema(description = "对 stock 列的净变动")
    private Integer stockDelta;

    @Schema(description = "对 frozen_stock 列的净变动")
    private Integer frozenDelta;

    @Schema(description = "变动前实际库存")
    private Integer beforeStock;

    @Schema(description = "变动后实际库存")
    private Integer afterStock;

    @Schema(description = "变动前预占库存")
    private Integer beforeFrozen;

    @Schema(description = "变动后预占库存")
    private Integer afterFrozen;

    @Schema(description = "关联订单号")
    private String orderNo;

    @Schema(description = "关联业务ID(支付单号/退款单号)")
    private String bizId;

    @Schema(description = "操作者")
    private String operator;

    @Schema(description = "备注")
    private String remark;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "发生时间")
    private Date createTime;
}
