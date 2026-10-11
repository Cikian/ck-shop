package org.jeecg.modules.shop.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * <p>
 * 商品-店铺上架关系（渠道属性）
 * </p>
 * 商品主体（SPU/规格/SKU/图集/多语言）全局共享一份；
 * "在哪个店卖、归哪个分类、卖多少钱"都是**渠道**的属性，放这里。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@TableName("ck_shop_product_store")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "商品-店铺上架关系")
public class ShopProductStore implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "店铺ID")
    private String storeId;

    /**
     * 本店分类：为空时回退到商品的主分类（ck_shop_product.category_id）
     */
    @Schema(description = "本店分类(空则回退商品主分类)")
    private String categoryId;

    /**
     * 本店售价：为空时回退到商品基准价。作用于"没有单独定价的 SKU"，与现有价格回退逻辑一致。
     */
    @Schema(description = "本店售价(空则回退商品基准价)")
    private BigDecimal priceOverride;

    @Schema(description = "本店是否上架: N-下架, Y-上架")
    private String status;

    @Schema(description = "本店排序")
    private Integer sortOrder;

    @Schema(description = "本店是否推荐")
    private String isRecommend;

    @TableLogic
    @Schema(description = "删除标记: 0-正常, 1-已删除")
    private Integer delFlag;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建日期")
    private Date createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新日期")
    private Date updateTime;
}
