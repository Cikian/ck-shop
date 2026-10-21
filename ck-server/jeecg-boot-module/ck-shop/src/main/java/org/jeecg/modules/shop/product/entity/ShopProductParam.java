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
 * 商品规格参数（Key-Value，展示用，按 商品 + 语言 分行）
 * </p>
 *
 * <p>契约见 docs/商品详情-设计契约.md 第 1.2 节。</p>
 *
 * <p>与 {@link ShopProductSpec} 的区别：Spec 是<b>销售维度</b>（决定 SKU 组合与价格），
 * 本表是<b>展示维度</b>（客户只看不选）。两者语义、编辑入口、生命周期都不同，刻意不复用。</p>
 *
 * <p>没有 del_flag：同 {@link ShopProductDetailBlock}，内容型数据，<b>物理删除</b>。</p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-20
 */
@Data
@TableName("ck_shop_product_param")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "商品规格参数(展示用)")
public class ShopProductParam implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    /**
     * 商品ID（ck_shop_product.id）
     */
    @Schema(description = "商品ID")
    private String productId;

    /**
     * 语言代码
     */
    @Schema(description = "语言代码")
    private String languageCode;

    /**
     * 参数名（如「屏幕尺寸」）
     */
    @Schema(description = "参数名")
    private String paramName;

    /**
     * 参数值（如「6.7 英寸」）
     */
    @Schema(description = "参数值")
    private String paramValue;

    /**
     * 展示顺序（升序）
     */
    @Schema(description = "展示顺序")
    private Integer sortOrder;

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
