package org.jeecg.modules.shop.analytics.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.shop.analytics.entity.TrackEvent;
import org.jeecg.modules.shop.analytics.entity.dto.AnalyticsQuery;
import org.jeecg.modules.shop.analytics.entity.vo.DeviceVO;
import org.jeecg.modules.shop.analytics.entity.vo.FunnelVO;
import org.jeecg.modules.shop.analytics.entity.vo.PageVO;
import org.jeecg.modules.shop.analytics.entity.vo.RankProductVO;
import org.jeecg.modules.shop.analytics.entity.vo.RealtimeOverviewVO;
import org.jeecg.modules.shop.analytics.entity.vo.RegionVO;
import org.jeecg.modules.shop.analytics.entity.vo.SourceVO;
import org.jeecg.modules.shop.analytics.entity.vo.TrendVO;
import org.jeecg.modules.shop.analytics.service.IAnalyticsQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 后台-行为分析统计接口（9 个，契约 §6.1）。
 * </p>
 *
 * <p>返回字段严格按契约 §6.4；金额字符串、时间 {@code yyyy-MM-dd HH:mm:ss}、camelCase；
 * 比率字段为字符串"百分数数值、不带 %、保留 2 位"。店铺隔离复用 {@code ShopOrgScopeResolver}，
 * {@code shop-analytics:all} 看全局。每个接口独立暴露，大屏按需刷新（总览 30s、排行 5min、明细按需）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Tag(name = "后台-行为分析统计")
@RestController
@RequestMapping("/shop/analytics/admin")
public class ShopAnalyticsAdminController {

    @Autowired
    private IAnalyticsQueryService analyticsQueryService;

    @Operation(summary = "实时大盘(在线/PV/UV/今日核心指标)")
    @RequiresPermissions("shop-analytics:view")
    @GetMapping(value = "/realtime/overview")
    public Result<RealtimeOverviewVO> realtimeOverview() {
        return Result.OK(analyticsQueryService.realtimeOverview());
    }

    @Operation(summary = "趋势(granularity=hour/day, 按dim)")
    @RequiresPermissions("shop-analytics:view")
    @GetMapping(value = "/trend")
    public Result<List<TrendVO>> trend(AnalyticsQuery query) {
        return Result.OK(analyticsQueryService.trend(query));
    }

    @Operation(summary = "商品排行(访问/加购/转化率/停留)")
    @RequiresPermissions("shop-analytics:view")
    @GetMapping(value = "/rank/product")
    public Result<List<RankProductVO>> rankProduct(AnalyticsQuery query) {
        return Result.OK(analyticsQueryService.rankProduct(query));
    }

    @Operation(summary = "转化漏斗(访问→加购→下单→支付)")
    @RequiresPermissions("shop-analytics:view")
    @GetMapping(value = "/funnel")
    public Result<FunnelVO> funnel(AnalyticsQuery query) {
        return Result.OK(analyticsQueryService.funnel(query));
    }

    @Operation(summary = "地域分布(level=country/province/city)")
    @RequiresPermissions("shop-analytics:view")
    @GetMapping(value = "/region")
    public Result<List<RegionVO>> region(AnalyticsQuery query) {
        return Result.OK(analyticsQueryService.region(query));
    }

    @Operation(summary = "来源分布(direct/search/referral/utm)")
    @RequiresPermissions("shop-analytics:view")
    @GetMapping(value = "/source")
    public Result<List<SourceVO>> source(AnalyticsQuery query) {
        return Result.OK(analyticsQueryService.source(query));
    }

    @Operation(summary = "设备/浏览器/语言分布")
    @RequiresPermissions("shop-analytics:view")
    @GetMapping(value = "/device")
    public Result<List<DeviceVO>> device(AnalyticsQuery query) {
        return Result.OK(analyticsQueryService.device(query));
    }

    @Operation(summary = "页面排行/平均停留/跳出率")
    @RequiresPermissions("shop-analytics:view")
    @GetMapping(value = "/page")
    public Result<List<PageVO>> page(AnalyticsQuery query) {
        return Result.OK(analyticsQueryService.page(query));
    }

    @Operation(summary = "行为明细分页查询(导出用)")
    @RequiresPermissions("shop-analytics:view")
    @GetMapping(value = "/events")
    public Result<IPage<TrackEvent>> events(AnalyticsQuery query) {
        return Result.OK(analyticsQueryService.events(query));
    }
}
