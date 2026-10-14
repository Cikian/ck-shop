package org.jeecg.modules.shop.favorite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.favorite.entity.ShopFavorite;
import org.jeecg.modules.shop.favorite.entity.vo.FavoriteStatusVO;
import org.jeecg.modules.shop.favorite.mapper.ShopFavoriteMapper;
import org.jeecg.modules.shop.favorite.service.FavoriteContext;
import org.jeecg.modules.shop.favorite.service.IShopFavoriteService;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.vo.StoreProductCardVO;
import org.jeecg.modules.shop.product.mapper.ShopProductMapper;
import org.jeecg.modules.shop.product.service.IShopProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 商品收藏 Service 实现
 * </p>
 *
 * 三条不可动摇的规则（与接口注释一致，这里给出实现层面的落点）：
 * <ol>
 *   <li><b>商品可见性先于一切</b>：任何一次读/写都先走
 *       {@link IShopProductService#getStoreCard}，让"商品存在 + 属于本店 + 已上架"的判断
 *       只有一处实现。少了这一步，收藏接口就成了"能用任意 productId 探测别店商品"的越权入口。</li>
 *   <li><b>关系表与计数同事务</b>：{@code favorite_count} 的 +1 / -1 只跟着关系表**真正发生变化**
 *       的那一次走（靠 UPDATE 的受影响行数判断），重复点收藏/取消不会把计数刷歪。</li>
 *   <li><b>返回的计数是回读值</b>：变更后再查一次商品表的当前值返回，
 *       不做"旧值 ± 1"的前端式心算——并发场景下那个数字会是错的。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Slf4j
@Service
public class ShopFavoriteServiceImpl extends ServiceImpl<ShopFavoriteMapper, ShopFavorite> implements IShopFavoriteService {

    @Autowired
    private IShopProductService shopProductService;

    @Autowired
    private ShopProductMapper shopProductMapper;

    // ==================================================================
    // 状态查询
    // ==================================================================

    @Override
    public FavoriteStatusVO status(FavoriteContext context, String productId, String language) {
        // 即使只是"查我收藏没有"，也要先确认这件商品在本店可见：
        // 否则接口会变成一个探测器（能通过响应差异判断某 id 是不是别店的商品）
        StoreProductCardVO card = requireVisibleProduct(context, productId, language);
        ShopFavorite favorite = baseMapper.selectAnyByProduct(
                context.getUserId(), context.getStoreId(), card.getId());
        boolean favorited = favorite != null && Integer.valueOf(0).equals(favorite.getDelFlag());
        return buildStatus(card.getId(), favorited, card.getFavoriteCount());
    }

    // ==================================================================
    // 收藏 / 取消收藏（取反）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteStatusVO toggle(FavoriteContext context, String productId, String language) {
        StoreProductCardVO card = requireVisibleProduct(context, productId, language);
        String pid = card.getId();

        // 含已软删除的行：唯一索引不含 del_flag，取消过的那一行仍占着键位
        ShopFavorite existing = baseMapper.selectAnyByProduct(context.getUserId(), context.getStoreId(), pid);

        boolean favorited;
        int delta;
        if (existing == null) {
            // 首次收藏：直接 INSERT
            Date now = new Date();
            ShopFavorite favorite = new ShopFavorite()
                    .setUserId(context.getUserId())
                    .setUsername(context.getUsername())
                    .setStoreId(context.getStoreId())
                    .setProductId(pid)
                    // 归属部门取"店铺绑定的部门"：商品可见性刚刚由 getStoreCard 校验过，
                    // 它的归属必定落在该部门的前缀域内，这里存的就是这次收藏的口径快照
                    .setOrgCode(context.getStoreOrgCode())
                    .setCreateBy(context.getUsername())
                    .setCreateTime(now)
                    .setUpdateBy(context.getUsername())
                    .setUpdateTime(now)
                    .setDelFlag(0);
            baseMapper.insert(favorite);
            favorited = true;
            delta = 1;
        } else if (Integer.valueOf(0).equals(existing.getDelFlag())) {
            // 已收藏 -> 取消。只有**真的删掉了 1 行**才减计数，
            // 这样"连点两次取消"不会把 favorite_count 多减一次
            int removed = baseMapper.softDeleteByProduct(
                    context.getUserId(), context.getStoreId(), pid, context.getUsername());
            favorited = false;
            delta = removed > 0 ? -1 : 0;
        } else {
            // 取消过 -> 复活（create_time 保留首次收藏时间，不重写）
            int restored = baseMapper.restoreFavorite(
                    context.getUserId(), context.getStoreId(), pid, context.getUsername());
            favorited = true;
            delta = restored > 0 ? 1 : 0;
        }

        if (delta != 0) {
            shopProductMapper.incrementFavoriteCount(pid, delta);
        }
        return buildStatus(pid, favorited, loadFavoriteCount(pid, card.getFavoriteCount()));
    }

    // ==================================================================
    // 我的收藏（分页）
    // ==================================================================

    @Override
    public IPage<StoreProductCardVO> listFavorites(FavoriteContext context, Integer pageNo, Integer pageSize,
                                                   String language) {
        int current = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 12 : Math.min(pageSize, 60);
        Page<StoreProductCardVO> empty = new Page<>(current, size, 0);
        empty.setRecords(new ArrayList<>());

        if (StringUtils.isBlank(context.getStoreOrgCode())) {
            // 店铺没绑部门属于数据缺失：收藏列表降级为空，而不是 500（与店铺端商品接口一致）
            log.warn("店铺[{}]未绑定部门，收藏列表返回空", context.getStoreName());
            return empty;
        }

        // 分页在关系表上做（按收藏时间倒序），商品信息随后批量实时查询：
        // 先分页商品再筛收藏会在"收藏了已下架商品"时出现空页，用户会以为收藏丢了。
        // 注意 total 是**关系表的收藏总数**（含已下架/已删除的），
        // 因此极端情况下可能出现"total=3 但只显示出 2 件"——这是有意的取舍：
        // 与其为了让数字好看而把用户真实的收藏记录数藏起来，不如让列表诚实地少一行。
        Page<ShopFavorite> favoritePage = super.page(
                new Page<>(current, size),
                new LambdaQueryWrapper<ShopFavorite>()
                        .eq(ShopFavorite::getUserId, context.getUserId())
                        .eq(ShopFavorite::getStoreId, context.getStoreId())
                        .orderByDesc(ShopFavorite::getCreateTime)
                        .orderByDesc(ShopFavorite::getId));
        if (favoritePage.getRecords().isEmpty()) {
            return empty;
        }

        List<String> productIds = favoritePage.getRecords().stream()
                .map(ShopFavorite::getProductId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        List<StoreProductCardVO> cards = shopProductService.listStoreCardsByIds(
                productIds, context.getStoreOrgCode(), language);

        // 批量查询不保证顺序，这里按关系表的收藏时间倒序重排；已下架/已删除的商品自然被丢掉
        Map<String, StoreProductCardVO> cardMap = new HashMap<>(cards.size());
        for (StoreProductCardVO card : cards) {
            cardMap.put(card.getId(), card);
        }
        List<StoreProductCardVO> ordered = new ArrayList<>(cards.size());
        for (String pid : productIds) {
            StoreProductCardVO card = cardMap.get(pid);
            if (card != null) {
                ordered.add(card);
            }
        }

        Page<StoreProductCardVO> result = new Page<>(favoritePage.getCurrent(), favoritePage.getSize(),
                favoritePage.getTotal());
        result.setRecords(ordered);
        return result;
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /**
     * 解析并校验"这件商品在本店可见"，返回它的卡片。
     * 不可见时直接抛业务异常（错误文案由商品层统一给出，此处不再改写）。
     */
    private StoreProductCardVO requireVisibleProduct(FavoriteContext context, String productId, String language) {
        if (context == null || StringUtils.isBlank(context.getUserId())) {
            throw new JeecgBootException("请先登录后再收藏商品");
        }
        if (StringUtils.isBlank(context.getStoreOrgCode())) {
            // 写接口在店铺归属缺失时直接拒绝：放过去就会产生一条任何前台都看不到的收藏记录
            throw new JeecgBootException("当前店铺未绑定部门，暂时无法收藏商品");
        }
        if (StringUtils.isBlank(productId)) {
            throw new JeecgBootException("缺少商品 ID");
        }
        // 复用商品层的可见性判断：归属部门前缀匹配 + 上架状态 + 未软删除，一处实现
        return shopProductService.getStoreCard(context.getStoreOrgCode(), productId, language);
    }

    /**
     * 变更后回读商品当前收藏数。
     * 回读失败（商品刚好被删）时退回"卡片的旧值 + 本次增量夹到 0"的兜底，
     * 而不是把 null 抛给前端 —— 心形状态已经变更成功，不该因为一个展示数字报错。
     */
    private Integer loadFavoriteCount(String productId, Integer fallback) {
        ShopProduct product = shopProductMapper.selectById(productId);
        if (product != null && product.getFavoriteCount() != null) {
            return product.getFavoriteCount();
        }
        return fallback == null ? 0 : Math.max(fallback, 0);
    }

    private FavoriteStatusVO buildStatus(String productId, boolean favorited, Integer favoriteCount) {
        return new FavoriteStatusVO()
                .setProductId(productId)
                .setFavorited(favorited)
                .setFavoriteCount(favoriteCount == null ? 0 : Math.max(favoriteCount, 0));
    }
}
