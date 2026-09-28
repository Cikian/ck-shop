package org.jeecg.modules.shop.category.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 分类多语言表
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-08-03
 */

@Data
@TableName("ck_shop_category_translation")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "分类多语言表")
public class ShopCategoryTranslation implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;
    /**
     * 关联分类ID
     */
    @Excel(name = "关联分类ID", width = 15)
    @Schema(description = "关联分类ID")
    private String categoryId;
    /**
     * 语言代码
     */
    @Excel(name = "语言代码", width = 15)
    @Schema(description = "语言代码")
    private String languageCode;
    /**
     * 分类名称
     */
    @Excel(name = "分类名称", width = 15)
    @Schema(description = "分类名称")
    private String name;
    /**
     * 封面url
     */
    @Excel(name = "封面url", width = 15)
    @Schema(description = "封面url")
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
