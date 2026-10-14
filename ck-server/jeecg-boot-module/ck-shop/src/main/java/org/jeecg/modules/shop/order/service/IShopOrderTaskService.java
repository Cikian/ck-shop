package org.jeecg.modules.shop.order.service;

import java.io.Serializable;

/**
 * <p>
 * 订单补偿任务 Service —— 四层兜底的第三、四层（定时补单 + 手动补偿）。
 * </p>
 *
 * <h3>为什么每次执行都要返回统计</h3>
 * "跑了个定时任务"这句话没有信息量。真正需要知道的是：
 * 扫到了多少、成功多少、失败多少、为什么失败。
 * 这些数字进入日志与监控，"掉单率""关单失败率"才可能被发现——
 * 文档 §6 明确提到"监控告警不足：支付成功率、掉单率……无告警"。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
public interface IShopOrderTaskService {

    /**
     * 关闭超时未支付的订单。
     *
     * <p>关单前会先向渠道确认支付结果（主动查询），
     * 渠道说已成功就**转为支付成功**而不是关单。
     */
    TaskResult closeTimeoutOrders(int batchSize);

    /**
     * 补单：对长时间处于 INIT/PENDING 的支付单主动查询渠道并据此结算。
     *
     * <p>这是发现"钱已经收了但系统不知道"的唯一自动化手段。
     */
    TaskResult compensatePendingPayments(int batchSize);

    /** 手动补偿某一笔订单（运维在后台按订单号触发） */
    TaskResult compensateOrder(String orderNo);

    /** 手动补偿某一笔支付单 */
    TaskResult compensatePayment(String paymentNo);

    /**
     * 任务执行结果。
     */
    class TaskResult implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 扫描到的条数 */
        private int scanned;
        /** 实际处理成功条数 */
        private int succeeded;
        /** 处理失败条数 */
        private int failed;
        /** 被跳过的条数（状态已变化、渠道结果未知等，属于正常竞态） */
        private int skipped;
        /** 耗时（毫秒） */
        private long costMillis;
        /** 补充说明 */
        private String message;

        public static TaskResult empty(String message) {
            return new TaskResult().setMessage(message);
        }

        public TaskResult scanned(int value) {
            this.scanned = value;
            return this;
        }

        public TaskResult succeeded(int value) {
            this.succeeded = value;
            return this;
        }

        public TaskResult failed(int value) {
            this.failed = value;
            return this;
        }

        public TaskResult skipped(int value) {
            this.skipped = value;
            return this;
        }

        public TaskResult costMillis(long value) {
            this.costMillis = value;
            return this;
        }

        public TaskResult setMessage(String message) {
            this.message = message;
            return this;
        }

        public int getScanned() {
            return scanned;
        }

        public int getSucceeded() {
            return succeeded;
        }

        public int getFailed() {
            return failed;
        }

        public int getSkipped() {
            return skipped;
        }

        public long getCostMillis() {
            return costMillis;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return String.format("扫描 %d 条，成功 %d，失败 %d，跳过 %d，耗时 %d ms%s",
                    scanned, succeeded, failed, skipped, costMillis,
                    message == null ? "" : "（" + message + "）");
        }
    }
}
