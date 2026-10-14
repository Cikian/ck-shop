package org.jeecg.modules.shop.order.job;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.modules.shop.order.service.IShopOrderTaskService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * <p>
 * 超时未支付订单自动关闭任务。
 * </p>
 *
 * <h3>为什么用 {@code SpringContextUtils} 取 Bean 而不是 {@code @Autowired}</h3>
 * JeecgBoot 的 {@code QuartzJobServiceImpl} 用
 * {@code clazz.getDeclaredConstructor().newInstance()} 反射创建 Job 实例，
 * 这样创建的实例**不受 Spring 容器管理**，字段注入全是 null。
 * 通过上下文工具类取 Bean 是唯一在两种调度方式（平台后台手动启动的 Job / 本模块
 * 编程式注册的 Job）下都可靠的做法。
 *
 * <h3>为什么加 {@code @DisallowConcurrentExecution}</h3>
 * 上一轮任务还没跑完时，Quartz 默认会再起一个实例并发执行。
 * 虽然本模块的所有变更都做了 CAS 与幂等（重复执行不会出错），
 * 但两个实例同时扫同一批订单会做无用的重复查询与渠道调用。
 * 加上这个注解后，同一时刻只有一个实例在执行本任务。
 * 跨节点（集群）层面的互斥由 Quartz 的 JDBC JobStore 保证。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Slf4j
@DisallowConcurrentExecution
public class ShopOrderTimeoutCloseJob implements Job {

    /** 单次处理批量：值与 {@code ShopOrderTaskServiceImpl#normalizeBatch} 的上限配合 */
    private static final int BATCH_SIZE = 200;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            IShopOrderTaskService taskService = SpringContextUtils.getBean(IShopOrderTaskService.class);
            IShopOrderTaskService.TaskResult result = taskService.closeTimeoutOrders(BATCH_SIZE);
            log.info("【定时任务】超时订单关闭：{}", result);
        } catch (Exception e) {
            // 抛 JobExecutionException 会让 Quartz 记录本次执行失败（可被平台后台看到），
            // 这正是我们想要的：任务失败必须可见，而不是被 catch 掉静默
            log.error("超时订单关闭任务执行失败：{}", e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}
