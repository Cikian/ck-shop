package org.jeecg.modules.shop.analytics.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 后台行为分析查询条件（9 个统计接口共用，按需取子集）。
 * </p>
 *
 * <p>入参字段与 {@code admin-client/src/api/ck/shopAnalytics.ts} 对齐：统一 camelCase
 * （storeId / startTime / endTime / granularity / dim / limit / level / pageNo / pageSize /
 * eventName / productId / keyword）。时间用 String 而非 Date，避免 Spring 未注册
 * String→Date 格式化器导致"带时间筛选的请求 400"。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Data
@Schema(description = "行为分析查询条件")
public class AnalyticsQuery implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "店铺ID; 不传=当前登录人可见域")
    private String storeId;

    @Schema(description = "开始时间 yyyy-MM-dd HH:mm:ss")
    private String startTime;

    @Schema(description = "结束时间 yyyy-MM-dd HH:mm:ss")
    private String endTime;

    @Schema(description = "统计粒度: hour/day, 默认 day")
    private String granularity;

    @Schema(description = "维度: global/store/product/region/source/device/page, 默认 global")
    private String dim;

    @Schema(description = "排行条数, 默认 10, 上限 100")
    private Integer limit;

    @Schema(description = "地域下钻级别: country/province/city, 默认 country")
    private String level;

    @Schema(description = "页码, 从 1 开始")
    private Integer pageNo = 1;

    @Schema(description = "每页条数")
    private Integer pageSize = 10;

    @Schema(description = "事件名(受控枚举)")
    private String eventName;

    @Schema(description = "商品ID")
    private String productId;

    @Schema(description = "关键字: visitor_id/user_id/session_id/页面路径模糊匹配")
    private String keyword;
}
