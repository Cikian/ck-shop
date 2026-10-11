package org.jeecg.modules.shop.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.brand.entity.ShopBrand;
import org.jeecg.modules.shop.brand.entity.ShopBrandTranslation;
import org.jeecg.modules.shop.brand.mapper.ShopBrandMapper;
import org.jeecg.modules.shop.category.entity.ShopCategory;
import org.jeecg.modules.shop.category.entity.ShopCategoryTranslation;
import org.jeecg.modules.shop.category.mapper.ShopCategoryMapper;
import org.jeecg.modules.shop.category.mapper.ShopCategoryTranslationMapper;
import org.jeecg.modules.shop.common.util.ShopAuditUtils;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.ShopProductImage;
import org.jeecg.modules.shop.product.entity.ShopProductSkuCombination;
import org.jeecg.modules.shop.product.entity.ShopProductSkuCombinationValue;
import org.jeecg.modules.shop.product.entity.ShopProductSpec;
import org.jeecg.modules.shop.product.entity.ShopProductSpecValue;
import org.jeecg.modules.shop.product.entity.ShopProductTranslation;
import org.jeecg.modules.shop.product.entity.dto.ProductDTO;
import org.jeecg.modules.shop.product.entity.dto.ProductQuery;
import org.jeecg.modules.shop.product.entity.dto.SkuCombinationDTO;
import org.jeecg.modules.shop.product.entity.dto.SpecDTO;
import org.jeecg.modules.shop.product.entity.dto.SpecValueDTO;
import org.jeecg.modules.shop.product.entity.vo.ProductDetailVO;
import org.jeecg.modules.shop.product.entity.vo.ProductListVO;
import org.jeecg.modules.shop.product.entity.vo.ProductSkuVO;
import org.jeecg.modules.shop.product.entity.vo.StoreProductCardVO;
import org.jeecg.modules.shop.product.entity.vo.StoreProductDetailVO;
import org.jeecg.modules.shop.product.entity.vo.StoreSkuVO;
import org.jeecg.modules.shop.product.entity.vo.StoreSpecValueVO;
import org.jeecg.modules.shop.product.entity.vo.StoreSpecVO;
import org.jeecg.modules.shop.product.entity.vo.TagRefVO;
import org.jeecg.modules.shop.product.mapper.ShopProductImageMapper;
import org.jeecg.modules.shop.product.mapper.ShopProductMapper;
import org.jeecg.modules.shop.product.mapper.ShopProductSkuCombinationMapper;
import org.jeecg.modules.shop.product.mapper.ShopProductSkuCombinationValueMapper;
import org.jeecg.modules.shop.product.mapper.ShopProductSpecMapper;
import org.jeecg.modules.shop.product.mapper.ShopProductSpecValueMapper;
import org.jeecg.modules.shop.product.mapper.ShopProductTranslationMapper;
import org.jeecg.modules.shop.product.service.IShopProductService;
import org.jeecg.modules.shop.tag.entity.ShopTag;
import org.jeecg.modules.shop.tag.entity.ShopTagTranslation;
import org.jeecg.modules.shop.tag.mapper.ShopTagMapper;
import org.jeecg.modules.shop.tag.mapper.ShopTagTranslationMapper;
import org.jeecg.modules.shop.category.entity.ShopCategory;
import org.jeecg.modules.shop.category.mapper.ShopCategoryMapper;
import org.jeecg.modules.shop.product.entity.ShopProductStore;
import org.jeecg.modules.shop.product.entity.dto.StoreListingDTO;
import org.jeecg.modules.shop.product.entity.vo.StoreListingVO;
import org.jeecg.modules.shop.product.entity.vo.StoreProductRowVO;
import org.jeecg.modules.shop.product.mapper.ShopProductStoreMapper;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.jeecg.modules.shop.store.mapper.ShopStoreMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品 Service 实现
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Slf4j
@Service
public class ShopProductServiceImpl extends ServiceImpl<ShopProductMapper, ShopProduct> implements IShopProductService {

    private static final String DEFAULT_LANGUAGE = "zh";

    /** 与 ck_shop_product_sku_combination.combination_key 的列宽保持一致 */
    private static final int COMBINATION_KEY_MAX_LENGTH = 500;

    @Autowired
    private ShopProductTranslationMapper translationMapper;
    /** 商品-店铺上架关系（渠道属性） */
    @Autowired
    private ShopProductStoreMapper productStoreMapper;
    @Autowired
    private ShopStoreMapper shopStoreMapper;
    @Autowired
    private ShopCategoryMapper shopCategoryMapper;
    @Autowired
    private ShopProductImageMapper imageMapper;
    @Autowired
    private ShopProductSpecMapper specMapper;
    @Autowired
    private ShopProductSpecValueMapper specValueMapper;
    @Autowired
    private ShopProductSkuCombinationMapper combinationMapper;
    @Autowired
    private ShopProductSkuCombinationValueMapper combinationValueMapper;
    @Autowired
    private ShopTagMapper tagMapper;
    @Autowired
    private ShopTagTranslationMapper tagTranslationMapper;
    @Autowired
    private ShopCategoryMapper categoryMapper;
    @Autowired
    private ShopCategoryTranslationMapper categoryTranslationMapper;
    @Autowired
    private ShopBrandMapper brandMapper;

    // ==================================================================
    // 管理端
    // ==================================================================

    @Override
    public IPage<ProductListVO> queryPageList(ProductQuery query, Integer pageNo, Integer pageSize, String language) {
        String lang = normalizeLanguage(language);
        // 按店铺筛选时走连接查询：驱动表是 ck_shop_product_store，索引以 store_id 打头，
        // 分类也要按"本店分类优先、回退主分类"来匹配，因此不能沿用下面的全量商品 wrapper。
        if (query != null && StringUtils.isNotBlank(query.getStoreId())) {
            return queryAdminPageByStore(query, pageNo, pageSize, lang);
        }
        QueryWrapper<ShopProduct> wrapper = new QueryWrapper<>();
        if (query != null) {
            wrapper.eq(StringUtils.isNotBlank(query.getSpuCode()), "spu_code", query.getSpuCode());
            wrapper.eq(StringUtils.isNotBlank(query.getStatus()), "status", query.getStatus());
            wrapper.eq(StringUtils.isNotBlank(query.getCategoryId()), "category_id", query.getCategoryId());
            wrapper.eq(StringUtils.isNotBlank(query.getBrandId()), "brand_id", query.getBrandId());
            wrapper.eq(StringUtils.isNotBlank(query.getIsRecommend()), "is_recommend", query.getIsRecommend());
        }
        String keyword = query == null ? null : query.getKeyword();
        String tagId = query == null ? null : query.getTagId();
        if (StringUtils.isNotBlank(keyword)) {
            String kw = escapeSqlLike(keyword);
            wrapper.inSql("id", "SELECT product_id FROM ck_shop_product_translation WHERE title LIKE '%" + kw + "%'");
        }
        if (StringUtils.isNotBlank(tagId)) {
            List<String> productIds = baseMapper.selectProductIdsByTagId(tagId);
            if (CollectionUtils.isEmpty(productIds)) {
                return new Page<>(pageNo == null ? 1 : pageNo, pageSize == null ? 10 : pageSize);
            }
            wrapper.in("id", productIds);
        }
        wrapper.orderByDesc("sort_order").orderByDesc("create_time");

        Page<ShopProduct> page = new Page<>(pageNo == null ? 1 : pageNo, pageSize == null ? 10 : pageSize);
        IPage<ShopProduct> pageList = super.page(page, wrapper);

        List<ShopProduct> records = pageList.getRecords();
        List<String> ids = records.stream().map(ShopProduct::getId).collect(Collectors.toList());
        ListContext ctx = buildListContext(ids, lang);

        // 上架渠道：当前页一次性取回，列表直接展示"已上架 N 店"
        Map<String, List<StoreListingVO>> storeListings = buildStoreListingsByProductIds(ids);
        List<ProductListVO> voList = records.stream().map(item -> {
            ProductListVO vo = toProductListVO(item, ctx);
            vo.setStores(storeListings.getOrDefault(item.getId(), new ArrayList<>()));
            return vo;
        }).collect(Collectors.toList());
        Page<ProductListVO> result = new Page<>(pageList.getCurrent(), pageList.getSize(), pageList.getTotal());
        result.setRecords(voList);
        return result;
    }

    /**
     * 管理端：按店铺筛选商品分页。
     * 走 ck_shop_product_store 与 ck_shop_product 的连接查询，保证：
     * <ul>
     *   <li>分页在数据库完成（不会把整店商品加载进内存再切页）；</li>
     *   <li>分类按"本店分类优先、回退商品主分类"匹配，与店铺端列表保持一致；</li>
     *   <li>不会因为漏加店铺条件而把别店商品带出来——条件在 XML 里由 store_id 强制。</li>
     * </ul>
     */
    private IPage<ProductListVO> queryAdminPageByStore(ProductQuery query, Integer pageNo, Integer pageSize, String lang) {
        List<String> categoryIds = null;
        if (StringUtils.isNotBlank(query.getCategoryId())) {
            categoryIds = collectCategoryIds(query.getCategoryId());
        }
        Page<StoreProductRowVO> page = new Page<>(pageNo == null ? 1 : pageNo, pageSize == null ? 10 : pageSize);
        IPage<StoreProductRowVO> rowPage = productStoreMapper.selectAdminStoreProductPage(page,
                query.getStoreId(), query.getStatus(), categoryIds, query.getBrandId(),
                query.getSpuCode(), query.getTagId(), query.getKeyword());

        List<String> ids = rowPage.getRecords().stream().map(StoreProductRowVO::getProductId).collect(Collectors.toList());
        Map<String, ShopProduct> productById = ids.isEmpty()
                ? Collections.emptyMap()
                : this.listByIds(ids).stream()
                .collect(Collectors.toMap(ShopProduct::getId, Function.identity(), (a, b) -> a));
        // 按数据库返回的顺序还原
        List<ShopProduct> records = ids.stream().map(productById::get).filter(Objects::nonNull).collect(Collectors.toList());

        ListContext ctx = buildListContext(ids, lang);
        // 上架渠道：当前页一次性取回，列表直接展示"已上架 N 店"
        Map<String, List<StoreListingVO>> storeListings = buildStoreListingsByProductIds(ids);
        List<ProductListVO> voList = records.stream().map(item -> {
            ProductListVO vo = toProductListVO(item, ctx);
            vo.setStores(storeListings.getOrDefault(item.getId(), new ArrayList<>()));
            return vo;
        }).collect(Collectors.toList());
        Page<ProductListVO> result = new Page<>(rowPage.getCurrent(), rowPage.getSize(), rowPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    /**
     * 收集某个分类及其全部后代的 id。
     * 分类最多 3 级，一次把本店可见的分类读进内存逐层展开即可，无需递归 SQL
     * （MySQL 5.7 也没有 CTE）。
     */
    private List<String> collectCategoryIds(String categoryId) {
        List<ShopCategory> all = shopCategoryMapper.selectList(new LambdaQueryWrapper<ShopCategory>());
        List<String> result = new ArrayList<>();
        result.add(categoryId);
        Set<String> frontier = new HashSet<>();
        frontier.add(categoryId);
        while (!frontier.isEmpty()) {
            Set<String> next = new HashSet<>();
            for (ShopCategory category : all) {
                if (category.getParentId() != null && frontier.contains(category.getParentId())
                        && !result.contains(category.getId())) {
                    result.add(category.getId());
                    next.add(category.getId());
                }
            }
            frontier = next;
        }
        return result;
    }

    /**
     * 同步"销售渠道"（本商品上架到哪些店铺、每店的分类归属与售价）。
     *
     * <b>约定</b>：{@code stores == null} 表示本次**不修改**上架关系；
     * 传空数组表示"全部下架"。这个区分很重要——否则任何一次只改商品主体的保存
     * 都会把上架关系清空，而运营完全无感。
     *
     * 每条关系用 upsert 写入，一条语句同时覆盖"新增 / 更新 / 复活曾经下架的行"；
     * 本次没勾选的店铺只做逻辑下架，**绝不物理删除**。
     */
    private void syncStoreListings(String productId, List<StoreListingDTO> stores, String username, Date now) {
        if (stores == null) {
            return;
        }
        List<String> keepStoreIds = new ArrayList<>();
        int index = 0;
        for (StoreListingDTO item : stores) {
            if (item == null || StringUtils.isBlank(item.getStoreId())) {
                continue;
            }
            keepStoreIds.add(item.getStoreId());
            productStoreMapper.upsertListing(IdWorker.getIdStr(), productId, item.getStoreId(),
                    StringUtils.trimToNull(item.getCategoryId()),
                    item.getPriceOverride(),
                    StringUtils.defaultIfBlank(item.getStatus(), "Y"),
                    item.getSortOrder() == null ? index : item.getSortOrder(),
                    StringUtils.defaultIfBlank(item.getIsRecommend(), "N"),
                    username);
            index++;
        }
        productStoreMapper.softDeleteMissing(productId, keepStoreIds);
    }

    /**
     * 批量取"上架渠道"，供列表展示（一次查询搞定当前页所有商品，避免 N+1）。
     */
    private Map<String, List<StoreListingVO>> buildStoreListingsByProductIds(List<String> productIds) {
        if (CollectionUtils.isEmpty(productIds)) {
            return Collections.emptyMap();
        }
        List<ShopProductStore> listings = productStoreMapper.selectByProductIds(productIds);
        if (CollectionUtils.isEmpty(listings)) {
            return Collections.emptyMap();
        }
        Map<String, ShopStore> storeById = shopStoreMapper.selectList(new LambdaQueryWrapper<ShopStore>())
                .stream().collect(Collectors.toMap(ShopStore::getId, Function.identity(), (a, b) -> a));
        Map<String, List<StoreListingVO>> result = new HashMap<>();
        for (ShopProductStore item : listings) {
            StoreListingVO vo = new StoreListingVO();
            BeanUtils.copyProperties(item, vo);
            ShopStore store = storeById.get(item.getStoreId());
            if (store != null) {
                vo.setStoreCode(store.getCode());
                vo.setStoreName(store.getName());
            }
            result.computeIfAbsent(item.getProductId(), key -> new ArrayList<>()).add(vo);
        }
        return result;
    }

    /**
     * 商品的上架渠道详情（管理端回显 / 列表展示用）。
     */
    private List<StoreListingVO> buildStoreListings(String productId) {
        List<ShopProductStore> listings = productStoreMapper.selectByProductId(productId);
        if (CollectionUtils.isEmpty(listings)) {
            return new ArrayList<>();
        }
        Map<String, ShopStore> storeById = shopStoreMapper.selectList(new LambdaQueryWrapper<ShopStore>())
                .stream().collect(Collectors.toMap(ShopStore::getId, Function.identity(), (a, b) -> a));
        return listings.stream().map(item -> {
            StoreListingVO vo = new StoreListingVO();
            BeanUtils.copyProperties(item, vo);
            ShopStore store = storeById.get(item.getStoreId());
            if (store != null) {
                vo.setStoreCode(store.getCode());
                vo.setStoreName(store.getName());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveProduct(ProductDTO dto) {
        if (dto == null) {
            throw new JeecgBootException("商品数据不能为空!");
        }
        // 先按唯一约束预检，失败了给友好提示（比数据库报 DuplicateKeyException 更可读）
        checkSpuCodeUnique(dto.getSpuCode(), null);

        ShopProduct product = new ShopProduct();
        BeanUtils.copyProperties(dto, product);
        product.setId(IdWorker.getIdStr());
        product.setDelFlag(null);
        // 新增：缺什么补什么（含自动生成 spu_code）
        applyInsertDefaults(product);

        String username = ShopAuditUtils.currentUsername();
        Date now = new Date();
        product.setCreateBy(username).setCreateTime(now).setUpdateBy(username).setUpdateTime(now);

        try {
            super.save(product);
        } catch (DuplicateKeyException e) {
            throw new JeecgBootException("商品SPU编码已存在：" + product.getSpuCode());
        }
        saveChildren(product, dto, username, now);
        product.setDelFlag(null);
        super.updateById(product);
        // 返回新主键：前端"保存并继续编辑"需要它来回填，否则会把提示语当成 id 去查详情
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateProduct(ProductDTO dto) {
        if (dto == null) {
            throw new JeecgBootException("商品数据不能为空!");
        }
        if (StringUtils.isBlank(dto.getId())) {
            throw new JeecgBootException("未找到商品主键!");
        }
        ShopProduct exist = super.getById(dto.getId());
        if (Objects.isNull(exist)) {
            throw new JeecgBootException("商品不存在或已删除!");
        }
        checkSpuCodeUnique(dto.getSpuCode(), dto.getId());

        ShopProduct product = new ShopProduct();
        BeanUtils.copyProperties(dto, product);
        // 审计字段不覆盖
        product.setCreateBy(null).setCreateTime(null);
        product.setDelFlag(null);
        // 编辑采用"库中现值兜底"的部分更新语义：
        // 前端未提交的字段沿用原值，绝不重新生成 spu_code、也不把 price 写成 0
        applyUpdateDefaults(product, exist);

        String username = ShopAuditUtils.currentUsername();
        product.setUpdateBy(username).setUpdateTime(new Date());

        saveChildren(product, dto, username, new Date());
        product.setDelFlag(null);
        try {
            super.updateById(product);
        } catch (DuplicateKeyException e) {
            throw new JeecgBootException("商品SPU编码已存在：" + product.getSpuCode());
        }
        // 与新增一致，统一返回商品ID，避免前端把提示语当成 id
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeProduct(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("请选择要删除的商品!");
        }
        clearChildren(Collections.singletonList(id));
        super.removeById(id);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeProducts(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            throw new JeecgBootException("请至少选择一条数据!");
        }
        clearChildren(ids);
        super.removeByIds(ids);
        return true;
    }

    @Override
    public boolean updateStatus(String id, String status) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("请选择要操作的商品!");
        }
        if (!"Y".equals(status) && !"N".equals(status)) {
            throw new JeecgBootException("状态值不合法，只能为 Y 或 N!");
        }
        ShopProduct product = new ShopProduct();
        product.setId(id);
        product.setStatus(status);
        product.setUpdateBy(ShopAuditUtils.currentUsername());
        product.setUpdateTime(new Date());
        // updateById 返回 false 表示 id 不存在或已被软删除，不能对用户谎报成功
        if (!super.updateById(product)) {
            throw new JeecgBootException("商品不存在或已删除，操作失败!");
        }
        return true;
    }

    @Override
    public ProductDetailVO getDetailById(String id, String language) {
        ShopProduct product = super.getById(id);
        if (Objects.isNull(product)) {
            throw new JeecgBootException("未找到数据!");
        }
        // 与列表接口保持一致：按调用方传入的语言回退标题/分类名/品牌名
        String lang = normalizeLanguage(language);
        ProductDetailVO vo = new ProductDetailVO();
        BeanUtils.copyProperties(product, vo);

        List<ShopProductTranslation> translations = translationMapper.selectByProductId(id);
        vo.setTranslations(translations);
        vo.setTitle(pickTitle(translations, lang, product.getSpuCode()));
        ShopProductTranslation defaultTranslation = pickTranslation(translations, lang);
        vo.setCoverUrl(defaultTranslation != null && StringUtils.isNotBlank(defaultTranslation.getCoverUrl())
                ? defaultTranslation.getCoverUrl() : product.getMainImage());

        vo.setImages(imageMapper.selectByProductId(id));
        vo.setCategoryName(loadCategoryName(product.getCategoryId(), lang));
        vo.setBrandName(loadBrandName(product.getBrandId(), lang));

        List<String> tagIds = tagMapper.selectTagIdsByProductId(id);
        vo.setTagIds(tagIds);
        vo.setTags(loadTagRefs(tagIds, lang));

        vo.setSpecs(buildSpecTree(id));
        vo.setSkus(buildSkuVOList(combinationMapper.selectByProductIdWithValues(id), product.getPrice()));
        vo.setStores(buildStoreListings(id));
        return vo;
    }

    @Override
    public List<ProductSkuVO> listSkuByProductId(String productId) {
        if (StringUtils.isBlank(productId)) {
            return new ArrayList<>();
        }
        ShopProduct product = super.getById(productId);
        BigDecimal basePrice = product == null ? null : product.getPrice();
        return buildSkuVOList(combinationMapper.selectByProductIdWithValues(productId), basePrice);
    }

    // ==================================================================
    // 店铺端
    // ==================================================================

    @Override
    public IPage<StoreProductCardVO> queryStorePageList(String storeId, String language, String categoryId, String brandId,
                                                        String keyword, Integer pageNo, Integer pageSize) {
        String lang = normalizeLanguage(language);
        // 分类要按"本店分类优先、回退商品主分类"匹配，因此按 [该分类 + 其后代] 一起查
        List<String> categoryIds = StringUtils.isNotBlank(categoryId) ? collectCategoryIds(categoryId) : null;

        /*
         * 店铺端列表一律走 ck_shop_product_store 驱动：
         * 条件里强制 ps.store_id = ?，从根上避免"A 店商品出现在 B 店"。
         */
        Page<StoreProductRowVO> page = new Page<>(pageNo == null ? 1 : pageNo, pageSize == null ? 12 : pageSize);
        IPage<StoreProductRowVO> rowPage = productStoreMapper.selectStoreProductPage(
                page, storeId, categoryIds, brandId, keyword);

        List<String> ids = rowPage.getRecords().stream()
                .map(StoreProductRowVO::getProductId).collect(Collectors.toList());
        Map<String, BigDecimal> storePriceById = rowPage.getRecords().stream()
                .filter(row -> row.getStorePrice() != null)
                .collect(Collectors.toMap(StoreProductRowVO::getProductId, StoreProductRowVO::getStorePrice, (a, b) -> a));
        Map<String, ShopProduct> productById = ids.isEmpty()
                ? Collections.emptyMap()
                : this.listByIds(ids).stream()
                .collect(Collectors.toMap(ShopProduct::getId, Function.identity(), (a, b) -> a));
        List<ShopProduct> records = ids.stream().map(productById::get).filter(Objects::nonNull).collect(Collectors.toList());
        // 本店售价覆盖基准价（只影响没有单独定价的 SKU）
        records.forEach(item -> {
            BigDecimal override = storePriceById.get(item.getId());
            if (override != null) {
                item.setPrice(override);
            }
        });

        ListContext ctx = buildListContext(ids, lang);
        List<StoreProductCardVO> voList = records.stream()
                .map(item -> toStoreCardVO(item, ctx, ctx.combinations.getOrDefault(item.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
        Page<StoreProductCardVO> result = new Page<>(rowPage.getCurrent(), rowPage.getSize(), rowPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    @Override
    public StoreProductDetailVO getStoreDetail(String storeId, String idOrSpuCode, String language) {
        String lang = normalizeLanguage(language);
        // 前台路由使用 spu_code（分享到社交平台的链接更短、可读），因此主键与 spu_code 都支持
        ShopProduct product = super.getById(idOrSpuCode);
        if (Objects.isNull(product)) {
            product = baseMapper.selectAliveBySpuCode(idOrSpuCode);
        }
        if (Objects.isNull(product)) {
            throw new JeecgBootException("商品不存在或已下架!");
        }
        final String productId = product.getId();

        /*
         * 关键：必须校验"这件商品确实上架在当前店铺"。
         * 少了这一步，任何人换个 spu_code 就能看到别的店铺的商品详情——
         * 多店铺最典型的事故就是这一类"忘了带店铺条件"。
         */
        ShopProductStore listing = productStoreMapper.selectOne(new LambdaQueryWrapper<ShopProductStore>()
                .eq(ShopProductStore::getProductId, productId)
                .eq(ShopProductStore::getStoreId, storeId)
                .last("LIMIT 1"));
        if (listing == null || !"Y".equals(listing.getStatus())) {
            throw new JeecgBootException("商品在当前店铺不存在或已下架!");
        }
        // 与店铺端列表保持一致：商品总开关下架也不对外暴露详情
        if (!"Y".equals(product.getStatus())) {
            throw new JeecgBootException("商品已下架!");
        }
        /*
         * 本店售价：覆盖商品基准价。
         * 语义与现有价格回退逻辑一致——只影响"没有单独定价的 SKU"所用的基准价，
         * 单独定过价的 SKU 仍以自身价格为准。
         */
        if (listing.getPriceOverride() != null) {
            product.setPrice(listing.getPriceOverride());
        }
        // 分类归属：本店分类优先，为空才回退商品主分类
        String effectiveCategoryId = StringUtils.isNotBlank(listing.getCategoryId())
                ? listing.getCategoryId() : product.getCategoryId();

        StoreProductDetailVO vo = new StoreProductDetailVO();
        BeanUtils.copyProperties(product, vo);

        List<ShopProductTranslation> translations = translationMapper.selectByProductId(productId);
        ShopProductTranslation translation = pickTranslation(translations, lang);
        vo.setTitle(pickTitle(translations, lang, product.getSpuCode()));
        if (translation != null) {
            vo.setSubTitle(translation.getSubTitle());
            vo.setDescription(translation.getDescription());
            vo.setSeoTitle(translation.getSeoTitle());
            vo.setSeoKeywords(translation.getSeoKeywords());
            vo.setSeoDescription(translation.getSeoDescription());
            vo.setCoverUrl(StringUtils.isNotBlank(translation.getCoverUrl())
                    ? translation.getCoverUrl() : product.getMainImage());
        } else {
            vo.setCoverUrl(product.getMainImage());
        }
        vo.setCategoryName(loadCategoryName(effectiveCategoryId, lang));
        vo.setBrandName(loadBrandName(product.getBrandId(), lang));
        vo.setTags(loadTagRefs(tagMapper.selectTagIdsByProductId(productId), lang));
        vo.setImages(imageMapper.selectByProductId(productId));

        List<SpecDTO> specTree = buildSpecTree(productId);
        vo.setSpecs(toStoreSpecs(specTree));
        vo.setCombinations(buildStoreSkuList(
                combinationMapper.selectByProductIdWithValues(productId), product.getPrice(), specTree));
        return vo;
    }

    // ==================================================================
    // 子表写入
    // ==================================================================

    /**
     * 增量同步商品子表（**不做全量覆盖**）。
     *
     * 为什么不删了重建：
     * 1) 订单会关联 SKU 主键（ck_shop_product_sku_combination.id），删了重建会让历史订单指向失效；
     * 2) 每保存一次就产生一整批无用行，软删除的行只会越积越多。
     *
     * 因此：所有子表按"业务身份"匹配后 update / insert，只有本次确实被移除的行才删除；
     * 同时每秒回收一次"上一轮已软删除"的垃圾行。要点：
     * - 多语言：没有 del_flag 列，被移除的行物理删除；
     * - 组合：唯一索引是 (product_id, combination_key)。被移除的组合**软删除**（保住历史/订单引用），
     *   已软删除的行在下次保存开头统一物理回收，避免再次插入同样的 key 时撞唯一索引；
     * - 组合明细是纯内部关联数据，改写时物理删除后重建，不留垃圾。
     */
    private void saveChildren(ShopProduct product, ProductDTO dto, String username, Date now) {
        String productId = product.getId();

        /*
         * 注意：这里**刻意不做任何物理删除**。
         * 曾经在每次保存开头"回收上一轮软删除的行"（purge），后果是：
         * 用户删掉 SKU A 保存（del_flag=1）→ 再删 SKU B 保存 → A 被物理清掉。
         * 如果已有订单引用 A，这条 SKU 就永久无法追溯了。
         * 现在一律只做逻辑删除，历史行永久保留。
         */

        // ---------- 1. 多语言：按 languageCode 匹配 ----------
        List<ShopProductTranslation> existTranslations = translationMapper.selectByProductId(productId);
        Map<String, ShopProductTranslation> translationByLang = new HashMap<>();
        for (ShopProductTranslation t : existTranslations) {
            if (StringUtils.isNotBlank(t.getLanguageCode())) {
                translationByLang.put(t.getLanguageCode(), t);
            }
        }
        Set<String> keptLanguages = new HashSet<>();
        if (!CollectionUtils.isEmpty(dto.getTranslations())) {
            for (ShopProductTranslation incoming : dto.getTranslations()) {
                if (StringUtils.isBlank(incoming.getLanguageCode())) {
                    continue;
                }
                String lang = incoming.getLanguageCode();
                keptLanguages.add(lang);
                ShopProductTranslation exist = translationByLang.get(lang);
                if (exist != null) {
                    // 原地更新：同一 (product_id, language_code) 只保留一行
                    incoming.setId(exist.getId());
                    incoming.setProductId(productId);
                    incoming.setCreateBy(null).setCreateTime(null);
                    incoming.setUpdateBy(username).setUpdateTime(now);
                    translationMapper.updateById(incoming);
                } else {
                    incoming.setId(IdWorker.getIdStr());
                    incoming.setProductId(productId);
                    incoming.setCreateBy(username).setCreateTime(now);
                    incoming.setUpdateBy(username).setUpdateTime(now);
                    translationMapper.insert(incoming);
                }
            }
        }
        for (ShopProductTranslation exist : existTranslations) {
            if (!keptLanguages.contains(exist.getLanguageCode())) {
                translationMapper.deleteById(exist.getId());
            }
        }

        // ---------- 2. 图集：优先按主键匹配，其次按 URL 匹配 ----------
        List<ShopProductImage> existImages = imageMapper.selectByProductId(productId);
        Map<String, ShopProductImage> imageById = new HashMap<>();
        Map<String, ShopProductImage> imageByUrl = new HashMap<>();
        for (ShopProductImage img : existImages) {
            imageById.put(img.getId(), img);
            if (StringUtils.isNotBlank(img.getUrl())) {
                imageByUrl.putIfAbsent(img.getUrl(), img);
            }
        }
        Set<String> keptImageIds = new HashSet<>();
        int imageIndex = 0;
        if (!CollectionUtils.isEmpty(dto.getImages())) {
            for (ShopProductImage incoming : dto.getImages()) {
                if (StringUtils.isBlank(incoming.getUrl())) {
                    continue;
                }
                String url = incoming.getUrl().trim();
                ShopProductImage exist = StringUtils.isNotBlank(incoming.getId()) ? imageById.get(incoming.getId()) : null;
                if (exist == null) {
                    exist = imageByUrl.get(url);
                }
                String isMain = StringUtils.isBlank(incoming.getIsMain())
                        ? (imageIndex == 0 ? "Y" : "N") : incoming.getIsMain();
                int sortOrder = incoming.getSortOrder() == null ? imageIndex : incoming.getSortOrder();
                if (exist != null) {
                    keptImageIds.add(exist.getId());
                    ShopProductImage update = new ShopProductImage();
                    update.setId(exist.getId());
                    update.setUrl(url);
                    update.setIsMain(isMain);
                    update.setSortOrder(sortOrder);
                    update.setUpdateBy(username).setUpdateTime(now);
                    imageMapper.updateById(update);
                } else {
                    incoming.setId(IdWorker.getIdStr());
                    incoming.setProductId(productId);
                    incoming.setUrl(url);
                    incoming.setIsMain(isMain);
                    incoming.setSortOrder(sortOrder);
                    incoming.setDelFlag(null);
                    incoming.setCreateBy(username).setCreateTime(now);
                    incoming.setUpdateBy(username).setUpdateTime(now);
                    imageMapper.insert(incoming);
                    keptImageIds.add(incoming.getId());
                }
                imageIndex++;
            }
        }
        for (ShopProductImage exist : existImages) {
            if (!keptImageIds.contains(exist.getId())) {
                imageMapper.deleteById(exist.getId());
            }
        }

        // ---------- 3. 标签关联：按集合差异增删，不整体重建 ----------
        List<String> existTagIds = tagMapper.selectTagIdsByProductId(productId);
        Set<String> keepTagIds = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(dto.getTagIds())) {
            for (String tagId : dto.getTagIds()) {
                if (StringUtils.isNotBlank(tagId)) {
                    keepTagIds.add(tagId);
                }
            }
        }
        for (String tagId : keepTagIds) {
            if (!existTagIds.contains(tagId)) {
                tagMapper.insertProductTagRelation(IdWorker.getIdStr(), productId, tagId, username, now);
            }
        }
        for (String tagId : existTagIds) {
            if (!keepTagIds.contains(tagId)) {
                tagMapper.deleteProductRelation(productId, tagId);
            }
        }

        // ---------- 4. 规格 + 规格值：按主键/名称匹配，主键保持不变 ----------
        List<ShopProductSpec> existSpecs = specMapper.selectByProductId(productId);
        List<ShopProductSpecValue> existValues = specValueMapper.selectByProductId(productId);
        Map<String, ShopProductSpec> specById = new HashMap<>();
        Map<String, ShopProductSpec> specByName = new HashMap<>();
        for (ShopProductSpec s : existSpecs) {
            specById.put(s.getId(), s);
            if (StringUtils.isNotBlank(s.getName())) {
                specByName.putIfAbsent(s.getName(), s);
            }
        }
        Map<String, ShopProductSpecValue> valueById = new HashMap<>();
        Map<String, ShopProductSpecValue> valueBySpecAndText = new HashMap<>();
        for (ShopProductSpecValue v : existValues) {
            valueById.put(v.getId(), v);
            if (StringUtils.isNotBlank(v.getSpecId()) && StringUtils.isNotBlank(v.getValue())) {
                valueBySpecAndText.putIfAbsent(v.getSpecId() + "::" + v.getValue(), v);
            }
        }

        /** 组合引用解析表：新主键 / DTO 主键 / tempId / "规格名::值" 兜底形态 */
        Map<String, ShopProductSpecValue> savedValues = new LinkedHashMap<>();
        List<ShopProductSpec> savedSpecs = new ArrayList<>();
        Set<String> keptSpecIds = new HashSet<>();
        Set<String> keptValueIds = new HashSet<>();

        if (!CollectionUtils.isEmpty(dto.getSpecs())) {
            int specIndex = 0;
            for (SpecDTO specDTO : dto.getSpecs()) {
                List<SpecValueDTO> values = specDTO.getValues();
                if (CollectionUtils.isEmpty(values)) {
                    continue;
                }
                String specName = StringUtils.trimToEmpty(specDTO.getName());
                ShopProductSpec existSpec = StringUtils.isNotBlank(specDTO.getId()) ? specById.get(specDTO.getId()) : null;
                if (existSpec == null && StringUtils.isNotBlank(specName)) {
                    existSpec = specByName.get(specName);
                }

                ShopProductSpec spec = new ShopProductSpec();
                BeanUtils.copyProperties(specDTO, spec);
                spec.setProductId(productId);
                spec.setDelFlag(null);
                spec.setSortOrder(specDTO.getSortOrder() == null ? specIndex : specDTO.getSortOrder());
                if (StringUtils.isBlank(spec.getIsRequired())) {
                    spec.setIsRequired("Y");
                }
                spec.setUpdateBy(username).setUpdateTime(now);
                if (existSpec != null) {
                    spec.setId(existSpec.getId());
                    spec.setCreateBy(null).setCreateTime(null);
                    specMapper.updateById(spec);
                } else {
                    spec.setId(IdWorker.getIdStr());
                    spec.setCreateBy(username).setCreateTime(now);
                    specMapper.insert(spec);
                }
                savedSpecs.add(spec);
                keptSpecIds.add(spec.getId());

                int valueIndex = 0;
                for (SpecValueDTO valueDTO : values) {
                    String text = StringUtils.trimToNull(valueDTO.getValue());
                    if (text == null) {
                        continue;
                    }
                    ShopProductSpecValue existValue = StringUtils.isNotBlank(valueDTO.getId()) ? valueById.get(valueDTO.getId()) : null;
                    if (existValue == null) {
                        existValue = valueBySpecAndText.get(spec.getId() + "::" + text);
                    }

                    ShopProductSpecValue value = new ShopProductSpecValue();
                    value.setSpecId(spec.getId());
                    value.setProductId(productId);
                    value.setValue(text);
                    value.setDelFlag(null);
                    value.setSortOrder(valueDTO.getSortOrder() == null ? valueIndex : valueDTO.getSortOrder());
                    value.setUpdateBy(username).setUpdateTime(now);
                    if (existValue != null) {
                        // 关键：复用原主键，保证 SKU 组合键与已有组合行都不受影响
                        value.setId(existValue.getId());
                        value.setCreateBy(null).setCreateTime(null);
                        specValueMapper.updateById(value);
                    } else {
                        value.setId(IdWorker.getIdStr());
                        value.setCreateBy(username).setCreateTime(now);
                        specValueMapper.insert(value);
                    }
                    keptValueIds.add(value.getId());

                    savedValues.put(value.getId(), value);
                    if (StringUtils.isNotBlank(valueDTO.getId())) {
                        savedValues.putIfAbsent(valueDTO.getId(), value);
                    }
                    if (StringUtils.isNotBlank(valueDTO.getTempId())) {
                        savedValues.put(valueDTO.getTempId(), value);
                    }
                    savedValues.putIfAbsent(specName + "::" + text, value);
                    valueIndex++;
                }
                specIndex++;
            }
        }
        // 移除本次没出现的规格值 / 规格（先建完映射再删，避免解析不到引用）
        for (ShopProductSpecValue exist : existValues) {
            if (!keptValueIds.contains(exist.getId())) {
                specValueMapper.deleteById(exist.getId());
            }
        }
        for (ShopProductSpec exist : existSpecs) {
            if (!keptSpecIds.contains(exist.getId())) {
                specMapper.deleteById(exist.getId());
            }
        }

        // ---------- 5. SKU 组合：按主键或组合键匹配，主键保持不变 ----------
        // 这里连**已软删除**的行一起查出来：唯一索引 (product_id, combination_key) 不含 del_flag，
        // 软删行仍占着组合键，用户"删掉再加回来"时必须复活旧行而不是新插（否则撞唯一索引，
        // 且新插会导致 SKU 主键变化，订单会失去引用）。
        List<ShopProductSkuCombination> allCombos = combinationMapper.selectAllIncludingDeleted(productId);
        // 存活行由数据库直接过滤，不依赖实体字段映射
        Set<String> liveComboIds = new HashSet<>(combinationMapper.selectLiveIdsByProductId(productId));
        Map<String, ShopProductSkuCombination> comboById = new HashMap<>();
        Map<String, ShopProductSkuCombination> comboByKey = new HashMap<>();
        for (ShopProductSkuCombination c : allCombos) {
            comboById.putIfAbsent(c.getId(), c);
            if (StringUtils.isNotBlank(c.getCombinationKey())) {
                // 同一组合键优先取存活行，其次才取软删行
                ShopProductSkuCombination prev = comboByKey.get(c.getCombinationKey());
                if (prev == null || (!liveComboIds.contains(prev.getId()) && liveComboIds.contains(c.getId()))) {
                    comboByKey.put(c.getCombinationKey(), c);
                }
            }
        }
        Map<String, Set<String>> existComboValues = new HashMap<>();
        for (ShopProductSkuCombinationValue cv : combinationValueMapper.selectList(
                new LambdaQueryWrapper<ShopProductSkuCombinationValue>()
                        .eq(ShopProductSkuCombinationValue::getProductId, productId))) {
            existComboValues.computeIfAbsent(cv.getCombinationId(), k -> new HashSet<>()).add(cv.getSpecValueId());
        }

        Map<String, Integer> specOrderIndex = new HashMap<>();
        for (int i = 0; i < savedSpecs.size(); i++) {
            specOrderIndex.put(savedSpecs.get(i).getId(), i);
        }

        int stockSum = 0;
        BigDecimal min = null;
        BigDecimal max = null;
        Set<String> usedKeys = new HashSet<>();
        Set<String> keptComboIds = new HashSet<>();
        boolean hasCombinations = !CollectionUtils.isEmpty(dto.getCombinations());

        if (hasCombinations) {
            for (SkuCombinationDTO comboDTO : dto.getCombinations()) {
                List<String> refs = comboDTO.getSpecValueRefs();
                if (CollectionUtils.isEmpty(refs)) {
                    continue;
                }
                // 按规格顺序解析出规格值，每行必须恰好选到每个规格的一个值
                List<ShopProductSpecValue> resolved = new ArrayList<>();
                for (String ref : refs) {
                    ShopProductSpecValue value = savedValues.get(ref);
                    if (value == null) {
                        // 不能静默丢弃：静默会导致"保存成功但 SKU 为空"，用户完全无感
                        throw new JeecgBootException("SKU 组合引用了不存在的规格值：" + ref
                                + "，请回到「规格配置」页签重新生成组合");
                    }
                    if (!resolved.contains(value)) {
                        resolved.add(value);
                    }
                }
                if (resolved.isEmpty() || resolved.size() != savedSpecs.size() || refs.size() != savedSpecs.size()) {
                    log.warn("跳过非法SKU组合，商品={}，引用={}", productId, refs);
                    continue;
                }
                final Map<String, Integer> orderIndex = specOrderIndex;
                resolved.sort((a, b) -> Integer.compare(
                        orderIndex.getOrDefault(a.getSpecId(), Integer.MAX_VALUE),
                        orderIndex.getOrDefault(b.getSpecId(), Integer.MAX_VALUE)));

                StringBuilder keyBuilder = new StringBuilder();
                StringBuilder nameBuilder = new StringBuilder();
                for (ShopProductSpecValue value : resolved) {
                    if (keyBuilder.length() > 0) {
                        keyBuilder.append('-');
                        nameBuilder.append('/');
                    }
                    keyBuilder.append(value.getId());
                    nameBuilder.append(value.getValue());
                }
                String combinationKey = keyBuilder.toString();
                // combination_key 列是 varchar(500)：规格值主键 36 字符 + 连字符，
                // 规格数约到 14 个就会超长，MySQL 严格模式下报 Data too long（对用户完全不可读）。
                if (combinationKey.length() > COMBINATION_KEY_MAX_LENGTH) {
                    throw new JeecgBootException("规格数量过多导致组合键超长（" + combinationKey.length()
                            + " > " + COMBINATION_KEY_MAX_LENGTH + " 字符），请减少规格数量后再保存");
                }
                if (!usedKeys.add(combinationKey)) {
                    log.warn("跳过重复SKU组合，商品={}，key={}", productId, combinationKey);
                    continue;
                }

                /*
                 * 匹配顺序：**先用组合键，再用主键**。
                 *
                 * combination_key 才是 SKU 的业务身份（唯一索引就是 (product_id, combination_key)），
                 * 主键只是订单引用的存储标识。按组合键优先匹配可以顺带解决一个刁钻的冲突：
                 * 若某行的规格值被改成与"一条已软删除记录"完全相同的组合，
                 * 直接按主键去更新那一行会把它的组合键改成软删行占用的键 → 撞唯一索引报 Duplicate entry。
                 * 改成组合键优先后，会直接复活并复用那条已删记录（主键也随之保留），
                 * 原来的行则因不在 keptComboIds 中而被逻辑删除，不会产生任何冲突。
                 */
                ShopProductSkuCombination exist = comboByKey.get(combinationKey);
                if (exist == null && StringUtils.isNotBlank(comboDTO.getId())) {
                    exist = comboById.get(comboDTO.getId());
                }

                ShopProductSkuCombination combination = new ShopProductSkuCombination();
                combination.setProductId(productId);
                combination.setSkuCode(comboDTO.getSkuCode());
                combination.setCombinationKey(combinationKey);
                combination.setCombinationName(StringUtils.defaultIfBlank(
                        comboDTO.getCombinationName(), nameBuilder.toString()));
                combination.setPrice(comboDTO.getPrice());
                combination.setStock(comboDTO.getStock() == null ? 0 : comboDTO.getStock());
                combination.setImageUrl(comboDTO.getImageUrl());
                combination.setSaleable(StringUtils.defaultIfBlank(comboDTO.getSaleable(), "Y"));
                combination.setDelFlag(null);
                combination.setUpdateBy(username).setUpdateTime(now);
                if (exist != null) {
                    // 关键：SKU 主键保持不变，订单引用不会失效
                    combination.setId(exist.getId());
                    boolean wasDeleted = exist.getDelFlag() != null && exist.getDelFlag() != 0;
                    /*
                     * 无条件先"复活"：@TableLogic 会给下面的 update 追加 `AND del_flag = 0`，
                     * 若该组合曾被软删除（用户删掉后又加回来），不先清掉 del_flag 就永远更新不到。
                     * reviveById 是幂等的——行本来就未删除时影响 0 行，没有副作用，
                     * 因此不必先判断它是否已删除（也就少了一层对字段映射的依赖）。
                     */
                    combinationMapper.reviveById(exist.getId());
                    /*
                     * 用显式 set 更新，而不是 updateById：
                     * updateById 默认跳过 null 字段，会导致"把售价/配图清空"这类操作存不进去
                     * （清空后前端传 null，旧值反而被保留）。显式 set 才能把 null 真正写进库。
                     */
                    combinationMapper.update(null, new LambdaUpdateWrapper<ShopProductSkuCombination>()
                            .eq(ShopProductSkuCombination::getId, exist.getId())
                            .set(ShopProductSkuCombination::getSkuCode, combination.getSkuCode())
                            .set(ShopProductSkuCombination::getCombinationKey, combination.getCombinationKey())
                            .set(ShopProductSkuCombination::getCombinationName, combination.getCombinationName())
                            .set(ShopProductSkuCombination::getPrice, combination.getPrice())
                            .set(ShopProductSkuCombination::getStock, combination.getStock())
                            .set(ShopProductSkuCombination::getImageUrl, combination.getImageUrl())
                            .set(ShopProductSkuCombination::getSaleable, combination.getSaleable())
                            .set(ShopProductSkuCombination::getUpdateBy, username)
                            .set(ShopProductSkuCombination::getUpdateTime, now));
                } else {
                    combination.setId(IdWorker.getIdStr());
                    combination.setCreateBy(username).setCreateTime(now);
                    combinationMapper.insert(combination);
                }
                keptComboIds.add(combination.getId());

                // 明细只在规格值集合变化时改写，避免每次保存都产生一批新行
                Set<String> newValueIds = resolved.stream()
                        .map(ShopProductSpecValue::getId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                Set<String> oldValueIds = existComboValues.get(combination.getId());
                boolean valuesChanged = oldValueIds == null || !oldValueIds.equals(newValueIds);
                if (valuesChanged) {
                    // 逻辑删除旧明细（保留"这个 SKU 当时由哪些规格值组成"的历史），再写新明细
                    combinationValueMapper.softDeleteByCombinationId(combination.getId());
                    for (ShopProductSpecValue value : resolved) {
                        ShopProductSkuCombinationValue cv = new ShopProductSkuCombinationValue();
                        cv.setId(IdWorker.getIdStr());
                        cv.setCombinationId(combination.getId());
                        cv.setProductId(productId);
                        cv.setSpecId(value.getSpecId());
                        cv.setSpecValueId(value.getId());
                        cv.setDelFlag(null);
                        cv.setCreateBy(username).setCreateTime(now);
                        cv.setUpdateBy(username).setUpdateTime(now);
                        combinationValueMapper.insert(cv);
                    }
                }

                if ("Y".equals(combination.getSaleable())) {
                    stockSum += combination.getStock();
                    BigDecimal effective = combination.getPrice() != null ? combination.getPrice() : product.getPrice();
                    if (effective != null) {
                        if (min == null || effective.compareTo(min) < 0) {
                            min = effective;
                        }
                        if (max == null || effective.compareTo(max) > 0) {
                            max = effective;
                        }
                    }
                }
            }
        }

        /*
         * 本次被移除的组合：只做**逻辑删除**，永久保留历史行。
         * 绝不物理删除——订单会引用 SKU 主键，删掉就无法追溯了。
         * 行保留着还占着唯一索引 (product_id, combination_key)，用户日后把同一组合加回来时，
         * 上面的 reviveById 会把这行复活并原地更新，主键不变。
         */
        for (String liveComboId : liveComboIds) {
            if (!keptComboIds.contains(liveComboId)) {
                combinationMapper.deleteById(liveComboId);
            }
        }

        // ---------- 6. 销售渠道：上架到哪些店、每店分类与售价 ----------
        syncStoreListings(productId, dto.getStores(), username, now);

        // ---------- 7. 回写汇总字段 ----------
        if (hasCombinations) {
            BigDecimal basePrice = product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
            product.setStock(stockSum);
            product.setMinPrice(min != null ? min : basePrice);
            product.setMaxPrice(max != null ? max : basePrice);
        } else {
            product.setStock(0);
            product.setMinPrice(product.getPrice());
            product.setMaxPrice(product.getPrice());
        }
    }

    /**
     * 清理商品的全部子表数据（**只做逻辑删除，绝不物理删除**）。
     *
     * 为什么不再 purge：
     * 订单会引用 SKU 主键与规格信息，物理删除会让历史订单失去可追溯的关联数据。
     * 曾经每次保存都"回收上一轮软删除行"，导致用户删掉 A 保存、再删 B 保存时 A 被彻底清除，
     * 这是不可逆的数据损失。现在 del_flag=1 的行一律永久保留。
     *
     * 唯一的例外是 ck_shop_product_translation：该表**没有 del_flag 列**，
     * 且带唯一索引 (product_id, language_code)——若做逻辑删除，同名语言无法再次添加。
     * 文案不属于订单追溯所需的关联数据（订单会快照自己需要的文本），因此这里仍物理删除。
     */
    private void clearChildren(List<String> productIds) {
        List<String> ids = productIds.stream().filter(StringUtils::isNotBlank).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        translationMapper.deleteByProductIds(ids);
        imageMapper.delete(new LambdaQueryWrapper<ShopProductImage>().in(ShopProductImage::getProductId, ids));
        combinationValueMapper.delete(new LambdaQueryWrapper<ShopProductSkuCombinationValue>()
                .in(ShopProductSkuCombinationValue::getProductId, ids));
        combinationMapper.delete(new LambdaQueryWrapper<ShopProductSkuCombination>()
                .in(ShopProductSkuCombination::getProductId, ids));
        specValueMapper.delete(new LambdaQueryWrapper<ShopProductSpecValue>()
                .in(ShopProductSpecValue::getProductId, ids));
        specMapper.delete(new LambdaQueryWrapper<ShopProductSpec>().in(ShopProductSpec::getProductId, ids));
        tagMapper.softDeleteProductRelationsByProductIds(ids);
    }

    /** 新增时的默认值：缺失即补，含自动生成 spu_code */
    private void applyInsertDefaults(ShopProduct product) {
        if (StringUtils.isBlank(product.getStatus())) {
            product.setStatus("Y");
        }
        if (StringUtils.isBlank(product.getIsRecommend())) {
            product.setIsRecommend("N");
        }
        if (StringUtils.isBlank(product.getUnit())) {
            product.setUnit("件");
        }
        if (product.getSortOrder() == null) {
            product.setSortOrder(0);
        }
        if (product.getPrice() == null) {
            product.setPrice(BigDecimal.ZERO);
        }
        if (StringUtils.isBlank(product.getSpuCode())) {
            product.setSpuCode("SPU" + IdWorker.getIdStr());
        }
    }

    /**
     * 编辑时的默认值：一律用库中现值兜底，绝不重新生成 spu_code、也不把价格写成 0。
     * 这让编辑接口具备"部分更新"语义，前端漏传字段不会静默篡改业务数据。
     */
    private void applyUpdateDefaults(ShopProduct product, ShopProduct exist) {
        if (StringUtils.isBlank(product.getSpuCode())) {
            product.setSpuCode(exist.getSpuCode());
        }
        if (StringUtils.isBlank(product.getStatus())) {
            product.setStatus(exist.getStatus());
        }
        if (StringUtils.isBlank(product.getIsRecommend())) {
            product.setIsRecommend(exist.getIsRecommend());
        }
        if (StringUtils.isBlank(product.getUnit())) {
            product.setUnit(exist.getUnit());
        }
        if (product.getSortOrder() == null) {
            product.setSortOrder(exist.getSortOrder());
        }
        if (product.getPrice() == null) {
            product.setPrice(exist.getPrice());
        }
        if (StringUtils.isBlank(product.getMainImage())) {
            product.setMainImage(exist.getMainImage());
        }
        if (StringUtils.isBlank(product.getCategoryId())) {
            product.setCategoryId(exist.getCategoryId());
        }
        if (StringUtils.isBlank(product.getBrandId())) {
            product.setBrandId(exist.getBrandId());
        }
    }

    /**
     * SPU 唯一性预检。
     * 必须用原生 SQL 显式包含已软删除的行：表上是 uk_spu_code(spu_code, status)，
     * 唯一索引不区分 del_flag。若只查未删除行，软删后重建同一 SPU 会绕过校验、
     * 到 INSERT 时才抛 DuplicateKeyException（用户看到的是系统错误）。
     * 同时 LIMIT 1 避免历史上已存在多行同 SPU 时抛 TooManyResultsException。
     */
    private void checkSpuCodeUnique(String spuCode, String excludeId) {
        if (StringUtils.isBlank(spuCode)) {
            return;
        }
        ShopProduct exist = baseMapper.selectAnyBySpuCode(spuCode);
        if (exist != null && !exist.getId().equals(excludeId)) {
            throw new JeecgBootException("商品SPU编码已存在：" + spuCode);
        }
    }

    // ==================================================================
    // 查询装配
    // ==================================================================

    /**
     * 列表公共上下文，避免 N+1 查询
     */
    private static class ListContext {
        String language = DEFAULT_LANGUAGE;
        Map<String, List<ShopProductTranslation>> translations = new HashMap<>();
        Map<String, String> categoryNames = new HashMap<>();
        Map<String, String> brandNames = new HashMap<>();
        Map<String, TagRefVO> tagRefs = new HashMap<>();
        Map<String, List<String>> tagIdsByProduct = new HashMap<>();
        Map<String, List<ShopProductSkuCombination>> combinations = new HashMap<>();
    }

    private ListContext buildListContext(List<String> productIds, String language) {
        ListContext ctx = new ListContext();
        ctx.language = language;
        if (CollectionUtils.isEmpty(productIds)) {
            return ctx;
        }

        ctx.translations = translationMapper.selectByProductIds(productIds).stream()
                .collect(Collectors.groupingBy(ShopProductTranslation::getProductId));
        ctx.combinations = loadCombinationMap(productIds);

        // 标签
        List<Map<String, Object>> relations = tagMapper.selectProductTagRelations(productIds);
        Set<String> distinctTagIds = new LinkedHashSet<>();
        for (Map<String, Object> relation : relations) {
            String pid = String.valueOf(relation.get("product_id"));
            String tid = String.valueOf(relation.get("tag_id"));
            ctx.tagIdsByProduct.computeIfAbsent(pid, k -> new ArrayList<>()).add(tid);
            distinctTagIds.add(tid);
        }
        if (!distinctTagIds.isEmpty()) {
            Map<String, ShopTag> tagMap = tagMapper.selectBatchIds(distinctTagIds).stream()
                    .collect(Collectors.toMap(ShopTag::getId, Function.identity()));
            Map<String, String> tagNameMap = new HashMap<>();
            for (ShopTagTranslation tt : tagMapper.selectTranslationsByTagIds(distinctTagIds)) {
                if (language.equals(tt.getLanguageCode())) {
                    tagNameMap.put(tt.getTagId(), tt.getName());
                }
            }
            for (String tid : distinctTagIds) {
                ShopTag tag = tagMap.get(tid);
                if (tag == null) {
                    continue;
                }
                TagRefVO ref = new TagRefVO();
                ref.setId(tid);
                ref.setCode(tag.getCode());
                ref.setName(tagNameMap.containsKey(tid) ? tagNameMap.get(tid) : tag.getCode());
                ctx.tagRefs.put(tid, ref);
            }
        }

        // 分类名 / 品牌名（批量）
        Set<String> categoryIds = new LinkedHashSet<>();
        Set<String> brandIds = new LinkedHashSet<>();
        // 依赖 products 表，调用方已在 records 中持有，这里通过一次查询补齐
        List<ShopProduct> products = productIds.isEmpty() ? Collections.emptyList() : baseMapper.selectBatchIds(productIds);
        for (ShopProduct p : products) {
            if (StringUtils.isNotBlank(p.getCategoryId())) {
                categoryIds.add(p.getCategoryId());
            }
            if (StringUtils.isNotBlank(p.getBrandId())) {
                brandIds.add(p.getBrandId());
            }
        }
        if (!categoryIds.isEmpty()) {
            Map<String, String> names = new HashMap<>();
            for (ShopCategoryTranslation ct : categoryTranslationMapper.selectByCategoryIds(new ArrayList<>(categoryIds))) {
                if (language.equals(ct.getLanguageCode())) {
                    names.put(ct.getCategoryId(), ct.getName());
                }
            }
            Map<String, ShopCategory> categoryMap = categoryMapper.selectBatchIds(categoryIds).stream()
                    .collect(Collectors.toMap(ShopCategory::getId, Function.identity()));
            for (String cid : categoryIds) {
                ShopCategory category = categoryMap.get(cid);
                if (category != null) {
                    ctx.categoryNames.put(cid, names.containsKey(cid) ? names.get(cid) : category.getName());
                }
            }
        }
        if (!brandIds.isEmpty()) {
            Map<String, String> names = new HashMap<>();
            for (ShopBrandTranslation bt : brandMapper.selectTranslationsByBrandIds(new ArrayList<>(brandIds))) {
                if (language.equals(bt.getLanguageCode())) {
                    names.put(bt.getBrandId(), bt.getName());
                }
            }
            Map<String, ShopBrand> brandMap = brandMapper.selectBatchIds(brandIds).stream()
                    .collect(Collectors.toMap(ShopBrand::getId, Function.identity()));
            for (String bid : brandIds) {
                ShopBrand brand = brandMap.get(bid);
                if (brand != null) {
                    ctx.brandNames.put(bid, names.containsKey(bid) ? names.get(bid) : brand.getCode());
                }
            }
        }
        return ctx;
    }

    private Map<String, List<ShopProductSkuCombination>> loadCombinationMap(List<String> productIds) {
        if (CollectionUtils.isEmpty(productIds)) {
            return new HashMap<>();
        }
        return combinationMapper.selectByProductIdsWithValues(productIds).stream()
                .collect(Collectors.groupingBy(ShopProductSkuCombination::getProductId));
    }

    private ProductListVO toProductListVO(ShopProduct product, ListContext ctx) {
        ProductListVO vo = new ProductListVO();
        BeanUtils.copyProperties(product, vo);
        List<ShopProductTranslation> translations = ctx.translations.getOrDefault(product.getId(), Collections.emptyList());
        vo.setTitle(pickTitle(translations, ctx.language, product.getSpuCode()));
        ShopProductTranslation translation = pickTranslation(translations, ctx.language);
        vo.setCoverUrl(translation != null && StringUtils.isNotBlank(translation.getCoverUrl())
                ? translation.getCoverUrl() : product.getMainImage());
        vo.setCategoryName(ctx.categoryNames.get(product.getCategoryId()));
        vo.setBrandName(ctx.brandNames.get(product.getBrandId()));
        List<String> tagIds = ctx.tagIdsByProduct.getOrDefault(product.getId(), Collections.emptyList());
        List<TagRefVO> tags = tagIds.stream().map(ctx.tagRefs::get).filter(Objects::nonNull).collect(Collectors.toList());
        vo.setTags(tags);
        return vo;
    }

    private StoreProductCardVO toStoreCardVO(ShopProduct product, ListContext ctx,
                                             List<ShopProductSkuCombination> combinations) {
        StoreProductCardVO vo = new StoreProductCardVO();
        BeanUtils.copyProperties(product, vo);
        List<ShopProductTranslation> translations = ctx.translations.getOrDefault(product.getId(), Collections.emptyList());
        ShopProductTranslation translation = pickTranslation(translations, ctx.language);
        vo.setTitle(pickTitle(translations, ctx.language, product.getSpuCode()));
        if (translation != null) {
            vo.setSubTitle(translation.getSubTitle());
            vo.setCoverUrl(StringUtils.isNotBlank(translation.getCoverUrl())
                    ? translation.getCoverUrl() : product.getMainImage());
        } else {
            vo.setCoverUrl(product.getMainImage());
        }
        vo.setCategoryName(ctx.categoryNames.get(product.getCategoryId()));
        vo.setBrandName(ctx.brandNames.get(product.getBrandId()));
        List<String> tagIds = ctx.tagIdsByProduct.getOrDefault(product.getId(), Collections.emptyList());
        vo.setTags(tagIds.stream().map(ctx.tagRefs::get).filter(Objects::nonNull).collect(Collectors.toList()));

        // 库存取"可售组合"合计，避免把停售组合的库存展示出去
        int saleableStock = combinations.stream()
                .filter(c -> "Y".equals(c.getSaleable()) && c.getStock() != null)
                .mapToInt(ShopProductSkuCombination::getStock)
                .sum();
        vo.setStock(saleableStock);
        if (vo.getPrice() == null) {
            vo.setPrice(vo.getMinPrice() != null ? vo.getMinPrice() : BigDecimal.ZERO);
        }
        return vo;
    }

    private String pickTitle(List<ShopProductTranslation> translations, String language, String fallback) {
        ShopProductTranslation matched = pickTranslation(translations, language);
        if (matched != null && StringUtils.isNotBlank(matched.getTitle())) {
            return matched.getTitle();
        }
        if (!CollectionUtils.isEmpty(translations)) {
            for (ShopProductTranslation t : translations) {
                if (StringUtils.isNotBlank(t.getTitle())) {
                    return t.getTitle();
                }
            }
        }
        return fallback;
    }

    /**
     * 按语言取文案；语言未命中时回退到中文，再回退到第一条
     */
    private ShopProductTranslation pickTranslation(List<ShopProductTranslation> translations, String language) {
        if (CollectionUtils.isEmpty(translations)) {
            return null;
        }
        ShopProductTranslation matched = translations.stream()
                .filter(t -> language.equals(t.getLanguageCode()))
                .findFirst().orElse(null);
        if (matched != null) {
            return matched;
        }
        matched = translations.stream()
                .filter(t -> DEFAULT_LANGUAGE.equals(t.getLanguageCode()))
                .findFirst().orElse(null);
        return matched != null ? matched : translations.get(0);
    }

    private String loadCategoryName(String categoryId, String language) {
        if (StringUtils.isBlank(categoryId)) {
            return null;
        }
        ShopCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            return null;
        }
        for (ShopCategoryTranslation ct : categoryTranslationMapper.selectByCategoryId(categoryId)) {
            if (language.equals(ct.getLanguageCode()) && StringUtils.isNotBlank(ct.getName())) {
                return ct.getName();
            }
        }
        return category.getName();
    }

    private String loadBrandName(String brandId, String language) {
        if (StringUtils.isBlank(brandId)) {
            return null;
        }
        ShopBrand brand = brandMapper.selectById(brandId);
        if (brand == null) {
            return null;
        }
        for (ShopBrandTranslation bt : brandMapper.selectTranslationsByBrandId(brandId)) {
            if (language.equals(bt.getLanguageCode()) && StringUtils.isNotBlank(bt.getName())) {
                return bt.getName();
            }
        }
        return brand.getCode();
    }

    private List<TagRefVO> loadTagRefs(List<String> tagIds, String language) {
        if (CollectionUtils.isEmpty(tagIds)) {
            return new ArrayList<>();
        }
        Map<String, ShopTag> tagMap = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(ShopTag::getId, Function.identity()));
        Map<String, String> nameMap = new HashMap<>();
        for (ShopTagTranslation tt : tagMapper.selectTranslationsByTagIds(tagIds)) {
            if (language.equals(tt.getLanguageCode())) {
                nameMap.put(tt.getTagId(), tt.getName());
            }
        }
        List<TagRefVO> result = new ArrayList<>();
        for (String tid : tagIds) {
            ShopTag tag = tagMap.get(tid);
            if (tag == null) {
                continue;
            }
            TagRefVO ref = new TagRefVO();
            ref.setId(tid);
            ref.setCode(tag.getCode());
            ref.setName(nameMap.containsKey(tid) ? nameMap.get(tid) : tag.getCode());
            result.add(ref);
        }
        return result;
    }

    private List<SpecDTO> buildSpecTree(String productId) {
        List<ShopProductSpec> specs = specMapper.selectByProductId(productId);
        if (CollectionUtils.isEmpty(specs)) {
            return new ArrayList<>();
        }
        Map<String, List<ShopProductSpecValue>> valueMap = specValueMapper.selectByProductId(productId).stream()
                .collect(Collectors.groupingBy(ShopProductSpecValue::getSpecId));
        List<SpecDTO> result = new ArrayList<>();
        for (ShopProductSpec spec : specs) {
            SpecDTO dto = new SpecDTO();
            BeanUtils.copyProperties(spec, dto);
            List<SpecValueDTO> values = new ArrayList<>();
            for (ShopProductSpecValue value : valueMap.getOrDefault(spec.getId(), Collections.emptyList())) {
                SpecValueDTO valueDTO = new SpecValueDTO();
                BeanUtils.copyProperties(value, valueDTO);
                values.add(valueDTO);
            }
            dto.setValues(values);
            result.add(dto);
        }
        return result;
    }

    private List<ProductSkuVO> buildSkuVOList(List<ShopProductSkuCombination> combinations, BigDecimal basePrice) {
        List<ProductSkuVO> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(combinations)) {
            return result;
        }
        for (ShopProductSkuCombination combination : combinations) {
            ProductSkuVO vo = new ProductSkuVO();
            BeanUtils.copyProperties(combination, vo);
            vo.setEffectivePrice(combination.getPrice() != null ? combination.getPrice() : basePrice);
            result.add(vo);
        }
        return result;
    }

    private List<StoreSpecVO> toStoreSpecs(List<SpecDTO> specTree) {
        List<StoreSpecVO> result = new ArrayList<>();
        for (SpecDTO spec : specTree) {
            StoreSpecVO vo = new StoreSpecVO();
            BeanUtils.copyProperties(spec, vo);
            List<StoreSpecValueVO> values = new ArrayList<>();
            if (!CollectionUtils.isEmpty(spec.getValues())) {
                for (SpecValueDTO value : spec.getValues()) {
                    StoreSpecValueVO valueVO = new StoreSpecValueVO();
                    BeanUtils.copyProperties(value, valueVO);
                    values.add(valueVO);
                }
            }
            vo.setValues(values);
            result.add(vo);
        }
        return result;
    }

    /**
     * 店铺端组合列表：specValueIds 按规格顺序排列，与 specs 数组一一对应，
     * 便于前端用"按序拼接"的方式判断某个规格值是否还有可选组合。
     */
    private List<StoreSkuVO> buildStoreSkuList(List<ShopProductSkuCombination> combinations, BigDecimal basePrice,
                                               List<SpecDTO> specTree) {
        List<StoreSkuVO> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(combinations)) {
            return result;
        }
        Map<String, Integer> specOrder = new HashMap<>();
        for (int i = 0; i < specTree.size(); i++) {
            specOrder.put(specTree.get(i).getId(), i);
        }
        // 规格值ID → 所属规格ID
        Map<String, String> valueSpec = new HashMap<>();
        for (SpecDTO spec : specTree) {
            if (spec.getValues() == null) {
                continue;
            }
            for (SpecValueDTO value : spec.getValues()) {
                valueSpec.put(value.getId(), spec.getId());
            }
        }

        for (ShopProductSkuCombination combination : combinations) {
            StoreSkuVO vo = new StoreSkuVO();
            BeanUtils.copyProperties(combination, vo);
            vo.setEffectivePrice(combination.getPrice() != null ? combination.getPrice() : basePrice);
            List<String> ids = combination.getSpecValueIds() == null
                    ? new ArrayList<>() : new ArrayList<>(combination.getSpecValueIds());
            ids.sort((a, b) -> Integer.compare(
                    specOrder.getOrDefault(valueSpec.get(a), Integer.MAX_VALUE),
                    specOrder.getOrDefault(valueSpec.get(b), Integer.MAX_VALUE)));
            vo.setSpecValueIds(ids);
            result.add(vo);
        }
        return result;
    }

    private String normalizeLanguage(String language) {
        return StringUtils.isBlank(language) ? DEFAULT_LANGUAGE : language;
    }

    private String escapeSqlLike(String keyword) {
        return keyword.trim()
                .replace("\\", "\\\\")
                .replace("'", "''")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
