package org.jeecg.modules.shop.analytics.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 地域分布项（/region）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@Accessors(chain = true)
@Schema(description = "地域分布项")
public class RegionVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "国家码")
    private String countryCode;

    @Schema(description = "国名")
    private String countryName;

    @Schema(description = "省/州名")
    private String provinceName;

    @Schema(description = "城市名")
    private String cityName;

    @Schema(description = "浏览量")
    private Long pv;

    @Schema(description = "去重访客数")
    private Long uv;

    @Schema(description = "去重IP数")
    private Long ip;
}
