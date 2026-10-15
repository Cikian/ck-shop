package org.jeecg.modules.shop.cart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.cart.entity.ShopCartEvent;
import org.jeecg.modules.shop.cart.entity.ShopCartItem;
import org.jeecg.modules.shop.cart.entity.dto.CartAddDTO;
import org.jeecg.modules.shop.cart.entity.dto.CartBatchDTO;
import org.jeecg.modules.shop.cart.entity.dto.CartQuantityDTO;
import org.jeecg.modules.shop.cart.entity.vo.StoreCartCountVO;
import org.jeecg.modules.shop.cart.entity.vo.StoreCartItemVO;
import org.jeecg.modules.shop.cart.entity.vo.StoreCartSummaryVO;
import org.jeecg.modules.shop.cart.entity.vo.StoreCartVO;
import org.jeecg.modules.shop.cart.mapper.ShopCartEventMapper;
import org.jeecg.modules.shop.cart.mapper.ShopCartItemMapper;
import org.jeecg.modules.shop.cart.service.CartContext;
import org.jeecg.modules.shop.cart.service.IShopCartService;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.ShopProductSkuCombination;
import org.jeecg.modules.shop.product.mapper.ShopProductMapper;
import org.jeecg.modules.shop.product.mapper.ShopProductSkuCombinationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jeecg.modules.shop.common.ShopOrgUtils;

/**
 * <p>
 * 店铺端购物车实现
 * </p>
 *
 * 这里集中了三件容易写错的事，都用显式代码处理而不是"顺手带过"：
 * <ol>
 *   <li><b>加购必须是 SKU</b>：商品有规格却不传 SKU 一律拒绝；
 *       无规格商品传了 SKU 同样拒绝——两个方向都堵住，购物车里才不会出现"未选规格"的脏数据。</li>
 *   <li><b>数量必须封顶在真实库存上</b>：超出时**报错并说明还能加几件**，不做静默截断。
 *       静默截断会让用户以为加了 5 件、实际只有 3 件。</li>
 *   <li><b>失效状态由后端直出</b>：下架 / 停售 / 无货 / 规格变更四种情况的判定只写在这一处，
 *       前端只负责按状态渲染，避免"详情页说有货、购物车说没货"。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Slf4j
@Service
public class ShopCartServiceImpl extends ServiceImpl<ShopCartItemMapper, ShopCartItem> implements IShopCartService {

    /** 单品最大数量上限：既是业务约束，也是防止被恶意刷进超大数字的一道闸 */
    private static final int MAX_QUANTITY_PER_ITEM = 999;

    /** 翻译兜底语言：当前语言没有商品翻译时用它，仍没有才回退 SPU 编码 */
    private static final String DEFAULT_LANG = "zh";

    /** 允许的来源取值，未命中一律记为 api，避免任意字符串写进日志表 */
    private static final Set<String> ALLOWED_SOURCES =
            new HashSet<>(Arrays.asList("detail", "list", "cart", "api"));

    @Autowired
    private ShopCartItemMapper cartItemMapper;

    @Autowired
    private ShopCartEventMapper cartEventMapper;

    @Autowired
    private ShopProductMapper productMapper;

    @Autowired
    private ShopProductSkuCombinationMapper combinationMapper;

    // ==================================================================
    // 读
    // ==================================================================

    @Override
    public StoreCartVO listCart(CartContext ctx, String language) {
        requireContext(ctx);
        return buildCartVO(ctx, language);
    }

    @Override
    public StoreCartCountVO countCart(CartContext ctx) {
        requireContext(ctx);
        // 必须是"当前用户在当前店铺"的口径。
        // 历史缺陷：这里曾调用 cartItemMapper.countItems/sumQuantity，
        // 而那两个方法是**后台统计口径**（只按 store_id 聚合，统计全店所有买家），
        // 于是每个买家看到的角标都是全店总量，顺带把经营数据暴露给了任意登录用户。
        StoreCartCountVO vo = cartItemMapper.countUserCart(ctx.getUserId(), ctx.getStoreId());
        if (vo == null) {
            vo = new StoreCartCountVO();
        }
        // SUM 在没有行时是 NULL（SQL 里已 COALESCE，这里再兜一层，避免前端收到 null）
        if (vo.getTotalItems() == null) {
            vo.setTotalItems(0L);
        }
        if (vo.getTotalQuantity() == null) {
            vo.setTotalQuantity(0L);
        }
        return vo;
    }

    // ==================================================================
    // 写
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreCartVO addItem(CartContext ctx, CartAddDTO dto, String language) {
        requireContext(ctx);
        requireStoreOrgCode(ctx);
        if (dto == null || StringUtils.isBlank(dto.getProductId())) {
            throw new JeecgBootException("请选择要加入购物车的商品");
        }

        int quantity = dto.getQuantity() == null ? 1 : dto.getQuantity();
        if (quantity < 1) {
            throw new JeecgBootException("加购数量必须大于 0");
        }
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new JeecgBootException("单次加购数量不能超过 " + MAX_QUANTITY_PER_ITEM + " 件");
        }

        String productId = dto.getProductId().trim();
        String skuId = StringUtils.trimToEmpty(dto.getSkuId());
        String source = normalizeSource(dto.getSource());

        // ① 商品本身必须"存在 + 已上架 + 属于当前店铺"
        ShopProduct product = productMapper.selectById(productId);
        assertProductSellable(product, ctx);

        // ② 规格校验：主体是 SKU，所以这里必须把"有规格/无规格"两条路都走清楚
        List<ShopProductSkuCombination> combinations =
                combinationMapper.selectByProductIdWithValues(productId);
        boolean hasSpecs = combinations != null && !combinations.isEmpty();

        int stock;
        if (hasSpecs) {
            if (StringUtils.isBlank(skuId)) {
                throw new JeecgBootException("该商品有规格可选，请先选择规格后再加入购物车");
            }
            ShopProductSkuCombination hit = combinations.stream()
                    .filter(item -> skuId.equals(item.getId()))
                    .findFirst()
                    .orElse(null);
            if (hit == null) {
                throw new JeecgBootException("所选规格已不存在或已下架，请重新选择");
            }
            if (!"Y".equals(hit.getSaleable())) {
                throw new JeecgBootException("所选规格已停售，请选择其它规格");
            }
            stock = hit.getStock() == null ? 0 : hit.getStock();
            if (stock <= 0) {
                throw new JeecgBootException("所选规格已无货，请选择其它规格");
            }
        } else {
            if (StringUtils.isNotBlank(skuId)) {
                throw new JeecgBootException("该商品没有规格，无需选择规格");
            }
            stock = product.getStock() == null ? 0 : product.getStock();
            if (stock <= 0) {
                throw new JeecgBootException("该商品已无货，暂时无法加入购物车");
            }
        }

        int maxAllowed = Math.min(stock, MAX_QUANTITY_PER_ITEM);
        ShopCartItem existing = cartItemMapper.selectAnyBySku(
                ctx.getUserId(), ctx.getStoreId(), productId, skuId);

        int current = aliveQuantity(existing);
        int target = current + quantity;
        assertWithinStock(target, current, maxAllowed);

        String eventType;
        if (existing == null) {
            // 首次加购。并发下另一个请求可能刚插入同一行，撞唯一索引后走"复活/累加"分支再试一次。
            try {
                insertItem(ctx, productId, skuId, target);
                eventType = ShopCartEvent.TYPE_ADD;
            } catch (DuplicateKeyException e) {
                ShopCartItem concurrent = cartItemMapper.selectAnyBySku(
                        ctx.getUserId(), ctx.getStoreId(), productId, skuId);
                if (concurrent == null) {
                    throw e;
                }
                int concurrentCurrent = aliveQuantity(concurrent);
                int concurrentTarget = concurrentCurrent + quantity;
                assertWithinStock(concurrentTarget, concurrentCurrent, maxAllowed);
                cartItemMapper.mergeQuantity(ctx.getUserId(), ctx.getStoreId(),
                        productId, skuId, concurrentTarget, ctx.getUsername());
                eventType = concurrentCurrent > 0 ? ShopCartEvent.TYPE_INCR : ShopCartEvent.TYPE_ADD;
            }
        } else {
            // 已有行（可能是被软删除的历史行）：原地写入目标数量并复活，保住主键不变
            cartItemMapper.mergeQuantity(ctx.getUserId(), ctx.getStoreId(),
                    productId, skuId, target, ctx.getUsername());
            eventType = current > 0 ? ShopCartEvent.TYPE_INCR : ShopCartEvent.TYPE_ADD;
        }

        // ③ 统计：记录"加购动作"本身与本次新增的件数（不是累加后的总数，便于按动作求和）
        recordEvent(ctx, productId, skuId, quantity, eventType,
                source, product.getSysOrgCode());

        return buildCartVO(ctx, language);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreCartVO updateQuantity(CartContext ctx, CartQuantityDTO dto, String language) {
        requireContext(ctx);
        requireStoreOrgCode(ctx);
        if (dto == null || StringUtils.isBlank(dto.getItemId())) {
            throw new JeecgBootException("缺少购物车条目标识");
        }
        if (dto.getQuantity() == null || dto.getQuantity() < 1) {
            throw new JeecgBootException("数量必须大于 0，如需删除请使用删除按钮");
        }

        ShopCartItem item = loadOwnedItem(ctx, dto.getItemId());
        if (item == null) {
            throw new JeecgBootException("该商品已不在购物车中，请刷新后重试");
        }

        ShopProduct product = productMapper.selectById(item.getProductId());
        assertProductSellable(product, ctx);
        int stock = resolveStock(product, item.getSkuId());
        int maxAllowed = Math.min(stock, MAX_QUANTITY_PER_ITEM);
        int target = dto.getQuantity();
        if (target > maxAllowed) {
            throw new JeecgBootException(stock <= 0
                    ? "该商品已无货，无法修改数量"
                    : "库存不足，最多可购买 " + maxAllowed + " 件");
        }

        int before = item.getQuantity() == null ? 0 : item.getQuantity();
        if (before != target) {
            ShopCartItem update = new ShopCartItem()
                    .setId(item.getId())
                    .setQuantity(target)
                    .setUpdateBy(ctx.getUsername())
                    .setUpdateTime(new Date());
            cartItemMapper.updateById(update);
            recordEvent(ctx, item.getProductId(), item.getSkuId(), Math.abs(target - before),
                    ShopCartEvent.TYPE_UPDATE, normalizeSource(dto.getSource()), product.getSysOrgCode());
        }

        return buildCartVO(ctx, language);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreCartVO removeItem(CartContext ctx, String itemId, String language) {
        requireContext(ctx);
        if (StringUtils.isBlank(itemId)) {
            throw new JeecgBootException("缺少购物车条目标识");
        }
        // 幂等：条目已经被删除（例如用户快速点了两次、或另一个标签页删掉了）不算失败，
        // 直接把当前购物车返回即可，报错只会让用户困惑。
        ShopCartItem item = loadOwnedItem(ctx, itemId);
        if (item != null) {
            cartItemMapper.softDeleteByIds(ctx.getUserId(), ctx.getStoreId(),
                    Collections.singletonList(itemId), ctx.getUsername());
            recordEvent(ctx, item.getProductId(), item.getSkuId(),
                    item.getQuantity() == null ? 0 : item.getQuantity(),
                    ShopCartEvent.TYPE_REMOVE, "cart", null);
        }
        return buildCartVO(ctx, language);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreCartVO batchRemove(CartContext ctx, CartBatchDTO dto, String language) {
        requireContext(ctx);
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            // 刻意不把"空集合"解释成"删除全部"：前端状态错乱时静默清空购物车是不可接受的
            throw new JeecgBootException("请先选择要删除的商品");
        }
        List<String> ids = dto.getIds().stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            throw new JeecgBootException("请先选择要删除的商品");
        }

        List<ShopCartItem> owned = loadOwnedItems(ctx, ids);
        if (!owned.isEmpty()) {
            cartItemMapper.softDeleteByIds(ctx.getUserId(), ctx.getStoreId(),
                    owned.stream().map(ShopCartItem::getId).collect(Collectors.toList()),
                    ctx.getUsername());
            for (ShopCartItem item : owned) {
                recordEvent(ctx, item.getProductId(), item.getSkuId(),
                        item.getQuantity() == null ? 0 : item.getQuantity(),
                        ShopCartEvent.TYPE_REMOVE, normalizeSource(dto.getSource()), null);
            }
        }
        return buildCartVO(ctx, language);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreCartVO clearCart(CartContext ctx, String language) {
        requireContext(ctx);
        List<ShopCartItem> owned = loadAllOwnedItems(ctx);
        if (!owned.isEmpty()) {
            cartItemMapper.softDeleteAll(ctx.getUserId(), ctx.getStoreId(), ctx.getUsername());
            for (ShopCartItem item : owned) {
                recordEvent(ctx, item.getProductId(), item.getSkuId(),
                        item.getQuantity() == null ? 0 : item.getQuantity(),
                        ShopCartEvent.TYPE_CLEAR, "cart", null);
            }
        }
        return buildCartVO(ctx, language);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreCartVO clearInvalidItems(CartContext ctx, String language) {
        requireContext(ctx);
        StoreCartVO cart = buildCartVO(ctx, language);
        List<String> invalidIds = cart.getItems().stream()
                .filter(item -> !Boolean.TRUE.equals(item.getSelectable()))
                .map(StoreCartItemVO::getId)
                .collect(Collectors.toList());
        if (!invalidIds.isEmpty()) {
            Set<String> invalidIdSet = new HashSet<>(invalidIds);
            cartItemMapper.softDeleteByIds(ctx.getUserId(), ctx.getStoreId(),
                    invalidIds, ctx.getUsername());
            for (StoreCartItemVO item : cart.getItems()) {
                if (!invalidIdSet.contains(item.getId())) {
                    continue;
                }
                recordEvent(ctx, item.getProductId(), item.getSkuId(),
                        item.getQuantity() == null ? 0 : item.getQuantity(),
                        ShopCartEvent.TYPE_REMOVE, "cart", null);
            }
            cart = buildCartVO(ctx, language);
        }
        return cart;
    }

    // ==================================================================
    // 组装 & 状态判定
    // ==================================================================

    private StoreCartVO buildCartVO(CartContext ctx, String language) {
        String lang = normalizeLanguage(language);
        List<StoreCartItemVO> items = cartItemMapper.selectStoreCartItems(
                ctx.getUserId(), ctx.getStoreId(), lang, DEFAULT_LANG);
        if (items == null) {
            items = new ArrayList<>();
        }
        for (StoreCartItemVO item : items) {
            resolveStatus(item, ctx.getStoreOrgCode());
        }
        StoreCartVO vo = new StoreCartVO();
        vo.setItems(items);
        vo.setSummary(buildSummary(items));
        return vo;
    }

    /**
     * 判定一条购物车明细的实时状态，并回填 {@code status} / {@code selectable} / {@code lineTotal}。
     *
     * 判定顺序刻意是"商品级 → 规格级 → 库存级"：商品都下架了，再去纠结 SKU 库存没有意义，
     * 提示也必须先告诉用户"这件东西没了"而不是"库存不足"。
     */
    private void resolveStatus(StoreCartItemVO item, String storeOrgCode) {
        int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
        int stock = item.getStock() == null ? 0 : item.getStock();
        int skuTypeCount = item.getSkuTypeCount() == null ? 0 : item.getSkuTypeCount();
        boolean skuMode = skuTypeCount > 0;

        String status;
        if (item.getProductDelFlag() == null
                || item.getProductDelFlag() == 1
                || StringUtils.isBlank(storeOrgCode)
                || StringUtils.isBlank(item.getProductOrgCode())
                || !ShopOrgUtils.matchesOrg(item.getProductOrgCode(), storeOrgCode)
                || !"Y".equals(item.getProductStatus())) {
            // 商品被删除 / 不属于当前店铺 / 已下架：统一按"已下架"提示，
            // 不把"别店商品"这种内部细节暴露给前台用户
            status = StoreCartItemVO.STATUS_OFFLINE;
        } else if (skuMode) {
            if (StringUtils.isBlank(item.getSkuId())
                    || item.getSkuDelFlag() == null
                    || item.getSkuDelFlag() == 1
                    || !"Y".equals(item.getSkuSaleable())) {
                status = StoreCartItemVO.STATUS_SKU_INVALID;
            } else if (stock <= 0) {
                status = StoreCartItemVO.STATUS_OUT_OF_STOCK;
            } else if (quantity > stock) {
                status = StoreCartItemVO.STATUS_STOCK_INSUFFICIENT;
            } else {
                status = StoreCartItemVO.STATUS_AVAILABLE;
            }
        } else {
            if (StringUtils.isNotBlank(item.getSkuId())) {
                // 商品原来的规格被运营整体删掉了，这条记录已对不上任何规格
                status = StoreCartItemVO.STATUS_SKU_INVALID;
            } else if (stock <= 0) {
                status = StoreCartItemVO.STATUS_OUT_OF_STOCK;
            } else if (quantity > stock) {
                status = StoreCartItemVO.STATUS_STOCK_INSUFFICIENT;
            } else {
                status = StoreCartItemVO.STATUS_AVAILABLE;
            }
        }

        item.setStatus(status);
        item.setSelectable(StoreCartItemVO.STATUS_AVAILABLE.equals(status)
                || StoreCartItemVO.STATUS_STOCK_INSUFFICIENT.equals(status));
        item.setSkuId(StringUtils.trimToEmpty(item.getSkuId()));
        item.setQuantity(quantity);
        item.setStock(stock);
        item.setLineTotal(calcLineTotal(item.getPrice(), quantity, Boolean.TRUE.equals(item.getSelectable())));
    }

    private StoreCartSummaryVO buildSummary(List<StoreCartItemVO> items) {
        StoreCartSummaryVO summary = new StoreCartSummaryVO();
        int totalItems = items.size();
        int totalQuantity = 0;
        int validItems = 0;
        int validQuantity = 0;
        BigDecimal validAmount = BigDecimal.ZERO;
        for (StoreCartItemVO item : items) {
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            totalQuantity += quantity;
            if (Boolean.TRUE.equals(item.getSelectable())) {
                validItems++;
                validQuantity += quantity;
                if (item.getLineTotal() != null) {
                    validAmount = validAmount.add(item.getLineTotal());
                }
            }
        }
        summary.setTotalItems(totalItems);
        summary.setTotalQuantity(totalQuantity);
        summary.setValidItems(validItems);
        summary.setValidQuantity(validQuantity);
        summary.setValidAmount(validAmount.setScale(2, RoundingMode.HALF_UP));
        summary.setInvalidItems(totalItems - validItems);
        summary.setAllInvalid(totalItems > 0 && validItems == 0);
        return summary;
    }

    /** 小计：失效条目的金额没有意义，直接给 0 而不是"看起来像要付的钱" */
    private BigDecimal calcLineTotal(BigDecimal price, int quantity, boolean selectable) {
        if (!selectable || price == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    // ==================================================================
    // 校验 & 工具
    // ==================================================================

    private void requireContext(CartContext ctx) {
        if (ctx == null || StringUtils.isBlank(ctx.getUserId())) {
            throw new JeecgBootException("请先登录后再操作购物车");
        }
        if (StringUtils.isBlank(ctx.getStoreId())) {
            throw new JeecgBootException("无法确定当前店铺，已拒绝操作");
        }
    }

    private void requireStoreOrgCode(CartContext ctx) {
        if (StringUtils.isBlank(ctx.getStoreOrgCode())) {
            throw new JeecgBootException("店铺[" + ctx.getStoreName() + "]尚未绑定部门，暂时无法加购。"
                    + "请先在【店铺管理】中为它绑定部门");
        }
    }

    /**
     * 商品必须存在、未删除、已上架，且归属落在当前店铺（部门）可见域内。
     * 与店铺端商品详情保持完全一致的口径，避免"详情页能加购、购物车却拒收"。
     */
    private void assertProductSellable(ShopProduct product, CartContext ctx) {
        if (product == null) {
            throw new JeecgBootException("商品不存在或已被删除");
        }
        if (!"Y".equals(product.getStatus())) {
            throw new JeecgBootException("商品已下架，无法加入购物车");
        }
        String productOrgCode = StringUtils.trimToNull(product.getSysOrgCode());
        if (!ShopOrgUtils.matchesOrg(productOrgCode, ctx.getStoreOrgCode())) {
            throw new JeecgBootException("商品在当前店铺不存在或已下架");
        }
    }

    /** 取"这条 SKU（或无规格商品）当前还能卖多少"，与列表接口用的是同一套取值优先级 */
    private int resolveStock(ShopProduct product, String skuId) {
        if (StringUtils.isBlank(skuId)) {
            return product.getStock() == null ? 0 : product.getStock();
        }
        ShopProductSkuCombination combination = combinationMapper.selectById(skuId);
        if (combination == null || !"Y".equals(combination.getSaleable())) {
            throw new JeecgBootException("所选规格已停售，请重新选择");
        }
        return combination.getStock() == null ? 0 : combination.getStock();
    }

    /** 数量超出库存时报错，并把"最多还能加几件"说清楚——比一句"库存不足"有用得多 */
    private void assertWithinStock(int target, int current, int maxAllowed) {
        if (target <= maxAllowed) {
            return;
        }
        if (current >= maxAllowed) {
            throw new JeecgBootException("购物车中该商品已有 " + current + " 件，已达到库存上限（" + maxAllowed + " 件）");
        }
        throw new JeecgBootException("库存不足：最多可购买 " + maxAllowed + " 件，"
                + "购物车已有 " + current + " 件，本次最多还能加 " + (maxAllowed - current) + " 件");
    }

    private int aliveQuantity(ShopCartItem item) {
        if (item == null || item.getDelFlag() == null || item.getDelFlag() != 0) {
            return 0;
        }
        return item.getQuantity() == null ? 0 : item.getQuantity();
    }

    private ShopCartItem loadOwnedItem(CartContext ctx, String itemId) {
        return cartItemMapper.selectOne(new LambdaQueryWrapper<ShopCartItem>()
                .eq(ShopCartItem::getId, itemId)
                .eq(ShopCartItem::getUserId, ctx.getUserId())
                .eq(ShopCartItem::getStoreId, ctx.getStoreId()));
    }

    private List<ShopCartItem> loadOwnedItems(CartContext ctx, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return cartItemMapper.selectList(new LambdaQueryWrapper<ShopCartItem>()
                .eq(ShopCartItem::getUserId, ctx.getUserId())
                .eq(ShopCartItem::getStoreId, ctx.getStoreId())
                .in(ShopCartItem::getId, ids));
    }

    private List<ShopCartItem> loadAllOwnedItems(CartContext ctx) {
        return cartItemMapper.selectList(new LambdaQueryWrapper<ShopCartItem>()
                .eq(ShopCartItem::getUserId, ctx.getUserId())
                .eq(ShopCartItem::getStoreId, ctx.getStoreId()));
    }

    private void insertItem(CartContext ctx, String productId, String skuId, int quantity) {
        Date now = new Date();
        ShopCartItem item = new ShopCartItem()
                .setUserId(ctx.getUserId())
                .setUsername(ctx.getUsername())
                .setStoreId(ctx.getStoreId())
                .setProductId(productId)
                .setSkuId(skuId)
                .setQuantity(quantity)
                .setDelFlag(0)
                .setCreateBy(ctx.getUsername())
                .setCreateTime(now)
                .setUpdateBy(ctx.getUsername())
                .setUpdateTime(now);
        cartItemMapper.insert(item);
    }

    /**
     * 写入一条加购行为流水。
     *
     * 统计失败绝不能让购物车操作失败（用户只是想加个购物车），
     * 因此这里捕获异常只记日志——统计是"尽量准"，不是"牺牲主流程的正确性"。
     */
    private void recordEvent(CartContext ctx, String productId, String skuId, int quantity,
                             String eventType, String source, String orgCode) {
        try {
            if (quantity <= 0) {
                return;
            }
            Date now = new Date();
            ShopCartEvent event = new ShopCartEvent()
                    .setUserId(ctx.getUserId())
                    .setUsername(ctx.getUsername())
                    .setStoreId(ctx.getStoreId())
                    .setProductId(productId)
                    .setSkuId(StringUtils.trimToEmpty(skuId))
                    .setQuantity(quantity)
                    .setEventType(eventType)
                    .setSource(normalizeSource(source))
                    .setOrgCode(orgCode)
                    .setDelFlag(0)
                    .setCreateBy(ctx.getUsername())
                    .setCreateTime(now)
                    .setUpdateBy(ctx.getUsername())
                    .setUpdateTime(now);
            cartEventMapper.insert(event);
        } catch (Exception e) {
            log.warn("记录加购行为失败（不影响购物车操作）: userId={}, productId={}, skuId={}, type={}, err={}",
                    ctx.getUserId(), productId, skuId, eventType, e.getMessage());
        }
    }

    private String normalizeSource(String source) {
        String normalized = StringUtils.trimToEmpty(source).toLowerCase();
        return ALLOWED_SOURCES.contains(normalized) ? normalized : "api";
    }

    private String normalizeLanguage(String language) {
        if (StringUtils.isBlank(language)) {
            return DEFAULT_LANG;
        }
        String lang = language.trim().toLowerCase();
        int dash = lang.indexOf('-');
        if (dash > 0) {
            lang = lang.substring(0, dash);
        }
        return StringUtils.isBlank(lang) ? DEFAULT_LANG : lang;
    }
}
