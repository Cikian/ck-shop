package org.jeecg.modules.shop.product.entity;

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
 * 商品多语言表
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@TableName("ck_shop_product_translation")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "商品多语言表")
public class ShopProductTranslation implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;
    /**
     * 关联商品ID
     */
    @Schema(description = "关联商品ID")
    private String productId;
    /**
     * 语言代码
     */
    @Schema(description = "语言代码")
    private String languageCode;
    /**
     * 商品名称
     */
    @Schema(description = "商品名称")
    private String title;
    /**
     * 副标题/卖点描述
     */
    @Schema(description = "副标题/卖点描述")
    private String subTitle;
    /**
     * 商品详细描述 (富文本/HTML)
     */
    @Schema(description = "商品详细描述")
    private String description;
    /**
     * SEO 标题
     */
    @Schema(description = "SEO 标题")
    private String seoTitle;
    /**
     * SEO 关键词
     */
    @Schema(description = "SEO 关键词")
    private String seoKeywords;
    /**
     * SEO 描述
     */
    @Schema(description = "SEO 描述")
    private String seoDescription;
    /**
     * 封面URL
     */
    @Schema(description = "封面URL")
    private String coverUrl;
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
}
