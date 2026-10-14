package org.jeecg.modules.shop.order.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 结算预览出参。
 * </p>
 *
 * <h3>这个接口是"防止意外扣款"的关键</h3>
 * 它把服务端算出的**每一项金额**摊开给用户看，并且回一个 {@code priceToken}。
 * 前端在结算页展示的必须是这里返回的金额，而不是自己算的近似值；
 * 提交订单时把 token 带回来，服务端再复核一遍。
 * 这样"用户看到的"和"实际扣的"必然是同一个数。
 *
 * <p>若存在 {@code available = false} 的明细，接口仍然返回 200 并给出原因，
 * 由前端决定是"置灰该行"还是"整单不可提交"——**不在这里直接抛异常**。
 * 因为用户需要看到"哪一件没货了"，一句"结算失败"帮不上任何忙。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Accessors(chain = true)
@Schema(description = "结算预览")
public class OrderPreviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "明细列表")
    private List<StoreOrderItemVO> items = new ArrayList<>();

    @Schema(description = "金额明细")
    private OrderAmountVO amount;

    /**
     * 结算价格令牌：提交订单时必须原样回传。
     * 它绑定了用户、店铺、金额与商品明细，篡改或过期都会导致下单被拒。
     */
    @Schema(description = "结算价格令牌(提交订单时必须回传)")
    private String priceToken;

    @Schema(description = "令牌过期时间(epoch 毫秒)")
    private Long priceTokenExpireAt;

    @Schema(description = "是否存在不可结算的明细")
    private Boolean hasInvalidItem;

    @Schema(description = "全部明细是否都可结算(前端据此决定提交按钮是否可点)")
    private Boolean allAvailable;
}
