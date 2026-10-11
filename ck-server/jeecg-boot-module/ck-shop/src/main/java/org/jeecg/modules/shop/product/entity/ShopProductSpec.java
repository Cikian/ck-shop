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
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 商品规格定义（颜色 / 内存 / 存储）
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@TableName("ck_shop_product_spec")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "商品规格定义")
public class ShopProductSpec implements Serializable {
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
     * 规格名，如 颜色 / 内存 / 存储
     */
    @Schema(description = "规格名")
    private String name;
    /**
     * 来源预设编码(ck_shop_options.option_code)，手工填写为空
     */
    @Schema(description = "来源预设编码")
    private String presetCode;
    /**
     * 规格展示顺序
     */
    @Schema(description = "规格展示顺序")
    private Integer sortOrder;
    /**
     * 是否必选: N-否, Y-是
     */
    @Schema(description = "是否必选: N-否, Y-是")
    private String isRequired;
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
