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
 * 支付补单任务 —— 四层兜底里的"主动查询 + 定时补单"。
 * </p>
 *
 * <h3>它解决的是最典型的支付事故</h3>
 * 文档事故场景 #1：「用户支付成功，但订单仍待支付：回调丢失，主动查询补单未覆盖」。
 * 渠道回调会丢、会延迟、会在系统重启期间到达失败，所以**不能只依赖回调**。
 * 本任务定期扫描长时间处于 INIT/PENDING 的支付单，主动向渠道查询：
 * 一旦渠道说已成功，立刻补正订单（扣库存、写资金流水、写操作流水）。
 *
 * <h3>失败的处置</h3>
 * 单笔渠道查询失败只记日志继续下一笔——渠道网络抖动是常态，
 * 因为一笔查不通就让整轮任务失败，会让其余几十笔本该补正的单继续滞留。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Slf4j
@DisallowConcurrentExecution
public class ShopPaymentCompensateJob implements Job {

    private static final int BATCH_SIZE = 200;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            IShopOrderTaskService taskService = SpringContextUtils.getBean(IShopOrderTaskService.class);
            IShopOrderTaskService.TaskResult result = taskService.compensatePendingPayments(BATCH_SIZE);
            log.info("【定时任务】支付补单：{}", result);
        } catch (Exception e) {
            log.error("支付补单任务执行失败：{}", e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}
