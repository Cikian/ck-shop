package org.jeecg.modules.shop.category.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 店铺端分类树节点
 * </p>
 * 与店铺端商品列表口径一致：分类归属与商品数都按"本店"计算。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "店铺端分类树节点")
public class StoreCategoryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "分类ID")
    private String id;

    @Schema(description = "父级分类ID")
    private String parentId;

    @Schema(description = "层级: 1-一级, 2-二级, 3-三级")
    private Integer level;

    @Schema(description = "分类名称(已按语言解析)")
    private String name;

    @Schema(description = "图标URL")
    private String iconUrl;

    @Schema(description = "封面URL")
    private String coverUrl;

    @Schema(description = "排序")
    private Integer sortOrder;

    /**
     * 本节点**含子节点**的已上架商品数（父分类要能把子分类的商品算进来）
     */
    @Schema(description = "本店已上架商品数(含子分类)")
    private Integer productCount;

    @Schema(description = "子分类")
    private List<StoreCategoryVO> children = new ArrayList<>();
}
