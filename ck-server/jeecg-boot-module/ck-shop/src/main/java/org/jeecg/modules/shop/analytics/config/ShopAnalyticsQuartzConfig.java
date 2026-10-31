package org.jeecg.modules.shop.analytics.config;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.shop.analytics.job.TrackAggregationJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 行为聚合任务的编程式注册（Quartz JDBC JobStore 保证集群互斥）。
 * </p>
 *
 * <p>刻意不用 {@code sys_quartz_job} 表注册（同订单任务）：避免"脚本执行完还要去后台点启动"
 * 这种容易被漏掉的环节；应用启动时幂等注册，失败不阻断启动。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ck.shop.analytics.job.enabled", havingValue = "true", matchIfMissing = true)
public class ShopAnalyticsQuartzConfig implements ApplicationRunner {

    private static final String GROUP = "shop_analytics";

    private final ObjectProvider<Scheduler> schedulerProvider;

    /** 聚合 cron：默认每分钟第 0 秒 */
    @Value("${ck.shop.analytics.job.aggregation-cron:0 * * * * ?}")
    private String aggregationCron;

    public ShopAnalyticsQuartzConfig(ObjectProvider<Scheduler> schedulerProvider) {
        this.schedulerProvider = schedulerProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        Scheduler scheduler = schedulerProvider.getIfAvailable();
        if (scheduler == null) {
            log.error("[analytics] 未找到 Quartz Scheduler，行为聚合任务未注册。请检查 spring.quartz 配置");
            return;
        }
        register(scheduler, "trackAggregationJob", TrackAggregationJob.class,
                aggregationCron, "用户行为小时/日聚合 + 漏斗回填");
    }

    private void register(Scheduler scheduler, String identity, Class<? extends org.quartz.Job> jobClass,
                          String cron, String description) {
        try {
            JobKey jobKey = JobKey.jobKey(identity, GROUP);
            TriggerKey triggerKey = TriggerKey.triggerKey(identity, GROUP);
            if (scheduler.checkExists(triggerKey)) {
                scheduler.unscheduleJob(triggerKey);
            }
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
            JobDetail jobDetail = JobBuilder.newJob(jobClass)
                    .withIdentity(jobKey)
                    .withDescription(description)
                    .storeDurably()
                    .build();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobDetail)
                    .withDescription(description)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                            .withMisfireHandlingInstructionDoNothing())
                    .build();
            scheduler.scheduleJob(jobDetail, trigger);
            log.info("[analytics] 已注册行为聚合任务：[{}] {}，cron={}", identity, description, cron);
        } catch (Exception e) {
            log.error("[analytics] 注册行为聚合任务失败：[{}] {}，cron={}，err={}",
                    identity, description, cron, e.getMessage(), e);
        }
    }
}
