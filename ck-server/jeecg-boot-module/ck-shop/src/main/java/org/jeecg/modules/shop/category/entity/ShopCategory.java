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
 * 商品分类
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-08-03
 */

@Data
@TableName("ck_shop_category")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "商品分类")
public class ShopCategory implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;
    /**
     * 父级分类ID, 0表示顶级分类
     */
    @Excel(name = "父级分类ID, 0表示顶级分类", width = 15)
    @Schema(description = "父级分类ID, 0表示顶级分类")
    private String parentId;
    /**
     * 分类层级: 1-一级, 2-二级, 3-三级
     */
    @Excel(name = "分类层级: 1-一级, 2-二级, 3-三级", width = 15)
    @Schema(description = "分类层级: 1-一级, 2-二级, 3-三级")
    private Integer level;
    /**
     * 排序
     */
    @Excel(name = "排序", width = 15)
    @Schema(description = "排序")
    private Integer sortOrder;
    /**
     * 分类图标URL
     */
    @Excel(name = "分类图标URL", width = 15)
    @Schema(description = "分类图标URL")
    private String iconUrl;
    /**
     * 内部名称
     */
    @Excel(name = "内部名称", width = 15)
    @Schema(description = "内部名称")
    private String name;
    /**
     * 封面URL
     */
    @Excel(name = "封面URL", width = 15)
    @Schema(description = "封面URL")
    private String coverUrl;
    /**
     * 是否显示: N-隐藏, Y-显示
     */
    @Excel(name = "是否显示: N-隐藏, Y-显示", width = 15, replace = {"是_Y", "否_N"})
    @Schema(description = "是否显示: N-隐藏, Y-显示")
    private String isShow;
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
