package org.jeecg.modules.shop.favorite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.favorite.entity.ShopFavorite;
import org.jeecg.modules.shop.favorite.entity.vo.FavoriteStatusVO;
import org.jeecg.modules.shop.product.entity.vo.StoreProductCardVO;

/**
 * <p>
 * 商品收藏 Service
 * </p>
 *
 * 三条对外承诺：
 * <ol>
 *   <li><b>幂等</b>：toggle 是"取反"语义，但同一状态重复提交不会把关系表搞乱
 *       （已收藏再收藏 = 无操作，未收藏再取消 = 无操作），也不会让 favorite_count 多算/少算；</li>
 *   <li><b>计数同源</b>：ck_shop_product.favorite_count 与关系表在同一个事务里变更，
 *       返回值里的 count 是**变更后重新读出来**的真实值，不是前端算出来的；</li>
 *   <li><b>不泄漏他店商品</b>：所有读写都以"用户 + 店铺"为条件，并校验商品落在该店铺的
 *       归属部门可见域内，A 店的收藏里不可能出现 B 店的商品。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

public interface IShopFavoriteService extends IService<ShopFavorite> {

    /**
     * 查询当前用户对某商品的收藏状态（只读，不改任何数据）。
     *
     * @param productId 商品主键
     * @param language  界面语言（用于校验商品可见性）
     * @return 收藏状态（favorited + 商品收藏总数）
     */
    FavoriteStatusVO status(FavoriteContext context, String productId, String language);

    /**
     * 收藏 / 取消收藏（取反）。返回**操作后**的最终状态，前端据此刷新心形与收藏数。
     */
    FavoriteStatusVO toggle(FavoriteContext context, String productId, String language);

    /**
     * 我的收藏（分页）。商品信息实时读取，已下架 / 已删除的商品不会出现在结果里。
     *
     * @param pageNo   页码，从 1 开始
     * @param pageSize 每页条数
     * @param language 界面语言（标题 / 封面 / 分类名按语言回退）
     */
    IPage<StoreProductCardVO> listFavorites(FavoriteContext context, Integer pageNo, Integer pageSize, String language);
}
