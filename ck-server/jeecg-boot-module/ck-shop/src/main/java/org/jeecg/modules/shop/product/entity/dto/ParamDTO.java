package org.jeecg.modules.shop.product.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 商品规格参数 DTO（管理端提交/回填）
 * </p>
 *
 * <p>契约 2.1：{@code paramName} / {@code paramValue} 均必填，长度上限 100 / 500。</p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-20
 */
@Data
public class ParamDTO {

    @Schema(description = "主键（可选，回填时带上；后端整体替换时不依赖它）")
    private String id;

    @Schema(description = "语言代码: zh/en/fr...")
    private String languageCode;

    @Schema(description = "参数名，如「屏幕尺寸」")
    private String paramName;

    @Schema(description = "参数值，如「6.7 英寸」")
    private String paramValue;

    @Schema(description = "展示顺序（后端按数组下标在同语言内重排，提交值仅作参考）")
    private Integer sortOrder;
}
