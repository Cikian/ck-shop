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
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
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
import java.util.Locale;
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
    /** 可见域/归属部门的唯一解析出口（运营只能管本部门，管理员不限） */
    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    // ==================================================================
    // 管理端
    // ==================================================================

    @Override
    public IPage<ProductListVO> queryPageList(ProductQuery query, Integer pageNo, Integer pageSize, String language) {
        String lang = normalizeLanguage(language);

        /*
         * 数据可见域（多店铺改造的核心）：
         *   1) storeId（店铺筛选）→ 该店铺绑定部门的 org_code；
         *   2) 与当前账号的可见部门域取交集。
         * 两种"空"的语义必须分清：
         *   - mergeScope 返回 null    → 不限（管理员且未按店铺筛选）；
         *   - mergeScope 返回空 List  → 两个域没有交集，直接返回空分页。
         * 交集为空时**绝不能把条件丢掉**——那就等于把全部店铺的数据返回给调用方。
         */
        String storeOrgCode = orgScopeResolver.resolveStoreOrgCode(query == null ? null : query.getStoreId());
        List<String> scope = orgScopeResolver.mergeScope(storeOrgCode, orgScopeResolver.resolveVisibleOrgCodes());
        if (scope != null && scope.isEmpty()) {
            return emptyListPage(pageNo, pageSize);
        }

        QueryWrapper<ShopProduct> wrapper = new QueryWrapper<>();
        applyOrgScope(wrapper, scope);
        if (query != null) {
            wrapper.eq(StringUtils.isNotBlank(query.getSpuCode()), "spu_code", query.getSpuCode());
            wrapper.eq(StringUtils.isNotBlank(query.getStatus()), "status", query.getStatus());
            wrapper.eq(StringUtils.isNotBlank(query.getCategoryId()), "category_id", query.getCategoryId());
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
                return emptyListPage(pageNo, pageSize);
            }
            wrapper.in("id", productIds);
        }
        if (query != null && StringUtils.isNotBlank(query.getCategoryId())) {
            // 与店铺端口径一致：按"该分类及其后代"筛选，否则运营按父分类筛选会漏掉子分类商品
            wrapper.in("category_id", collectCategoryIds(query.getCategoryId(), orgScopeResolver.resolveVisibleOrgCodes()));
        }
        // 与前台保持一致：sort_order 升序（数字越小越靠前）
        wrapper.orderByAsc("sort_order").orderByDesc("create_time");

        Page<ShopProduct> page = new Page<>(pageNo == null ? 1 : pageNo, pageSize == null ? 10 : pageSize);
        IPage<ShopProduct> pageList = super.page(page, wrapper);

        List<ShopProduct> records = pageList.getRecords();
        List<String> ids = records.stream().map(ShopProduct::getId).collect(Collectors.toList());
        ListContext ctx = buildListContext(ids, lang);

        List<ProductListVO> voList = records.stream()
                .map(item -> toProductListVO(item, ctx))
                .collect(Collectors.toList());
        Page<ProductListVO> result = new Page<>(pageList.getCurrent(), pageList.getSize(), pageList.getTotal());
        result.setRecords(voList);
        return result;
    }

    /**
     * 把可见域作用到商品查询条件上（实现收口在 ShopOrgScopeResolver，导出/列表共用同一套）。
     */
    private void applyOrgScope(QueryWrapper<ShopProduct> wrapper, List<String> scope) {
        orgScopeResolver.applyScope(wrapper, scope);
    }

    /** 空分页（可见域无交集 / 标签筛不出商品时用），保持与正常分页一致的 current/size */
    private Page<ProductListVO> emptyListPage(Integer pageNo, Integer pageSize) {
        Page<ProductListVO> empty = new Page<>(pageNo == null ? 1 : pageNo, pageSize == null ? 10 : pageSize, 0);
        empty.setRecords(new ArrayList<>());
        return empty;
    }

    /**
     * 收集某个分类及其全部后代的 id（可限定可见域）。
     * 分类最多 3 级，一次读进内存逐层展开即可，无需递归 SQL（MySQL 5.7 也没有 CTE）。
     *
     * @param scope 可见域前缀列表；{@code null} 表示不限（总部）
     */
    private List<String> collectCategoryIds(String categoryId, List<String> scope) {
        // 全表加载一次（分类表很小），再按可见域过滤：以前没有任何过滤，
        // 店铺端每次带 categoryId 的请求都会把所有店铺的分类都读进来
        List<ShopCategory> all = shopCategoryMapper.selectList(new LambdaQueryWrapper<ShopCategory>());
        List<String> result = new ArrayList<>();
        result.add(categoryId);
        Set<String> frontier = new HashSet<>();
        frontier.add(categoryId);
        while (!frontier.isEmpty()) {
            Set<String> next = new HashSet<>();
            for (ShopCategory category : all) {
                if (!orgScopeResolver.inScope(category.getSysOrgCode(), scope)) {
                    continue;
                }
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveProduct(ProductDTO dto) {
        if (dto == null) {
            throw new JeecgBootException("商品数据不能为空!");
        }
        // 归属部门：新增时默认取操作人当前部门；管理员可以显式指定。
        // 只有先定下归属，才能做"按部门唯一"的 SPU 预检。
        String orgCode = orgScopeResolver.resolveWritableOrgCode(dto.getSysOrgCode(), null);
        // 先按唯一约束预检，失败了给友好提示（比数据库报 DuplicateKeyException 更可读）
        checkSpuCodeUnique(dto.getSpuCode(), orgCode, null);

        ShopProduct product = new ShopProduct();
        BeanUtils.copyProperties(dto, product);
        product.setId(IdWorker.getIdStr());
        product.setDelFlag(null);
        // 显式写入归属（非空时 MybatisInterceptor 不会再覆盖），保证与上面的校验结果一致
        product.setSysOrgCode(orgCode);
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
        // 越权防线：接口可以被直接按 id 调用，列表过滤不算数
        orgScopeResolver.checkAccessible(exist.getSysOrgCode(), "商品");
        /*
         * 归属部门：运营不允许改。
         * resolveWritableOrgCode 在"请求归属 ≠ 当前部门"时直接抛错——
         * 这是后端硬校验，前端把下拉置灰只是体验层面的配合。
         */
        String orgCode = orgScopeResolver.resolveWritableOrgCode(dto.getSysOrgCode(), exist.getSysOrgCode());
        checkSpuCodeUnique(dto.getSpuCode(), orgCode, dto.getId());

        ShopProduct product = new ShopProduct();
        BeanUtils.copyProperties(dto, product);
        // 审计字段不覆盖
        product.setCreateBy(null).setCreateTime(null);
        product.setDelFlag(null);
        // 归属以服务端解析结果为准，忽略请求里的原始值
        product.setSysOrgCode(orgCode);
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
        loadAccessible(id);
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
        List<String> distinctIds = ids.stream().filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
        // 与单条删除口径一致：有 id 不存在就直接报错，不做"部分成功"（否则前端以为全删了）
        List<ShopProduct> found = this.listByIds(distinctIds);
        if (found.size() < distinctIds.size()) {
            Set<String> foundIds = found.stream().map(ShopProduct::getId).collect(Collectors.toSet());
            List<String> missing = distinctIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
            throw new JeecgBootException("部分商品不存在或已删除：" + String.join(",", missing));
        }
        // 批量删除同样要逐条校验越权：listByIds 已自动排除软删除行
        for (ShopProduct item : found) {
            orgScopeResolver.checkAccessible(item.getSysOrgCode(), "商品");
        }
        clearChildren(distinctIds);
        super.removeByIds(distinctIds);
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
        loadAccessible(id);
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

    /**
     * 按 id 取商品并做越权校验。
     * 所有"按 id 操作"的入口都必须走这里——列表已经过滤了，但接口是可以被直接按 id 调的。
     */
    private ShopProduct loadAccessible(String id) {
        ShopProduct product = super.getById(id);
        if (Objects.isNull(product)) {
            throw new JeecgBootException("商品不存在或已删除!");
        }
        orgScopeResolver.checkAccessible(product.getSysOrgCode(), "商品");
        return product;
    }

    @Override
    public ProductDetailVO getDetailById(String id, String language) {
        ShopProduct product = loadAccessible(id);
        // 与列表接口保持一致：按调用方传入的语言回退标题/分类名
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

        List<String> tagIds = tagMapper.selectTagIdsByProductId(id);
        vo.setTagIds(tagIds);
        vo.setTags(loadTagRefs(tagIds, lang));

        vo.setSpecs(buildSpecTree(id));
        vo.setSkus(buildSkuVOList(combinationMapper.selectByProductIdWithValues(id), product.getPrice()));
        return vo;
    }

    @Override
    public List<ProductSkuVO> listSkuByProductId(String productId) {
        if (StringUtils.isBlank(productId)) {
            return new ArrayList<>();
        }
        ShopProduct product = loadAccessible(productId);
        BigDecimal basePrice = product == null ? null : product.getPrice();
        return buildSkuVOList(combinationMapper.selectByProductIdWithValues(productId), basePrice);
    }

    // ==================================================================
    // 店铺端
    // ==================================================================

    @Override
    public IPage<StoreProductCardVO> queryStorePageList(String orgCode, String language, String categoryId,
                                                        String keyword, Integer pageNo, Integer pageSize, String sortBy) {
        String lang = normalizeLanguage(language);
        if (StringUtils.isBlank(orgCode)) {
            // 解析不到店铺归属部门时报错，**绝不退化成"返回全量"**——那就是 A 店展示 B 店商品
            throw new JeecgBootException("无法确定店铺归属部门，已拒绝查询");
        }
        // 分类筛选：按 [该分类 + 其后代] 一起查（分类树最多 3 级，内存展开即可）
        List<String> categoryIds = StringUtils.isNotBlank(categoryId)
                ? collectCategoryIds(categoryId, Collections.singletonList(orgCode)) : null;

        /*
         * 直接以归属列为驱动条件查商品表：
         *   sys_org_code LIKE 'orgCode%' —— 前缀式编码天然实现"本部门及其下级部门"；
         *   status='Y'                   —— 与店铺端"只看已上架"的口径一致。
         * 条件由 wrapper 强制带上，从根上避免"漏了店铺条件"。
         */
        QueryWrapper<ShopProduct> wrapper = new QueryWrapper<>();
        wrapper.likeRight("sys_org_code", orgCode);
        wrapper.eq("status", "Y");
        if (!CollectionUtils.isEmpty(categoryIds)) {
            wrapper.in("category_id", categoryIds);
        }
        if (StringUtils.isNotBlank(keyword)) {
            String kw = escapeSqlLike(keyword);
            wrapper.inSql("id", "SELECT product_id FROM ck_shop_product_translation WHERE title LIKE '%" + kw + "%'");
        }
        // 排序：只按白名单映射到固定列，未知取值回落到原有默认排序（详见 applyStoreSort）
        applyStoreSort(wrapper, sortBy);

        Page<ShopProduct> page = new Page<>(pageNo == null ? 1 : pageNo, pageSize == null ? 12 : pageSize);
        IPage<ShopProduct> productPage = super.page(page, wrapper);

        List<ShopProduct> records = productPage.getRecords();
        List<String> ids = records.stream().map(ShopProduct::getId).collect(Collectors.toList());
        ListContext ctx = buildListContext(ids, lang);
        List<StoreProductCardVO> voList = records.stream()
                .map(item -> toStoreCardVO(item, ctx, ctx.combinations.getOrDefault(item.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
        Page<StoreProductCardVO> result = new Page<>(productPage.getCurrent(), productPage.getSize(), productPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    /**
     * 店铺端：按商品主键取单张卡片，并完成可见性校验（收藏等写操作的统一准入判断）。
     *
     * <p>与 {@link #getStoreDetail} 的区别是**只做准入 + 卡片装配**，不查规格树与 SKU 组合：
     * 收藏一件商品只需要知道"它在不在这个店铺里、上没上架"，
     * 走详情接口等于每次收藏都白白多查几张子表。
     */
    @Override
    public StoreProductCardVO getStoreCard(String orgCode, String productId, String language) {
        if (StringUtils.isBlank(orgCode)) {
            // 解析不到店铺归属就报错，绝不退化成"任意店铺都能收藏"——那就是跨店写入
            throw new JeecgBootException("无法确定店铺归属部门，已拒绝操作");
        }
        if (StringUtils.isBlank(productId)) {
            throw new JeecgBootException("缺少商品 ID");
        }
        // getById 走 MP 的逻辑删除：已软删除的商品直接查不到
        ShopProduct product = super.getById(productId);
        if (product == null || !matchesOrg(product.getSysOrgCode(), orgCode)) {
            throw new JeecgBootException("商品在当前店铺不存在或已下架!");
        }
        if (!"Y".equals(product.getStatus())) {
            throw new JeecgBootException("商品已下架!");
        }
        ListContext ctx = buildListContext(Collections.singletonList(product.getId()), normalizeLanguage(language));
        return toStoreCardVO(product, ctx, ctx.combinations.getOrDefault(product.getId(), Collections.emptyList()));
    }

    /**
     * 店铺端：按主键批量取卡片（我的收藏列表用）。
     *
     * <p>过滤条件刻意与 {@link #queryStorePageList} 保持一致（可见部门 + 上架 + 未软删除），
     * 这样"收藏了但商品已下架"的条目会自动从收藏列表消失，
     * 而不是留在列表里、点进去才报错。
     */
    @Override
    public List<StoreProductCardVO> listStoreCardsByIds(List<String> productIds, String orgCode, String language) {
        if (CollectionUtils.isEmpty(productIds)) {
            return new ArrayList<>();
        }
        if (StringUtils.isBlank(orgCode)) {
            throw new JeecgBootException("无法确定店铺归属部门，已拒绝查询");
        }
        List<String> ids = productIds.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        String lang = normalizeLanguage(language);
        QueryWrapper<ShopProduct> wrapper = new QueryWrapper<>();
        wrapper.in("id", ids);
        wrapper.likeRight("sys_org_code", orgCode);
        wrapper.eq("status", "Y");
        List<ShopProduct> products = super.list(wrapper);
        ListContext ctx = buildListContext(
                products.stream().map(ShopProduct::getId).collect(Collectors.toList()), lang);
        return products.stream()
                .map(item -> toStoreCardVO(item, ctx, ctx.combinations.getOrDefault(item.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    /**
     * 店铺端列表排序白名单。
     *
     * <p>只把 sortBy 当作"枚举键"来匹配，命中后写入的是**代码里硬编码的列名**，
     * 任何未知 / 空 / 恶意取值都回落到默认排序，因此不存在 SQL 注入面。
     * 大小写与首尾空格会被归一化（如 "Sales " → sales）。
     *
     * @param sortBy 前端传入的排序标识：sales / newest / price_asc / price_desc，其余走默认
     */
    private void applyStoreSort(QueryWrapper<ShopProduct> wrapper, String sortBy) {
        String sort = sortBy == null ? "" : sortBy.trim().toLowerCase(Locale.ROOT);
        switch (sort) {
            case "sales":
                // 近30天销量优先，其次累计销量，最后按运营排序号兜底，保证同销量商品顺序稳定
                wrapper.orderByDesc("month_sales_count").orderByDesc("sales_count").orderByAsc("sort_order");
                break;
            case "newest":
                // MySQL 中 NULL 视为最小值，DESC 时天然排在最后，listed_at 为 NULL 的商品不会抢占头部
                wrapper.orderByDesc("listed_at").orderByDesc("create_time");
                break;
            case "price_asc":
                wrapper.orderByAsc("min_price");
                break;
            case "price_desc":
                wrapper.orderByDesc("min_price");
                break;
            default:
                // 原有默认排序，保持不变
                wrapper.orderByDesc("is_recommend").orderByAsc("sort_order").orderByDesc("create_time");
                break;
        }
    }

    @Override
    public StoreProductDetailVO getStoreDetail(String orgCode, String idOrSpuCode, String language) {
        String lang = normalizeLanguage(language);
        if (StringUtils.isBlank(orgCode)) {
            throw new JeecgBootException("无法确定店铺归属部门，已拒绝查询");
        }
        // 前台路由使用 spu_code（分享到社交平台的链接更短、可读），因此主键与 spu_code 都支持
        ShopProduct product = super.getById(idOrSpuCode);
        if (Objects.isNull(product)) {
            // spu_code 只在部门内唯一：必须带上归属部门一起查，否则会命中别店的同码商品
            product = baseMapper.selectAliveBySpuCode(idOrSpuCode, orgCode);
        }
        if (Objects.isNull(product)) {
            throw new JeecgBootException("商品不存在或已下架!");
        }
        /*
         * 关键：必须校验"这件商品确实属于当前店铺（部门）"。
         * 少了这一步，任何人换个 spu_code / 商品 id 就能看到别店的商品详情——
         * 多店铺最典型的事故就是这一类"忘了带店铺条件"。
         */
        if (!matchesOrg(product.getSysOrgCode(), orgCode)) {
            throw new JeecgBootException("商品在当前店铺不存在或已下架!");
        }
        // 与店铺端列表保持一致：商品总开关下架也不对外暴露详情
        if (!"Y".equals(product.getStatus())) {
            throw new JeecgBootException("商品已下架!");
        }
        final String productId = product.getId();
        // 分类归属就是商品自身的分类：一商品只属于一个店铺，不再有"本店分类覆盖"这一层
        String effectiveCategoryId = product.getCategoryId();

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
        vo.setTags(loadTagRefs(tagMapper.selectTagIdsByProductId(productId), lang));
        vo.setImages(imageMapper.selectByProductId(productId));

        List<SpecDTO> specTree = buildSpecTree(productId);
        vo.setSpecs(toStoreSpecs(specTree));
        vo.setCombinations(buildStoreSkuList(
                combinationMapper.selectByProductIdWithValues(productId), product.getPrice(), specTree));
        return vo;
    }

    /**
     * 商品归属部门是否落在店铺（部门）可见域内。
     * 与后台一致地采用前缀语义："店铺绑定部门"及其下级部门的商品，前台都能看到，
     * 避免"运营把商品挂到子部门后，前台反而什么都看不到"的诡异现象。
     */
    private boolean matchesOrg(String productOrgCode, String storeOrgCode) {
        return StringUtils.isNotBlank(productOrgCode)
                && StringUtils.isNotBlank(storeOrgCode)
                && productOrgCode.startsWith(storeOrgCode);
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

        // ---------- 6. 回写汇总字段 ----------
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
        // 归属部门不在这里兜底：它由 orgScopeResolver 显式解析后写入，
        // 若在这里再用库中现值覆盖，反而会把"管理员跨部门调整"的结果吞掉。
    }

    /**
     * SPU 唯一性预检（**按部门**）。
     *
     * 唯一索引是 {@code uk_spu_code(sys_org_code, spu_code)}：spu_code 只在部门内唯一，
     * 不同店铺可以有相同编码——前台路由的店铺身份来自域名 / X-Store-Code，不靠 spu_code 区分。
     * 必须用原生 SQL 显式包含已软删除的行（索引不区分 del_flag），
     * 否则软删后重建同一 SPU 会绕过校验、到 INSERT 时才抛 DuplicateKeyException（用户看到的是系统错误）。
     * 同时 LIMIT 1 避免历史上已存在多行同 SPU 时抛 TooManyResultsException。
     */
    private void checkSpuCodeUnique(String spuCode, String orgCode, String excludeId) {
        if (StringUtils.isBlank(spuCode)) {
            return;
        }
        if (StringUtils.isBlank(orgCode)) {
            throw new JeecgBootException("无法确定商品归属部门，已拒绝保存");
        }
        ShopProduct exist = baseMapper.selectAnyBySpuCode(spuCode, orgCode);
        if (exist != null && !exist.getId().equals(excludeId)) {
            if (exist.getDelFlag() != null && exist.getDelFlag() == 1) {
                // spu_code 是前台路由标识，禁止回收复用（否则已分享出去的旧链接会指向另一个商品）。
                // 命中软删行时给一条能解释清楚的提示，而不是干巴巴的"编码已存在"。
                throw new JeecgBootException("本店铺内曾用过商品SPU编码[" + spuCode + "]，该编码已被删除、按规则不回收复用"
                        + "（分享出去的链接必须永远指向同一个商品）。请换一个编码");
            }
            throw new JeecgBootException("本店铺内商品SPU编码已存在：" + spuCode + "（各店铺之间可以重复）");
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

        // 分类名（批量）
        Set<String> categoryIds = new LinkedHashSet<>();
        // 依赖 products 表，调用方已在 records 中持有，这里通过一次查询补齐
        List<ShopProduct> products = productIds.isEmpty() ? Collections.emptyList() : baseMapper.selectBatchIds(productIds);
        for (ShopProduct p : products) {
            if (StringUtils.isNotBlank(p.getCategoryId())) {
                categoryIds.add(p.getCategoryId());
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

        // P0 电商字段：BeanUtils 的浅拷贝对同名同型字段已经生效，
        // 这里显式再写一遍是为了让"哪些字段要透出到店铺端"这件事在代码里可见，
        // 而不是藏在一次隐式拷贝里（将来实体加字段却忘了透出时，这里会立刻暴露）。
        vo.setOriginalPrice(product.getOriginalPrice());
        vo.setSalesCount(product.getSalesCount());
        vo.setMonthSalesCount(product.getMonthSalesCount());
        vo.setReviewCount(product.getReviewCount());
        vo.setRatingAvg(product.getRatingAvg());
        vo.setFavoriteCount(product.getFavoriteCount());
        vo.setViewCount(product.getViewCount());
        // 实体与 VO 都是 Boolean（见 ShopProduct.freeShipping 的注释）：
        // NULL 归一成 false，前端就只需要处理"是/否"两种情况。
        vo.setFreeShipping(Boolean.TRUE.equals(product.getFreeShipping()));
        vo.setListedAt(product.getListedAt());
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
