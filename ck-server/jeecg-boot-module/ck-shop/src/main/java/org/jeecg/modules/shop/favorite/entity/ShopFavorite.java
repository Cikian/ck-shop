package org.jeecg.modules.shop.favorite.entity;

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
 * 商品收藏（我的收藏）
 * </p>
 *
 * 设计决策（与建表脚本注释一致，这里再强调一次）：
 * <ul>
 *   <li><b>收藏的是商品，不是 SKU</b>：用户在详情页点收藏时很可能还没选规格，
 *       把 SKU 塞进唯一键会逼前端"先选规格才能收藏"，那是最容易被骂的交互；</li>
 *   <li><b>按 user_id + store_id 隔离</b>：多店铺下 A 店的收藏列表绝不能出现 B 店的商品，
 *       所有查询一律带上这两个条件；</li>
 *   <li><b>唯一索引不含 del_flag</b>：取消收藏只置 del_flag=1，旧行仍占着这个键，
 *       再次收藏时必须先查出来复活（{@code selectAnyByProduct} → {@code restoreFavorite}），
 *       否则 INSERT 会直接撞唯一索引报错；</li>
 *   <li><b>不存商品快照</b>：标题/封面/价格/库存/上下架一律实时读取，
 *       商品下架后收藏列表自动少一行，而不是留下一条"价格是三个月前的"僵尸数据。</li>
 * </ul>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@TableName("ck_shop_favorite")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "商品收藏")
public class ShopFavorite implements Serializable {
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
     * 收藏时商品的归属部门（冗余快照，排查"这件商品为什么会出现在这个店铺的收藏里"时用）
     */
    @Schema(description = "收藏时商品归属部门")
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

    /**
     * 删除标记: 0-已收藏, 1-已取消收藏
     */
    @TableLogic(value = "0", delval = "1")
    @Schema(description = "删除标记")
    private Integer delFlag;
}
