package org.jeecg.modules.shop.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.shop.order.entity.dto.OrderActionDTO;
import org.jeecg.modules.shop.order.entity.dto.OrderQuery;
import org.jeecg.modules.shop.order.entity.dto.OrderRemarkDTO;
import org.jeecg.modules.shop.order.entity.dto.OrderShipDTO;
import org.jeecg.modules.shop.order.entity.vo.AdminOrderDetailVO;
import org.jeecg.modules.shop.order.entity.vo.AdminOrderListVO;
import org.jeecg.modules.shop.order.service.IShopOrderService;
import org.jeecg.modules.shop.order.service.IShopOrderTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>
 * 后台订单管理接口。
 * </p>
 *
 * <h3>刻意**不提供删除接口**</h3>
 * 需求明确要求"订单严禁物理删除；有效订单在逻辑层面也禁止删除"。
 * 因此这里没有 {@code /delete}、没有 {@code /deleteBatch}——
 * 不是"忘了加权限"，而是这个能力根本不存在。
 * 需要清理列表时用【归档】，且归档只对已取消/已关闭订单开放，
 * 归档后数据仍然完整保留（可按单号检索）。
 *
 * <h3>数据可见域</h3>
 * 所有接口都经过 {@code ShopOrgScopeResolver}：运营只能看/操作本部门的数据，
 * {@code shop-store:manage-all}（总部/管理员）可跨部门。
 * 按 id 操作的接口（详情、发货等）会额外做一次 {@code checkAccessible}，
 * 因为列表过滤挡不住"直接按单号调接口"。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Tag(name = "后台-订单管理")
@RestController
@RequestMapping("/shop/order/admin")
@Slf4j
public class ShopAdminOrderController {

    @Autowired
    private IShopOrderService orderService;

    @Autowired
    private IShopOrderTaskService taskService;

    @Operation(summary = "订单-分页列表")
    @RequiresPermissions("shop-order:list")
    @GetMapping(value = "/list")
    public Result<Page<AdminOrderListVO>> list(OrderQuery query) {
        return Result.OK(orderService.adminPage(query));
    }

    @Operation(summary = "订单-详情(含支付单/退款单/完整操作流水)")
    @RequiresPermissions("shop-order:detail")
    @GetMapping(value = "/detail")
    public Result<AdminOrderDetailVO> detail(@RequestParam(name = "orderNo") String orderNo) {
        return Result.OK(orderService.adminDetail(orderNo));
    }

    @Operation(summary = "订单-发货")
    @RequiresPermissions("shop-order:ship")
    @PostMapping(value = "/ship")
    public Result<String> ship(@RequestBody OrderShipDTO dto) {
        orderService.ship(dto == null ? null : dto.getOrderNo(), dto);
        return Result.OK("发货成功");
    }

    @Operation(summary = "订单-关闭/取订单(已支付订单必须先全额退款)")
    @RequiresPermissions("shop-order:close")
    @PostMapping(value = "/close")
    public Result<String> close(@RequestBody OrderActionDTO dto) {
        orderService.adminClose(dto == null ? null : dto.getOrderNo(),
                dto == null ? null : dto.getReason());
        return Result.OK("订单已关闭");
    }

    @Operation(summary = "订单-修改卖家备注")
    @RequiresPermissions("shop-order:remark")
    @PostMapping(value = "/remark")
    public Result<String> remark(@RequestBody OrderRemarkDTO dto) {
        orderService.updateSellerRemark(dto == null ? null : dto.getOrderNo(),
                dto == null ? null : dto.getSellerRemark());
        return Result.OK("备注已保存");
    }

    @Operation(summary = "订单-归档(仅已取消/已关闭；数据保留，永不物理删除)")
    @RequiresPermissions("shop-order:archive")
    @PostMapping(value = "/archive")
    public Result<String> archive(@RequestParam(name = "orderNo") String orderNo) {
        orderService.archive(orderNo);
        return Result.OK("订单已归档（数据完整保留，可按单号检索）");
    }

    @Operation(summary = "订单-人工补偿(按订单号主动查询渠道支付结果)")
    @RequiresPermissions("shop-order:compensate")
    @PostMapping(value = "/compensate")
    public Result<Map<String, Object>> compensate(@RequestParam(name = "orderNo") String orderNo) {
        IShopOrderTaskService.TaskResult result = taskService.compensateOrder(orderNo);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scanned", result.getScanned());
        data.put("succeeded", result.getSucceeded());
        data.put("skipped", result.getSkipped());
        data.put("message", result.getMessage());
        return Result.OK(result.getMessage(), data);
    }
}
