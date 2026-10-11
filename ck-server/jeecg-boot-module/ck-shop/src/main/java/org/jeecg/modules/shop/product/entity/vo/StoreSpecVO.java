package org.jeecg.modules.shop.product.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 店铺端规格 VO（规格 + 可选取值）
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@Schema(description = "店铺端规格")
public class StoreSpecVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "规格ID")
    private String id;

    @Schema(description = "规格名，如 颜色")
    private String name;

    @Schema(description = "规格展示顺序")
    private Integer sortOrder;

    @Schema(description = "是否必选: N-否, Y-是")
    private String isRequired;

    @Schema(description = "可选取值（按 sortOrder 升序）")
    private List<StoreSpecValueVO> values;
}
