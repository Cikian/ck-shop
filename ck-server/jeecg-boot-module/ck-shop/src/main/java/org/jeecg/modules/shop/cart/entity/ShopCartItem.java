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
 * 购物车明细
 * </p>
 *
 * 设计决策（与建表脚本注释一致，这里再强调一次）：
 * <ul>
 *   <li><b>主体是 SKU 而不是商品</b>：同一商品的不同规格各占一行，{@code skuId} 是业务主键的一部分；
 *       无规格商品用空串 {@code ""} 占位，<b>不能用 NULL</b>——MySQL 唯一索引里 NULL 互不相等，
 *       用 NULL 会让"同一商品重复加购"插出多行。</li>
 *   <li><b>按 user_id + store_id 隔离</b>：多店铺下 A 店的购物车绝不能被 B 店看到，
 *       所有查询一律带上这两个条件。</li>
 *   <li><b>不存价格/库存快照</b>：价格与库存在列表接口里实时读取，避免用户结算时才发现价格变了。</li>
 * </ul>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@TableName("ck_shop_cart_item")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "购物车明细")
public class ShopCartItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    /**
     * 用户ID（sys_user.id）
     */
    @Schema(description = "用户ID")
    private String userId;

    /**
     * 用户账号（冗余，便于后台统计直接展示，不必回表 sys_user）
     */
    @Schema(description = "用户账号")
    private String username;

    /**
     * 店铺ID（ck_shop_store.id）
     */
    @Schema(description = "店铺ID")
    private String storeId;

    /**
     * 商品ID
     */
    @Schema(description = "商品ID")
    private String productId;

    /**
     * SKU 组合ID；无规格商品为空串（空串而非 NULL，原因见类注释）
     */
    @Schema(description = "SKU组合ID；无规格商品为空串")
    private String skuId;

    /**
     * 购买数量
     */
    @Schema(description = "购买数量")
    private Integer quantity;

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

    /**
     * 删除标记: 0-正常, 1-已删除
     */
    @TableLogic(value = "0", delval = "1")
    @Schema(description = "删除标记")
    private Integer delFlag;
}
