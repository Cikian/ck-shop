package org.jeecg.modules.shop.cart.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 店铺端购物车整体返回：明细 + 汇总
 * </p>
 *
 * 所有会改动购物车的接口（加购/改数量/删除/批量删除/清空）都返回这个结构，
 * 让前端一次请求就能拿到最新状态，不必"改完再查一次"——
 * 少一次往返，也少一次"两次请求之间状态不一致"的机会。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@Schema(description = "店铺端购物车")
public class StoreCartVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "购物车明细（按最近修改时间倒序）")
    private List<StoreCartItemVO> items = new ArrayList<>();

    @Schema(description = "汇总")
    private StoreCartSummaryVO summary = new StoreCartSummaryVO();
}
