package org.jeecg.modules.shop.order.constant;

/**
 * <p>
 * 操作者类型（操作流水里的 {@code operator_type}）。
 * </p>
 *
 * <b>刻意区分 SYSTEM / CHANNEL / ADMIN / USER</b>：
 * 出事故时第一个要回答的问题是"这个动作是谁干的"。
 * 把渠道回调和后台人工操作混为一谈，就永远查不清"这笔单到底是系统自动补的还是人工补的"，
 * 也就无法追责、无法判断是否需要人工复核。
 *
 * @author Cikian
 * @since 2026-10-14
 */
public final class OperatorType {

    private OperatorType() {
    }

    /** 买家本人 */
    public static final String USER = "USER";
    /** 后台管理员 */
    public static final String ADMIN = "ADMIN";
    /** 系统自动（定时任务、超时关单） */
    public static final String SYSTEM = "SYSTEM";
    /** 支付渠道（异步回调） */
    public static final String CHANNEL = "CHANNEL";
}
