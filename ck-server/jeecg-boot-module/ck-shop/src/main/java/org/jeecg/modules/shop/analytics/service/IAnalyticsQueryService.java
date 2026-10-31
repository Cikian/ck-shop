package org.jeecg.modules.shop.analytics.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
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

import java.util.List;

/**
 * <p>
 * 后台行为分析查询 Service（9 个统计接口）。
 * </p>
 *
 * <p>店铺隔离复用 {@code ShopOrgScopeResolver}；{@code shop-analytics:all} 或 admin 角色看全局。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
public interface IAnalyticsQueryService {

    RealtimeOverviewVO realtimeOverview();

    List<TrendVO> trend(AnalyticsQuery query);

    List<RankProductVO> rankProduct(AnalyticsQuery query);

    FunnelVO funnel(AnalyticsQuery query);

    List<RegionVO> region(AnalyticsQuery query);

    List<SourceVO> source(AnalyticsQuery query);

    List<DeviceVO> device(AnalyticsQuery query);

    List<PageVO> page(AnalyticsQuery query);

    IPage<TrackEvent> events(AnalyticsQuery query);
}
