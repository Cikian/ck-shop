package org.jeecg.modules.shop.cart.entity;

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
 * 加购行为流水：后台"加购统计"的唯一数据源。
 * </p>
 *
 * 为什么独立成表、而不是在商品/SKU 上加一个累计计数字段：
 * <ol>
 *   <li>累计计数只能回答"总共多少次"，回答不了"最近 7 天""哪个店铺""哪些用户"；</li>
 *   <li>购物车被用户清空后数据就没了，而运营恰恰想看到"加了又放弃"的行为；</li>
 *   <li>流水表只追加不修改，不存在并发累加的写冲突（计数器字段在秒杀场景下会热点竞争）。</li>
 * </ol>
 *
 * {@code orgCode} 是加购那一刻商品归属部门的**快照**：统计接口按店铺（部门）过滤时
 * 直接用这一列，不必回表 join 商品；商品后来被调拨到别的店铺也不会篡改历史统计口径。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@TableName("ck_shop_cart_event")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "加购行为流水")
public class ShopCartEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 事件类型：首次加入购物车 */
    public static final String TYPE_ADD = "ADD";
    /** 事件类型：同一 SKU 再次加购（数量累加） */
    public static final String TYPE_INCR = "INCR";
    /** 事件类型：修改数量 */
    public static final String TYPE_UPDATE = "UPDATE";
    /** 事件类型：删除单条 */
    public static final String TYPE_REMOVE = "REMOVE";
    /** 事件类型：清空购物车 */
    public static final String TYPE_CLEAR = "CLEAR";

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "用户账号")
    private String username;

    @Schema(description = "店铺ID")
    private String storeId;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "SKU组合ID；无规格商品为空串")
    private String skuId;

    /**
     * 本次变动数量（取绝对值，恒为正）
     */
    @Schema(description = "本次变动数量")
    private Integer quantity;

    /**
     * 事件类型：ADD / INCR / UPDATE / REMOVE / CLEAR
     */
    @Schema(description = "事件类型")
    private String eventType;

    /**
     * 来源：detail-商品详情 / list-商品列表 / cart-购物车页 / api-其它
     */
    @Schema(description = "来源")
    private String source;

    /**
     * 加购时商品归属部门（快照）
     */
    @Schema(description = "商品归属部门快照")
    private String orgCode;

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
