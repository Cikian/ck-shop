package org.jeecg.modules.ck.sys.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.poi.excel.annotation.Excel;

import java.io.Serializable;

/**
 * <p>
 * 系统语言支持
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-07-20
 */

@Data
@TableName("ck_sys_language")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "系统语言支持")
public class SysLanguage implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;
    /**
     * 语言编码
     */
    @Excel(name = "语言编码", width = 15)
    @Schema(description = "语言编码")
    private String code;
    /**
     * 语言代码
     */
    @Excel(name = "语言代码", width = 15)
    @Schema(description = "语言代码")
    private String language;
    /**
     * 语言名称
     */
    @Excel(name = "语言名称", width = 15)
    @Schema(description = "语言名称")
    private String name;
    /**
     * 启用
     */
    @Excel(name = "启用", width = 15, replace = {"是_Y", "否_N"})
    @Schema(description = "启用")
    private String enabled;
    /**
     * 启用
     */
    @Excel(name = "排序", width = 15)
    @Schema(description = "排序")
    private Integer sort;
}
