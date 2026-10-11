package org.jeecg.modules.shop.brand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
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
 * 商品品牌
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@TableName("ck_shop_brand")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "商品品牌")
public class ShopBrand implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;
    /**
     * 品牌编码
     */
    @Excel(name = "品牌编码", width = 20)
    @Schema(description = "品牌编码")
    private String code;
    /**
     * 品牌LOGO URL
     */
    @Excel(name = "品牌LOGO", width = 30)
    @Schema(description = "品牌LOGO URL")
    private String logoUrl;
    /**
     * 排序
     */
    @Excel(name = "排序", width = 15)
    @Schema(description = "排序")
    private Integer sortOrder;
    /**
     * 是否启用: N-禁用, Y-启用
     */
    @Excel(name = "是否启用", width = 15, replace = {"是_Y", "否_N"})
    @Schema(description = "是否启用: N-禁用, Y-启用")
    private String isShow;
    /**
     * 删除标记: 0-正常, 1-已删除
     */
    @TableLogic(value = "0", delval = "1")
    @Schema(description = "删除标记")
    private Integer delFlag;

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
