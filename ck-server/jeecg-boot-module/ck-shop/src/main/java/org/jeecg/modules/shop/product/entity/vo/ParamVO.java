package org.jeecg.modules.shop.product.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 商品规格参数 VO（管理端编辑页回填，含 id 与 languageCode）
 * </p>
 *
 * <p>契约 2.1：返回商品**全部语言**的参数。</p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-20
 */
@Data
public class ParamVO {

    @Schema(description = "主键")
    private String id;

    @Schema(description = "语言代码")
    private String languageCode;

    @Schema(description = "参数名")
    private String paramName;

    @Schema(description = "参数值")
    private String paramValue;

    @Schema(description = "展示顺序")
    private Integer sortOrder;
}
