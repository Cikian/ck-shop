package org.jeecg.modules.shop.order.config;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.shop.order.job.ShopOrderTimeoutCloseJob;
import org.jeecg.modules.shop.order.job.ShopPaymentCompensateJob;
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
 * 订单相关定时任务的**编程式注册**。
 * </p>
 *
 * <h3>为什么不用 {@code sys_quartz_job} 表注册</h3>
 * JeecgBoot 平台的任务管理是"往 {@code sys_quartz_job} 插一行 + 到后台点【启动】"。
 * 对交易系统来说这个流程有两个硬伤：
 * <ol>
 *   <li><b>容易被漏掉</b>：脚本执行完没有任何提示，运维不知道还要点一下启动。
 *       超时关单任务不跑，直接后果是库存被未付款订单永久占用；
 *       补单任务不跑，直接后果是掉单永远发现不了。</li>
 *   <li><b>环境不可复现</b>：测试环境点了启动、生产环境忘了点，两边行为不一致。</li>
 * </ol>
 * 因此这里在应用启动时**幂等地注册**这两个任务：Cron 表达式来自配置，
 * 集群互斥由 Quartz 的 JDBC JobStore 保证（同一时刻只有一个节点触发）。
 *
 * <h3>失败不阻断启动</h3>
 * Quartz 调度器初始化异常时只记 ERROR 并继续启动应用。
 * 理由：定时任务失败会让"过期订单不会自动关"这类问题累积，
 * 但不应该让整个商城无法访问——两害相权，前者可控、后者是灾难。
 * 日志与监控负责把这个问题暴露出来。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ck.shop.order.job.enabled", havingValue = "true", matchIfMissing = true)
public class ShopOrderQuartzConfig implements ApplicationRunner {

    private static final String GROUP = "shop_order";

    private final ObjectProvider<Scheduler> schedulerProvider;

    /** 超时关单 cron：默认每分钟扫一次 */
    @Value("${ck.shop.order.job.timeout-close-cron:0 * * * * ?}")
    private String timeoutCloseCron;

    /** 支付补单 cron：默认每分钟扫一次 */
    @Value("${ck.shop.order.job.compensate-cron:30 * * * * ?}")
    private String compensateCron;

    public ShopOrderQuartzConfig(ObjectProvider<Scheduler> schedulerProvider) {
        this.schedulerProvider = schedulerProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        Scheduler scheduler = schedulerProvider.getIfAvailable();
        if (scheduler == null) {
            log.error("未找到 Quartz Scheduler，订单超时关单与支付补单任务**未注册**。"
                    + "请检查 spring.quartz 配置；在此之前请依赖手动补偿接口处理过期订单");
            return;
        }
        register(scheduler, "shopOrderTimeoutCloseJob", ShopOrderTimeoutCloseJob.class,
                timeoutCloseCron, "超时未支付订单自动关闭");
        register(scheduler, "shopPaymentCompensateJob", ShopPaymentCompensateJob.class,
                compensateCron, "支付补单（主动查询渠道）");
    }

    /**
     * 幂等注册一个 Job。
     *
     * <p>{@code storeDurably()} + 先删后建：应用重启时不会因为
     * "JobKey 已存在"而报错，也不会留下新旧两个触发器。
     */
    private void register(Scheduler scheduler, String identity, Class<? extends org.quartz.Job> jobClass,
                          String cron, String description) {
        try {
            JobKey jobKey = JobKey.jobKey(identity, GROUP);
            TriggerKey triggerKey = TriggerKey.triggerKey(identity, GROUP);

            // 先清理旧的触发器与任务（幂等注册的关键）
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
                            // 错过触发时不补跑：补跑一批早已过期的关单没有意义，
                            // 下一轮扫描自然会处理到它们（扫描条件是"已过 pay_deadline"，不是"本轮新增"）
                            .withMisfireHandlingInstructionDoNothing())
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            log.info("已注册订单定时任务：[{}] {}，cron={}", identity, description, cron);
        } catch (Exception e) {
            // 不阻断启动：定时任务失效是可控问题，整站无法访问是灾难
            log.error("注册订单定时任务失败：[{}] {}，cron={}，err={}。"
                            + "请检查 Quartz 配置，或改用后台手动补偿接口",
                    identity, description, cron, e.getMessage(), e);
        }
    }
}
