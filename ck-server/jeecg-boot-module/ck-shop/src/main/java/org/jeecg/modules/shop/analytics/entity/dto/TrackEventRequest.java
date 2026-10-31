package org.jeecg.modules.shop.analytics.entity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 采集事件批量上报信封：{@code {"events": [ ... ]}}。
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@Schema(description = "采集事件批量上报请求")
public class TrackEventRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("events")
    @Schema(description = "事件列表")
    private List<TrackEventDTO> events;
}
