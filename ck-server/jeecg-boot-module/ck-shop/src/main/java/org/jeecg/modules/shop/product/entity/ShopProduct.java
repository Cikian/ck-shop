package org.jeecg.modules.shop.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * <p>
 * 商品 SPU 主表
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@TableName("ck_shop_product")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "商品")
public class ShopProduct implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;
    /**
     * 商品SPU编码
     */
    @Excel(name = "商品SPU编码", width = 25)
    @Schema(description = "商品SPU编码")
    private String spuCode;
    /**
     * 基准价格
     */
    @Excel(name = "基准价格", width = 15)
    @Schema(description = "基准价格")
    private BigDecimal price;
    /**
     * 最低售价(SKU汇总)
     */
    @Excel(name = "最低售价", width = 15)
    @Schema(description = "最低售价(SKU汇总)")
    private BigDecimal minPrice;
    /**
     * 最高售价(SKU汇总)
     */
    @Excel(name = "最高售价", width = 15)
    @Schema(description = "最高售价(SKU汇总)")
    private BigDecimal maxPrice;
    /**
     * 总库存
     */
    @Excel(name = "总库存", width = 15)
    @Schema(description = "总库存")
    private Integer stock;
    /**
     * 单位
     */
    @Excel(name = "单位", width = 15)
    @Schema(description = "单位")
    private String unit;
    /**
     * 状态: N-下架, Y-上架
     */
    @Excel(name = "状态", width = 15, replace = {"上架_Y", "下架_N"})
    @Schema(description = "状态: N-下架, Y-上架")
    private String status;
    /**
     * 排序
     */
    @Excel(name = "排序", width = 15)
    @Schema(description = "排序")
    private Integer sortOrder;
    /**
     * 是否推荐: N-否, Y-是
     */
    @Excel(name = "是否推荐", width = 15, replace = {"是_Y", "否_N"})
    @Schema(description = "是否推荐: N-否, Y-是")
    private String isRecommend;
    /**
     * 分类ID
     */
    @Excel(name = "分类ID", width = 25)
    @Schema(description = "分类ID")
    private String categoryId;
    /**
     * 品牌ID
     */
    @Excel(name = "品牌ID", width = 25)
    @Schema(description = "品牌ID")
    private String brandId;
    /**
     * 上游来源：取自系统字典 shop_upstream_source（暂定：淘宝/京东/1688/闲鱼）
     */
    @Schema(description = "上游来源(字典 shop_upstream_source)")
    @Dict(dicCode = "shop_upstream_source")
    private String upstreamSource;
    /**
     * 上游链接：手动录入的商品来源地址，详情页可直接点击跳转
     */
    @Schema(description = "上游链接")
    private String upstreamUrl;
    /**
     * 主图URL
     */
    @Excel(name = "主图URL", width = 30)
    @Schema(description = "主图URL")
    private String mainImage;
    /**
     * 创建人
     */
    @Schema(description = "创建人")
    private String createBy;
    /**
     * 创建日期
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建日期")
    private Date createTime;
    /**
     * 更新人
     */
    @Schema(description = "更新人")
    private String updateBy;
    /**
     * 更新日期
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新日期")
    private Date updateTime;
    /**
     * 删除标记: 0-正常, 1-已删除（软删除）
     */
    @TableLogic(value = "0", delval = "1")
    @Schema(description = "删除标记: 0-正常, 1-已删除")
    private Integer delFlag;
}
