package org.jeecg.modules.shop.analytics.job;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.modules.shop.analytics.service.ITrackSummaryService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * <p>
 * 行为聚合任务（Quartz 分钟级）。
 * </p>
 *
 * <p>把 {@code ck_shop_track_event} 聚合进 {@code ck_shop_track_summary}（hour/day 两粒度），
 * 并从业务表回填漏斗（加购/下单/支付）。聚合幂等（覆盖式），单次失败只记日志，
 * 下一轮扫描会重新覆盖同一时间桶，无需补跑。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Slf4j
@DisallowConcurrentExecution
public class TrackAggregationJob implements Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            ITrackSummaryService summaryService = SpringContextUtils.getBean(ITrackSummaryService.class);
            summaryService.aggregateRecent();
        } catch (Exception e) {
            log.error("[analytics] 行为聚合任务执行失败：{}", e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}
