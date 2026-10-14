package org.jeecg.modules.shop.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.shop.order.entity.dto.OrderQuery;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsOverviewVO;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsRankVO;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsStatusVO;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsTrendVO;
import org.jeecg.modules.shop.order.service.IShopOrderStatsService;
import org.jeecg.modules.shop.order.service.IShopOrderTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 后台订单统计接口。
 * </p>
 *
 * <h3>统计口径（与 {@code OrderStatsOverviewVO} 的注释一致，此处是权威说明）</h3>
 * <ul>
 *   <li><b>GMV</b>：已支付订单的应付金额之和，不扣退款；</li>
 *   <li><b>实收</b>：已支付订单的实收金额之和；</li>
 *   <li><b>净收入</b>：实收 − 退款，这才是留在账上的钱；</li>
 *   <li><b>退款率</b>：按金额算，不按单数（退 1 件 9999 元与退 10 件 9.9 元影响差三个数量级）；</li>
 *   <li><b>下单量按下单时间、GMV 按支付时间、退款额按退款到账时间</b>，三者不混用。</li>
 * </ul>
 *
 * <p>每个接口都单独暴露，而不是"一个接口返回全部"：
 * 大屏可以按需刷新（总览 30 秒刷、排行 5 分钟刷），
 * 不必为了一个数字重新计算全部指标。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Tag(name = "后台-订单统计")
@RestController
@RequestMapping("/shop/order/admin/stats")
@Slf4j
public class ShopAdminOrderStatsController {

    @Autowired
    private IShopOrderStatsService statsService;

    @Autowired
    private IShopOrderTaskService taskService;

    @Operation(summary = "统计-总览(GMV/实收/退款/净收入/转化率/风险指标)")
    @RequiresPermissions("shop-order:stats")
    @GetMapping(value = "/overview")
    public Result<OrderStatsOverviewVO> overview(OrderQuery query) {
        return Result.OK(statsService.overview(query));
    }

    @Operation(summary = "统计-订单状态分布(饼图)")
    @RequiresPermissions("shop-order:stats")
    @GetMapping(value = "/status")
    public Result<List<OrderStatsStatusVO>> status(OrderQuery query) {
        return Result.OK(statsService.statusDistribution(query));
    }

    @Operation(summary = "统计-按日趋势(下单量/GMV/实收/退款/净收入)")
    @RequiresPermissions("shop-order:stats")
    @GetMapping(value = "/trend")
    public Result<List<OrderStatsTrendVO>> trend(OrderQuery query) {
        return Result.OK(statsService.trend(query));
    }

    @Operation(summary = "统计-热销商品/SKU排行")
    @RequiresPermissions("shop-order:stats")
    @GetMapping(value = "/rank/product")
    public Result<List<OrderStatsRankVO>> rankProduct(OrderQuery query,
                                                      @RequestParam(name = "limit", required = false) Integer limit) {
        return Result.OK(statsService.rankByProduct(query, limit));
    }

    @Operation(summary = "统计-热销分类排行")
    @RequiresPermissions("shop-order:stats")
    @GetMapping(value = "/rank/category")
    public Result<List<OrderStatsRankVO>> rankCategory(OrderQuery query,
                                                       @RequestParam(name = "limit", required = false) Integer limit) {
        return Result.OK(statsService.rankByCategory(query, limit));
    }

    @Operation(summary = "运维-手动触发超时关单任务")
    @RequiresPermissions("shop-order:compensate")
    @PostMapping(value = "/task/close-timeout")
    public Result<Map<String, Object>> runCloseTimeout(
            @RequestParam(name = "batchSize", required = false, defaultValue = "200") Integer batchSize) {
        return Result.OK(toMap(taskService.closeTimeoutOrders(batchSize)));
    }

    @Operation(summary = "运维-手动触发支付补单任务")
    @RequiresPermissions("shop-order:compensate")
    @PostMapping(value = "/task/compensate")
    public Result<Map<String, Object>> runCompensate(
            @RequestParam(name = "batchSize", required = false, defaultValue = "200") Integer batchSize) {
        return Result.OK(toMap(taskService.compensatePendingPayments(batchSize)));
    }

    private Map<String, Object> toMap(IShopOrderTaskService.TaskResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scanned", result.getScanned());
        data.put("succeeded", result.getSucceeded());
        data.put("failed", result.getFailed());
        data.put("skipped", result.getSkipped());
        data.put("costMillis", result.getCostMillis());
        data.put("message", result.getMessage());
        return data;
    }
}
