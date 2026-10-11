package org.jeecg.modules.shop.product.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 标签简要信息（不区分语言，用于列表与详情展示）
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@Schema(description = "标签简要信息")
public class TagRefVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "标签ID")
    private String id;

    @Schema(description = "标签编码")
    private String code;

    @Schema(description = "标签名称（按当前语言）")
    private String name;
}
