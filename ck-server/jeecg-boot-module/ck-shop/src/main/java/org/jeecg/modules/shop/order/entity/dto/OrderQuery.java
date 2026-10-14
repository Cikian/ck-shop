package org.jeecg.modules.shop.order.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 后台订单列表查询条件。
 * </p>
 *
 * <h3>为什么"归档订单"要显式勾选才查</h3>
 * 默认 {@code delFlag = 0}：归档订单不进入日常列表（否则列表被历史数据淹没）。
 * 但**必须提供 {@code includeArchived} 开关**：财务年底查一张三年前的订单时
 * 如果查不到，第一反应是"系统把数据弄丢了"，这会引发大量无谓的排查。
 * 能查到、且清楚地标注"该订单已归档"，才是正确的产品行为。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Schema(description = "后台订单查询条件")
public class OrderQuery implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "页码(从1开始)")
    private Integer pageNo = 1;

    @Schema(description = "每页条数")
    private Integer pageSize = 10;

    @Schema(description = "订单号(精确)")
    private String orderNo;

    /** 模糊搜索：订单号 / 用户账号 / 收货人姓名 / 收货手机号，一个输入框搜全部 */
    @Schema(description = "关键字(订单号/用户账号/收货人/手机号)")
    private String keyword;

    @Schema(description = "订单状态: WAIT_PAY/WAIT_SHIP/SHIPPED/COMPLETED/CANCELLED/CLOSED")
    private String orderStatus;

    @Schema(description = "支付状态: UNPAID/PAID/PARTIAL_REFUND/REFUNDED")
    private String payStatus;

    @Schema(description = "退款状态: NONE/APPLYING/REJECTED/PARTIAL/FULL")
    private String refundStatus;

    @Schema(description = "按店铺筛选(店铺ID)")
    private String storeId;

    @Schema(description = "支付渠道")
    private String payChannel;

    @Schema(description = "下单来源: web/h5/app/api")
    private String orderSource;

    /**
     * 时间条件用 **String** 而不是 {@code java.util.Date}。
     *
     * <p>Spring 的 {@code DateFormatterRegistrar} 默认只给 {@code Date} 注册与
     * {@code Long}/{@code Calendar} 的转换器，**不会**注册 String→Date 格式化器。
     * DTO 里写 {@code Date} 时，前端传 {@code 2026-10-14 18:30:00} 会直接抛
     * {@code TypeMismatchException} → 接口 400，而且**只有带时间筛选的请求才会 400**，
     * 不带筛选一切正常——这类问题在联调前极难发现。
     *
     * <p>解析在 {@code QueryDateUtils} / {@code OrderTimeRange} 里统一完成，
     * 支持 {@code yyyy-MM-dd}、{@code yyyy-MM-dd HH:mm}、{@code yyyy-MM-dd HH:mm:ss}
     * 与 ISO 形式。结束时间按给定精度**包含**（见 {@code QueryDateUtils} 注释）。
     */
    @Schema(description = "下单开始时间(yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss)")
    private String startTime;

    @Schema(description = "下单结束时间(按给定精度包含)")
    private String endTime;

    @Schema(description = "支付开始时间")
    private String payStartTime;

    @Schema(description = "支付结束时间(按给定精度包含)")
    private String payEndTime;

    @Schema(description = "金额下限(按应付金额)")
    private BigDecimal minAmount;

    @Schema(description = "金额上限(按应付金额)")
    private BigDecimal maxAmount;

    @Schema(description = "是否包含已归档订单(默认否)")
    private Boolean includeArchived = Boolean.FALSE;

    /** 前端通常传 date 而不是 date-time，这里接受两种写法并统一解析 */
    public void setStartTime(String startTime) {
        this.startTime = trimToNull(startTime);
    }

    public void setEndTime(String endTime) {
        this.endTime = trimToNull(endTime);
    }

    public void setPayStartTime(String payStartTime) {
        this.payStartTime = trimToNull(payStartTime);
    }

    public void setPayEndTime(String payEndTime) {
        this.payEndTime = trimToNull(payEndTime);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
