package org.jeecg.modules.shop.product.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * <p>
 * 商品详情模块 VO（管理端编辑页回填，含 id 与 languageCode）
 * </p>
 *
 * <p>契约 2.1：返回商品**全部语言**的模块；{@code content} 是 JSON 对象。</p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-20
 */
@Data
public class DetailBlockVO {

    @Schema(description = "主键")
    private String id;

    @Schema(description = "语言代码")
    private String languageCode;

    @Schema(description = "模块类型")
    private String blockType;

    @Schema(description = "模块内容（JSON 对象，结构见契约 1.3）")
    private Map<String, Object> content;

    @Schema(description = "同语言内展示顺序")
    private Integer sortOrder;

    /*
     * 模块四方向间距（契约 1.4 / 2.1）：管理端编辑页回填用；null 归一为 0，越界按范围夹取。
     */
    @Schema(description = "上间距(px)")
    private Integer marginTop;

    @Schema(description = "右间距(px)")
    private Integer marginRight;

    @Schema(description = "下间距(px)")
    private Integer marginBottom;

    @Schema(description = "左间距(px)")
    private Integer marginLeft;
}
