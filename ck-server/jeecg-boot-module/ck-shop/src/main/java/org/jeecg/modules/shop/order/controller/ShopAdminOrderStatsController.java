package org.jeecg.modules.shop.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.order.entity.dto.OrderQuery;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsOverviewVO;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsRankVO;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsStatusVO;
import org.jeecg.modules.shop.order.entity.vo.OrderStatsTrendVO;
import org.jeecg.modules.shop.order.service.IShopOrderStatsService;
import org.jeecg.modules.shop.order.service.IShopOrderTaskService;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
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

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

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
        requireGlobalTaskPermission();
        return Result.OK(toMap(taskService.closeTimeoutOrders(batchSize)));
    }

    @Operation(summary = "运维-手动触发支付补单任务")
    @RequiresPermissions("shop-order:compensate")
    @PostMapping(value = "/task/compensate")
    public Result<Map<String, Object>> runCompensate(
            @RequestParam(name = "batchSize", required = false, defaultValue = "200") Integer batchSize) {
        requireGlobalTaskPermission();
        return Result.OK(toMap(taskService.compensatePendingPayments(batchSize)));
    }

    /**
     * 这两个运维接口会**扫描并处理所有店铺**的订单（它们的 SQL 不带店铺/组织条件，
     * 因为定时任务本身是平台级的）。所以手动触发的门槛必须高于单店运营：
     * 只有拥有 {@code shop-store:manage-all} 的账号（总部/平台管理员）才能执行，
     * 否则 A 店运营可以用它把 B 店的超时订单批量关掉、或触发 B 店的补单。
     *
     * <p>校验放在 Controller 而不是 Service：Service 里的这两个方法同时被
     * Quartz 定时任务调用，那里没有登录用户，加在 Service 会让定时任务直接失败。
     */
    private void requireGlobalTaskPermission() {
        if (!orgScopeResolver.canManageAll()) {
            throw new JeecgBootException(
                    "该接口会处理全部店铺的订单，仅限拥有 " + ShopOrgScopeResolver.MANAGE_ALL_PERM
                            + " 权限的账号执行。单店订单请使用「人工补偿」（按订单号）。");
        }
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
