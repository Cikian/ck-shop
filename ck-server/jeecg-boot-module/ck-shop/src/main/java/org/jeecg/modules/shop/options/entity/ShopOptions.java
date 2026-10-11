package org.jeecg.modules.shop.options.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 规格预设字典：为商品编辑页"从预设库导入规格"提供数据源
 * 约定 option_code 为规格名（如 color / memory / storage），
 * option_value 为可选值，多个值以英文逗号分隔（如 白色,红色,黑色）
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@TableName("ck_shop_options")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "规格预设字典")
public class ShopOptions implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Schema(description = "选项编码（规格名）")
    private String optionCode;

    @Schema(description = "选项值，多个以英文逗号分隔")
    private String optionValue;

    @Schema(description = "选项名称（规格中文名，如 颜色）")
    private String optionName;

    @Schema(description = "选项描述")
    private String description;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "是否启用: N-禁用, Y-启用")
    private String isShow;

    @TableLogic(value = "0", delval = "1")
    @Schema(description = "删除标记")
    private Integer delFlag;
}
