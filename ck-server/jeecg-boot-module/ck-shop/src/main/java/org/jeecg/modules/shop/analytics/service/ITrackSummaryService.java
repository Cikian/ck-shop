package org.jeecg.modules.shop.analytics.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.analytics.entity.TrackSummary;

/**
 * <p>
 * 用户行为汇总 Service。
 * </p>
 *
 * <p>聚合任务（Quartz 分钟级）把明细聚合进汇总表（hour/day 两粒度、7 维度 × 全局/店铺两种视角），
 * 并把漏斗的加购/下单/支付从业务表回填，保证与订单统计 GMV/实收口径一致。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
public interface ITrackSummaryService extends IService<TrackSummary> {

    /** 聚合最近窗口（小时 + 日两粒度），幂等。由 Quartz 分钟级任务调用。 */
    void aggregateRecent();
}
