package org.jeecg.modules.shop.product.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 店铺端规格参数 VO
 * </p>
 *
 * <p>契约 2.2：**不返回 id / languageCode**；只含当前语言最终生效的数据（已按 2.3 回退）。</p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-20
 */
@Data
public class StoreParamVO {

    @Schema(description = "参数名")
    private String paramName;

    @Schema(description = "参数值")
    private String paramValue;

    @Schema(description = "展示顺序（已按 sortOrder 升序）")
    private Integer sortOrder;
}
