package org.jeecg.modules.shop.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.shop.order.entity.dto.RefundAuditDTO;
import org.jeecg.modules.shop.order.entity.dto.RefundQuery;
import org.jeecg.modules.shop.order.entity.vo.OrderRefundVO;
import org.jeecg.modules.shop.order.service.IShopRefundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>
 * 后台退款管理接口。
 * </p>
 *
 * <h3>审核是两步式的</h3>
 * "点通过"不等于"钱已经退了"：
 * <ol>
 *   <li>审核通过 → **先在支付单上占退款额度**（原子条件更新），
 *       占不到就直接拒绝（说明额度不足或并发超额）；</li>
 *   <li>再提交渠道退款。渠道可能同步成功、可能受理中（异步）、可能失败。</li>
 * </ol>
 * 顺序绝不能反：先提交渠道再占额度，一旦额度不足就会出现
 * "钱已经退出去了，系统却认为没退" —— 资金已经离开账户，无法挽回。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Tag(name = "后台-退款管理")
@RestController
@RequestMapping("/shop/refund/admin")
@Slf4j
public class ShopAdminRefundController {

    @Autowired
    private IShopRefundService refundService;

    @Autowired
    private OrderRequestContextResolver contextResolver;

    @Operation(summary = "退款单-分页列表")
    @RequiresPermissions("shop-refund:list")
    @GetMapping(value = "/list")
    public Result<Page<OrderRefundVO>> list(RefundQuery query) {
        return Result.OK(refundService.adminPage(query));
    }

    @Operation(summary = "退款单-详情")
    @RequiresPermissions("shop-refund:list")
    @GetMapping(value = "/detail")
    public Result<OrderRefundVO> detail(@RequestParam(name = "refundNo") String refundNo) {
        return Result.OK(refundService.adminDetail(refundNo));
    }

    @Operation(summary = "退款单-审核(通过即占额度并提交渠道退款)")
    @RequiresPermissions("shop-refund:audit")
    @PostMapping(value = "/audit")
    public Result<String> audit(@RequestBody RefundAuditDTO dto) {
        refundService.audit(dto, contextResolver.currentUsername());
        return Result.OK(Boolean.TRUE.equals(dto == null ? null : dto.getApproved())
                ? "审核通过，已提交渠道退款" : "已拒绝退款申请");
    }

    @Operation(summary = "退款单-重试渠道退款(仅失败/退款中)")
    @RequiresPermissions("shop-refund:retry")
    @PostMapping(value = "/retry")
    public Result<String> retry(@RequestParam(name = "refundNo") String refundNo) {
        refundService.retryChannelRefund(refundNo, contextResolver.currentUsername());
        return Result.OK("已重新提交渠道退款");
    }

    @Operation(summary = "退款单-查询某订单可退金额")
    @RequiresPermissions("shop-refund:list")
    @GetMapping(value = "/refundable")
    public Result<Map<String, Object>> refundable(@RequestParam(name = "orderNo") String orderNo) {
        BigDecimal amount = refundService.refundableAmount(orderNo);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("refundableAmount", amount);
        return Result.OK(data);
    }
}
