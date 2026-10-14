package org.jeecg.modules.shop.cart.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.jeecg.modules.shop.cart.entity.dto.CartStatsQuery;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsEventVO;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsOverviewVO;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsRankVO;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsTrendVO;
import org.jeecg.modules.shop.store.entity.ShopStore;

import java.util.List;

/**
 * <p>
 * 后台加购统计 Service
 * </p>
 *
 * 所有查询都会先经过 {@code ShopOrgScopeResolver} 求出的**可见店铺集合**，
 * 拿不到可见域时返回空结果而不是"不过滤"——统计数据泄露等同于把别店的经营情况交出去。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

public interface IShopCartStatsService {

    /** 加购总览（含当前购物车快照） */
    CartStatsOverviewVO overview(CartStatsQuery query, String language);

    /** 商品/SKU 热度排行 */
    List<CartStatsRankVO> rank(CartStatsQuery query, String language);

    /** 按日趋势（日期连续，无数据的日期补 0） */
    List<CartStatsTrendVO> trend(CartStatsQuery query, String language);

    /** 加购明细分页 */
    IPage<CartStatsEventVO> eventPage(CartStatsQuery query, String language);

    /** 店铺下拉选项（按当前账号可见域过滤） */
    List<ShopStore> storeOptions();
}
