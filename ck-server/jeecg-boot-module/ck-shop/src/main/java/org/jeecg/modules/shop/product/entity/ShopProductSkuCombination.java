package org.jeecg.modules.shop.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableField;
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
import java.util.List;

/**
 * <p>
 * 商品 SKU 规格组合：笛卡尔积的每一行，库存与价格的唯一载体
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@TableName("ck_shop_product_sku_combination")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "商品SKU规格组合")
public class ShopProductSkuCombination implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;
    /**
     * 商品ID
     */
    @Schema(description = "商品ID")
    private String productId;
    /**
     * SKU编码
     */
    @Schema(description = "SKU编码")
    private String skuCode;
    /**
     * 规格值ID有序拼接，唯一标识一个组合
     */
    @Schema(description = "组合唯一键")
    private String combinationKey;
    /**
     * 组合文案快照，如 白色/8G/256G
     */
    @Schema(description = "组合文案")
    private String combinationName;
    /**
     * SKU售价，为空回退商品基准价
     */
    @Schema(description = "SKU售价")
    private BigDecimal price;
    /**
     * SKU库存
     */
    @Schema(description = "SKU库存")
    private Integer stock;
    /**
     * SKU配图URL
     */
    @Schema(description = "SKU配图URL")
    private String imageUrl;
    /**
     * 是否可售: N-不可售(未生产), Y-可售
     */
    @Schema(description = "是否可售: N-不可售, Y-可售")
    private String saleable;
    /**
     * 删除标记: 0-正常, 1-已删除
     */
    @TableLogic(value = "0", delval = "1")
    @Schema(description = "删除标记")
    private Integer delFlag;

    /**
     * 该组合包含的规格值ID，由 XML 中的 collection 映射装配，非数据库字段
     */
    @TableField(exist = false)
    @Schema(description = "该组合包含的规格值ID")
    private List<String> specValueIds;

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
}
