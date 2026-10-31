package org.jeecg.modules.shop.analytics.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 实时大盘（/realtime/overview）。金额一律字符串，UV 为近似值。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@Accessors(chain = true)
@Schema(description = "实时大盘")
public class RealtimeOverviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "当前在线人数(秒级, Redis ZSET)")
    private Integer onlineCount;

    @Schema(description = "今日PV")
    private Long todayPv;

    @Schema(description = "今日UV(近似, HyperLogLog)")
    private Long todayUv;

    @Schema(description = "今日IP数(去重)")
    private Long todayIp;

    @Schema(description = "今日访客(会话)数")
    private Long todayVisit;

    @Schema(description = "今日订单数")
    private Long todayOrderCount;

    @Schema(description = "今日实收(字符串金额)")
    private String todayPaidAmount;
}
