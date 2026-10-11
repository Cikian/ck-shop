package org.jeecg.modules.shop.product.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 店铺端规格值 VO
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@Schema(description = "店铺端规格值")
public class StoreSpecValueVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "规格值ID")
    private String id;

    @Schema(description = "规格值")
    private String value;

    @Schema(description = "展示顺序")
    private Integer sortOrder;
}
