package org.jeecg.modules.shop.analytics.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 设备分布项（/device）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@Accessors(chain = true)
@Schema(description = "设备分布项")
public class DeviceVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备类型")
    private String deviceType;

    @Schema(description = "操作系统")
    private String os;

    @Schema(description = "浏览器")
    private String browser;

    @Schema(description = "界面语言")
    private String lang;

    @Schema(description = "浏览量")
    private Long pv;

    @Schema(description = "去重访客数")
    private Long uv;
}
