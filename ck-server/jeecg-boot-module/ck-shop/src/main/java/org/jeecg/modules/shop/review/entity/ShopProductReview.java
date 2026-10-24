package org.jeecg.modules.shop.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
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
 * 商品评价（一条订单明细只能评价一次）
 * </p>
 *
 * 设计决策：
 * <ul>
 *   <li><b>评价挂在订单明细上，不挂在商品上</b>：只有真正买过的人才能评价，
 *       这是"评分不是编的"的唯一保证。唯一索引 {@code uk_review_order_item} 把这条规则
 *       写进数据库，而不是靠业务代码先查一遍（并发下那种检查必输）；</li>
 *   <li><b>保存下单时的规格与商品名快照</b>：商品改名/规格下架后，评价里显示的
 *       仍然是买家当时买的那样东西；</li>
 *   <li><b>不存商品评分汇总</b>：{@code ck_shop_product.review_count / rating_avg}
 *       是冗余缓存，由本模块在**同一个事务**里按真实评价重算，展示值则实时聚合。</li>
 * </ul>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-21
 */

@Data
@TableName("ck_shop_product_review")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "商品评价")
public class ShopProductReview implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    /** 商品ID */
    @Schema(description = "商品ID")
    private String productId;

    /** 店铺ID */
    @Schema(description = "店铺ID")
    private String storeId;

    /** 评价时店铺绑定的部门（快照） */
    @Schema(description = "评价时店铺绑定部门")
    private String storeOrgCode;

    /** 来源订单ID */
    @Schema(description = "订单ID")
    private String orderId;

    /** 来源订单号（冗余，便于排查与展示） */
    @Schema(description = "订单号")
    private String orderNo;

    /** 来源订单明细ID（唯一） */
    @Schema(description = "订单明细ID")
    private String orderItemId;

    /** 评价人 */
    @Schema(description = "评价人ID")
    private String userId;

    /** 评价人账号（冗余快照） */
    @Schema(description = "评价人账号")
    private String username;

    /** 下单时的 SKU 组合ID（快照） */
    @Schema(description = "SKU组合ID")
    private String skuCombinationId;

    /** 下单时的规格文案（快照） */
    @Schema(description = "规格文案")
    private String skuName;

    /** 评分 1-5 */
    @Schema(description = "评分 1-5")
    private Integer rating;

    /** 评价内容，可空（允许只打分不写字） */
    @Schema(description = "评价内容")
    private String content;

    /**
     * 评价图片：JSON 数组字符串（如 {@code ["https://…","https://…"]}），NULL 表示无图。
     *
     * 为什么是一列 JSON 而不是子表：评价图片是"跟着一条评价走的只读列表"，
     * 没有单独查询/统计需求，子表只会多一次 JOIN 和一套增删改逻辑。
     * 服务端读取时会走 {@code parseImages} 的白名单校验（只认 http(s)/站内绝对路径，最多 9 张）。
     */
    @Schema(description = "评价图片(JSON 数组字符串；NULL=无图)")
    private String images;

    /** 是否匿名: true-隐藏昵称 */
    @Schema(description = "是否匿名")
    private Boolean anonymous;

    /** 展示状态: Y-展示 N-后台隐藏（隐藏后不计入评分与条数） */
    @Schema(description = "展示状态: Y-展示 N-后台隐藏")
    private String status;

    @Schema(description = "创建人")
    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建日期")
    private Date createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新日期")
    private Date updateTime;

    @TableLogic(value = "0", delval = "1")
    @Schema(description = "删除标记")
    private Integer delFlag;
}
