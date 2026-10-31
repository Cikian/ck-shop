package org.jeecg.modules.shop.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.shop.order.entity.dto.OrderActionDTO;
import org.jeecg.modules.shop.order.entity.dto.OrderPreviewDTO;
import org.jeecg.modules.shop.order.entity.dto.OrderQuery;
import org.jeecg.modules.shop.order.entity.dto.OrderSubmitDTO;
import org.jeecg.modules.shop.order.entity.dto.RefundApplyDTO;
import org.jeecg.modules.shop.order.entity.vo.OrderPreviewVO;
import org.jeecg.modules.shop.order.entity.vo.OrderRefundVO;
import org.jeecg.modules.shop.order.entity.vo.OrderSubmitVO;
import org.jeecg.modules.shop.order.entity.vo.StoreOrderDetailVO;
import org.jeecg.modules.shop.order.entity.vo.StoreOrderListVO;
import org.jeecg.modules.shop.order.service.IShopOrderService;
import org.jeecg.modules.shop.order.service.IShopRefundService;
import org.jeecg.modules.shop.order.service.OrderContext;
import org.jeecg.modules.shop.region.RegionLangResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 店铺端（前台）订单接口。
 * </p>
 *
 * <b>刻意不加 {@code @IgnoreAuth}</b>：订单必须知道"是谁的单"，
 * 匿名访问没有任何意义。未登录时由 Shiro 直接拦掉（401），前端据此跳登录页。
 *
 * <p>店铺身份来自 {@code OrderRequestContextResolver}（请求头 X-Store-Code 优先，
 * 域名兜底），与商品/购物车接口完全一致——订单按店铺隔离，
 * A 店不能对 B 店的购物车下单。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Tag(name = "店铺端-订单")
@RestController
@RequestMapping("/shop/order/store")
@Slf4j
public class ShopStoreOrderController {

    @Autowired
    private IShopOrderService orderService;

    @Autowired
    private IShopRefundService refundService;

    @Autowired
    private OrderRequestContextResolver contextResolver;

    @Operation(summary = "店铺端-结算预览(服务端算钱并返回价格令牌)")
    @PostMapping(value = "/preview")
    public Result<OrderPreviewVO> preview(@RequestBody OrderPreviewDTO dto, HttpServletRequest req) {
        OrderContext context = contextResolver.build(req, true);
        return Result.OK(orderService.preview(context, dto));
    }

    @Operation(summary = "店铺端-提交订单(必须带幂等键与价格令牌)")
    @PostMapping(value = "/submit")
    public Result<OrderSubmitVO> submit(@RequestBody OrderSubmitDTO dto, HttpServletRequest req) {
        OrderContext context = contextResolver.build(req, true);
        return Result.OK("下单成功", orderService.submit(context, dto));
    }

    @Operation(summary = "店铺端-我的订单列表")
    @GetMapping(value = "/list")
    public Result<Page<StoreOrderListVO>> list(OrderQuery query, HttpServletRequest req) {
        // 读接口不强制要求店铺已绑部门：绑定关系缺失时列表为空，
        // 比整个"我的订单"页面 500 要好
        OrderContext context = contextResolver.build(req, false);
        return Result.OK(orderService.storePage(context, query));
    }

    @Operation(summary = "店铺端-订单详情")
    @GetMapping(value = "/detail")
    public Result<StoreOrderDetailVO> detail(String orderNo, HttpServletRequest req) {
        OrderContext context = contextResolver.build(req, false);
        return Result.OK(orderService.storeDetail(context, orderNo, RegionLangResolver.resolveLocale(req)));
    }

    @Operation(summary = "店铺端-取消订单(仅待支付)")
    @PostMapping(value = "/cancel")
    public Result<String> cancel(@RequestBody OrderActionDTO dto, HttpServletRequest req) {
        OrderContext context = contextResolver.build(req, false);
        orderService.cancelByBuyer(context,
                dto == null ? null : dto.getOrderNo(),
                dto == null ? null : dto.getReason());
        return Result.OK("订单已取消");
    }

    @Operation(summary = "店铺端-确认收货")
    @PostMapping(value = "/confirm-receive")
    public Result<String> confirmReceive(@RequestBody OrderActionDTO dto, HttpServletRequest req) {
        OrderContext context = contextResolver.build(req, false);
        orderService.confirmReceive(context, dto == null ? null : dto.getOrderNo());
        return Result.OK("已确认收货");
    }

    // ==================================================================
    // 退款（买家侧）
    // ==================================================================

    @Operation(summary = "店铺端-申请退款(金额由服务端计算)")
    @PostMapping(value = "/refund/apply")
    public Result<OrderRefundVO> applyRefund(@RequestBody RefundApplyDTO dto, HttpServletRequest req) {
        OrderContext context = contextResolver.build(req, false);
        return Result.OK("退款申请已提交，请等待审核", refundService.apply(context, dto));
    }

    @Operation(summary = "店铺端-撤销退款申请")
    @PostMapping(value = "/refund/withdraw")
    public Result<String> withdrawRefund(String refundNo, HttpServletRequest req) {
        OrderContext context = contextResolver.build(req, false);
        refundService.withdraw(context, refundNo);
        return Result.OK("退款申请已撤销");
    }

    @Operation(summary = "店铺端-某订单的退款单列表")
    @GetMapping(value = "/refund/list")
    public Result<List<OrderRefundVO>> refundList(String orderNo, HttpServletRequest req) {
        OrderContext context = contextResolver.build(req, false);
        return Result.OK(refundService.listByOrderNo(context, orderNo));
    }
}
