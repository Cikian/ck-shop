package org.jeecg.modules.shop.order.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 后台订单列表项。
 * </p>
 *
 * 继承 {@code ShopOrder}：后台需要看到成本、毛利、IP、UA、渠道等全部经营字段。
 * 与前台 VO 的字段差异（前台不含成本）由**两个不同的类**保证，
 * 而不是靠服务层记得手动抹字段。
 *
 * <p>唯一的加工是**手机号脱敏**：后台列表会被投屏、截图、被多人看到，
 * 完整手机号没必要出现在列表里。需要完整号码时走"发货详情"接口，
 * 那条路径有权限校验、有操作流水。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "后台订单列表项")
public class AdminOrderListVO extends org.jeecg.modules.shop.order.entity.ShopOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "订单状态中文")
    private String orderStatusLabel;

    @Schema(description = "支付状态中文")
    private String payStatusLabel;

    @Schema(description = "退款状态中文")
    private String refundStatusLabel;

    @Schema(description = "商品明细")
    private List<AdminOrderItemVO> items = new ArrayList<>();

    @Schema(description = "退款单数量(待审核时后台列表需要红点提示)")
    private Integer pendingRefundCount;
}
