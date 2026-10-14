package org.jeecg.modules.shop.cart.entity.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * <p>
 * 店铺端购物车明细 VO（一条 = 一个 SKU）
 * </p>
 *
 * <b>状态字段是本 VO 的核心</b>：需求要求"商品或 SKU 下架时，购物车里对应的条目置灰并提示"。
 * 后端把判定结果放在 {@code status} 里直出，前端只管按状态渲染，不再自己拼规则——
 * 规则只有一份，就不会出现"详情页说没货、购物车说有货"这种自相矛盾。
 *
 * 状态取值（前端按它映射文案与样式）：
 * <ul>
 *   <li>{@code AVAILABLE} 可购买</li>
 *   <li>{@code STOCK_INSUFFICIENT} 库存不足：还有货，但数量超过了剩余库存</li>
 *   <li>{@code OUT_OF_STOCK} 无货</li>
 *   <li>{@code OFFLINE} 商品已下架 / 已删除 / 不属于当前店铺</li>
 *   <li>{@code SKU_INVALID} 该规格已停售或商品规格已变更（需重新选择）</li>
 * </ul>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Schema(description = "店铺端购物车明细")
public class StoreCartItemVO implements Serializable {
    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // 状态常量
    // ------------------------------------------------------------------

    /** 可购买 */
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    /** 库存不足：还有货，但用户当前数量超过了剩余库存（前端应提示并允许一键改为最大可购数） */
    public static final String STATUS_STOCK_INSUFFICIENT = "STOCK_INSUFFICIENT";
    /** 无货：库存为 0 */
    public static final String STATUS_OUT_OF_STOCK = "OUT_OF_STOCK";
    /** 已失效：商品下架 / 被删除 / 不属于当前店铺 */
    public static final String STATUS_OFFLINE = "OFFLINE";
    /** 规格失效：SKU 已停售，或商品规格已变更导致这条记录对不上任何规格 */
    public static final String STATUS_SKU_INVALID = "SKU_INVALID";

    // ------------------------------------------------------------------
    // 字段
    // ------------------------------------------------------------------

    @Schema(description = "购物车明细ID")
    private String id;

    @Schema(description = "商品ID（同时是店铺端 slug）")
    private String productId;

    @Schema(description = "SKU组合ID；无规格商品为空串")
    private String skuId;

    @Schema(description = "SKU编码")
    private String skuCode;

    @Schema(description = "商品SPU编码")
    private String spuCode;

    @Schema(description = "商品标题（按当前语言）")
    private String title;

    @Schema(description = "封面（按当前语言，缺失回退商品主图）")
    private String coverUrl;

    @Schema(description = "规格文案快照，如 白色/8G/256G；无规格商品为空")
    private String skuName;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "单价（SKU价优先，缺失回退商品基准价）")
    private BigDecimal price;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "小计 = 单价 × 数量（仅 AVAILABLE / STOCK_INSUFFICIENT 有意义）")
    private BigDecimal lineTotal;

    @Schema(description = "当前可售库存（SKU库存优先，无规格商品取商品库存）")
    private Integer stock;

    @Schema(description = "状态：AVAILABLE / STOCK_INSUFFICIENT / OUT_OF_STOCK / OFFLINE / SKU_INVALID")
    private String status;

    @Schema(description = "是否可勾选结算（AVAILABLE / STOCK_INSUFFICIENT 为 true）")
    private Boolean selectable;

    @Schema(description = "商品整体状态原始值: Y-上架 / N-下架（前端可据此给出更细提示）")
    private String productStatus;

    @Schema(description = "SKU 是否可售原始值: Y-可售 / N-不可售")
    private String skuSaleable;

    @Schema(description = "加入购物车时间")
    private Date createTime;

    @Schema(description = "最近一次修改时间（购物车按它倒序）")
    private Date updateTime;

    // ------------------------------------------------------------------
    // 仅用于服务端判定状态的原始信号，不对外暴露
    // ------------------------------------------------------------------

    /** 商品软删除标记（1=已删除）。查出来是为了把"商品被删除"和"商品下架"区分开 */
    @JsonIgnore
    private Integer productDelFlag;

    /** SKU 软删除标记（1=已删除） */
    @JsonIgnore
    private Integer skuDelFlag;

    /** 该商品当前存活的 SKU 组合数量（0 表示是无规格商品） */
    @JsonIgnore
    private Integer skuTypeCount;

    /** 商品归属部门编码，用于校验商品是否还属于当前店铺 */
    @JsonIgnore
    private String productOrgCode;
}
