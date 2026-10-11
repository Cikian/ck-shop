package org.jeecg.modules.shop.brand.entity;

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
 * 品牌多语言表
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@TableName("ck_shop_brand_translation")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "品牌多语言表")
public class ShopBrandTranslation implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;
    /**
     * 关联品牌ID
     */
    @Schema(description = "关联品牌ID")
    private String brandId;
    /**
     * 语言代码
     */
    @Schema(description = "语言代码")
    private String languageCode;
    /**
     * 品牌名称
     */
    @Schema(description = "品牌名称")
    private String name;
    /**
     * 品牌描述
     */
    @Schema(description = "品牌描述")
    private String description;

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
