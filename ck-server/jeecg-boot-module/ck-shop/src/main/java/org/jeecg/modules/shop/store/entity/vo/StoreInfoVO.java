package org.jeecg.modules.shop.store.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 店铺端公开信息（供前台 Header / Footer / SEO 读取品牌与主题）
 * </p>
 *
 * <p><b>只允许放"买家本来就看得见"的字段</b>：店铺编码、名称、Logo、主题配置。
 * 绝不能像 {@link StoreOptionVO} 那样顺手把 departId / domain / createBy 带出去——
 * 这是匿名接口（{@code @IgnoreAuth}），任何访问者都能拿到。
 *
 * @author Cikian
 * @since 2026-10-22
 */
@Data
@Accessors(chain = true)
@Schema(description = "店铺端公开信息")
public class StoreInfoVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "店铺编码")
    private String code;

    @Schema(description = "店铺名称（前台品牌名）")
    private String name;

    @Schema(description = "店铺Logo")
    private String logoUrl;

    @Schema(description = "主题配置JSON（前台配色/样式）")
    private String themeConfig;
}
