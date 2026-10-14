package org.jeecg.modules.shop.cart.service;

import org.jeecg.modules.shop.cart.entity.dto.CartAddDTO;
import org.jeecg.modules.shop.cart.entity.dto.CartBatchDTO;
import org.jeecg.modules.shop.cart.entity.dto.CartQuantityDTO;
import org.jeecg.modules.shop.cart.entity.vo.StoreCartCountVO;
import org.jeecg.modules.shop.cart.entity.vo.StoreCartVO;

/**
 * <p>
 * 店铺端购物车 Service
 * </p>
 *
 * 接口约定：**所有写操作都返回操作后的完整购物车**。
 * 前端一次请求就能拿到最新状态，不必"改完再查一次"——
 * 少一次往返，也少一次两次请求之间状态不一致的机会。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

public interface IShopCartService {

    /** 购物车列表（含实时库存/上下架判定与汇总） */
    StoreCartVO listCart(CartContext ctx, String language);

    /** 购物车角标（条目数 + 件数） */
    StoreCartCountVO countCart(CartContext ctx);

    /** 加入购物车：有规格商品必须指定 SKU，同 SKU 数量累加 */
    StoreCartVO addItem(CartContext ctx, CartAddDTO dto, String language);

    /** 修改数量 */
    StoreCartVO updateQuantity(CartContext ctx, CartQuantityDTO dto, String language);

    /** 删除单条（幂等：条目已不存在时返回当前购物车，不报错） */
    StoreCartVO removeItem(CartContext ctx, String itemId, String language);

    /** 批量删除 */
    StoreCartVO batchRemove(CartContext ctx, CartBatchDTO dto, String language);

    /** 清空购物车 */
    StoreCartVO clearCart(CartContext ctx, String language);

    /** 一键清理失效商品（下架 / 停售 / 无货 / 规格已变更） */
    StoreCartVO clearInvalidItems(CartContext ctx, String language);
}
