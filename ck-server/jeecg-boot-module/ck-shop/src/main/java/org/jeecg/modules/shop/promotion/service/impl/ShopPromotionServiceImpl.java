package org.jeecg.modules.shop.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.common.util.ShopAuditUtils;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.ShopProductTranslation;
import org.jeecg.modules.shop.product.mapper.ShopProductMapper;
import org.jeecg.modules.shop.promotion.entity.ShopPromotion;
import org.jeecg.modules.shop.promotion.entity.ShopPromotionProduct;
import org.jeecg.modules.shop.promotion.entity.ShopPromotionTranslation;
import org.jeecg.modules.shop.promotion.entity.dto.PromotionConflictRow;
import org.jeecg.modules.shop.promotion.entity.dto.PromotionDTO;
import org.jeecg.modules.shop.promotion.entity.dto.PromotionQuery;
import org.jeecg.modules.shop.promotion.entity.vo.PromoBriefVO;
import org.jeecg.modules.shop.promotion.entity.vo.PromotionConflictVO;
import org.jeecg.modules.shop.promotion.entity.vo.PromotionListVO;
import org.jeecg.modules.shop.promotion.mapper.ShopPromotionMapper;
import org.jeecg.modules.shop.promotion.mapper.ShopPromotionProductMapper;
import org.jeecg.modules.shop.promotion.mapper.ShopPromotionTranslationMapper;
import org.jeecg.modules.shop.promotion.service.IShopPromotionService;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.jeecg.modules.shop.store.mapper.ShopStoreMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
import java.util.stream.Collectors;

/**
 * <p>
 * 促销活动 Service 实现
 * </p>
 *
 * <p>三条贯穿本类的纪律：
 * <ol>
 *   <li><b>店铺隔离</b>：活动挂在 {@code store_id} 上（不是 {@code sys_org_code}），
 *       所以可见域要通过"店铺绑定部门的 org_code"映射成店铺 id 列表，
 *       映射不到就是"没有可见店铺"，返回空结果，**绝不退化成不过滤**；</li>
 *   <li><b>空值语义</b>：{@code promoPrice} 为空 = 按活动类型自动算，不是 0 元；
 *       主表 {@code bg_image} 为空 = 前台用品牌色兜底。所有可空字段保持 null 落库；</li>
 *   <li><b>关联商品必须属于本店</b>：复用 {@code ShopOrgScopeResolver} 的
 *       "店铺 id → 部门 org_code → 前缀匹配商品 sys_org_code"这条路，
 *       与前台商品可见性完全一致，不另造一套判定。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-13
 */

@Slf4j
@Service
public class ShopPromotionServiceImpl extends ServiceImpl<ShopPromotionMapper, ShopPromotion>
        implements IShopPromotionService {

    /** 首页促销条最多返回条数（契约第 4 节：前台轮播多条，不能无限） */
    private static final int MAX_ACTIVE_PROMOTIONS = 10;

    /** 活动状态：启用 */
    private static final String STATUS_ENABLED = "Y";

    /** 活动状态：停用 */
    private static final String STATUS_DISABLED = "N";

    /** 多语言兜底语言：契约要求 translations 必须含 zh */
    private static final String DEFAULT_LANGUAGE = "zh";

    /** 复制活动的编码后缀时间格式 */
    private static final String COPY_CODE_TIME_PATTERN = "yyyyMMddHHmmss";

    /** 复制活动的名称后缀（契约第 3 节：方便在列表里区分副本） */
    private static final String COPY_TITLE_SUFFIX = "(副本)";

    /** 合法活动类型 */
    private static final Set<String> VALID_TYPES =
            new HashSet<>(java.util.Arrays.asList("DIRECT", "PERCENT", "FULL_CUT", "SECKILL"));

    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
    @Autowired
    private ShopPromotionSupport promotionSupport;


    @Autowired
    private ShopPromotionProductMapper promotionProductMapper;

    @Autowired
    private ShopPromotionTranslationMapper translationMapper;

    @Autowired
    private ShopStoreMapper shopStoreMapper;

    @Autowired
    private ShopProductMapper shopProductMapper;

    /** 可见域/归属部门的唯一解析出口（运营只能管本部门，管理员不限） */
    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    // ==================================================================
    // 店铺端
    // ==================================================================

    @Override
    public List<PromoBriefVO> listActiveByStore(String storeId, String lang) {
        if (StringUtils.isBlank(storeId)) {
            // fail-closed：拿不到店铺就返回空列表，绝不退化成"不过滤店铺"把全量活动发出去
            log.warn("查询进行中的促销活动时店铺 id 为空，已返回空列表（不做全量兜底）");
            return new ArrayList<>();
        }
        String language = normalizeLanguage(lang);

        // 时间过滤用 Java 侧当前时间：与数据库 NOW() 等价（JDBC 已按 Asia/Shanghai 转换），
        // 且不用在 wrapper 里拼 SQL 片段，避免 SQL 注入面
        Date now = new Date();
        QueryWrapper<ShopPromotion> wrapper = new QueryWrapper<>();
        wrapper.eq("store_id", storeId)
                // 表已存在且 del_flag 语义为 0-正常；这里显式写一遍（@TableLogic 也会自动追加），
                // 让"店铺隔离 + 只取有效行"两个条件在代码里看得见
                .eq("del_flag", 0)
                .eq("status", STATUS_ENABLED)
                .le("start_time", now)
                .ge("end_time", now)
                .orderByAsc("sort_order")
                .orderByAsc("end_time")
                .last("LIMIT " + MAX_ACTIVE_PROMOTIONS);

        List<ShopPromotion> promotions = list(wrapper);
        if (promotions.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, ProductStat> stats = loadProductStats(promotions);
        Map<String, Map<String, ShopPromotionTranslation>> translations = loadTranslations(promotions);

        List<PromoBriefVO> result = new ArrayList<>(promotions.size());
        for (ShopPromotion promotion : promotions) {
            ProductStat stat = stats.get(promotion.getId());
            Map<String, ShopPromotionTranslation> byLang =
                    translations.getOrDefault(promotion.getId(), Collections.emptyMap());
            PromoBriefVO vo = new PromoBriefVO();
            vo.setId(promotion.getId());
            vo.setType(promotion.getType());
            // 按 lang 解析：translation[lang] → translation['zh'] → 主表兜底
            vo.setTitle(resolveText(byLang, language, ShopPromotionTranslation::getTitle, promotion.getTitle()));
            vo.setSubTitle(resolveText(byLang, language, ShopPromotionTranslation::getSubTitle, promotion.getSubTitle()));
            // 落地页路径原样返回：语言前缀由前端按当前语言补，后端不猜
            vo.setLinkUrl(promotion.getLinkUrl());
            // 秒杀条背景图；为空时由前台用品牌色渐变兜底
            vo.setBgImage(promotion.getBgImage());
            vo.setStartTime(promotion.getStartTime());
            vo.setEndTime(promotion.getEndTime());
            vo.setDiscountValue(promotion.getDiscountValue());
            vo.setThresholdAmount(promotion.getThresholdAmount());
            vo.setSeckillStock(promotion.getSeckillStock());
            vo.setProductId(stat == null ? null : stat.firstProductId);
            vo.setProductCount(stat == null ? 0 : stat.productIds.size());
            result.add(vo);
        }
        return result;
    }

    /**
     * 一次性把这几条活动关联的商品关系查出来，再在内存里聚合。
     *
     * <p>为什么不用逐条 count：10 条活动就是 20 次查询（count + first）；
     * 为什么在内存里去重：{@code ck_shop_promotion_product} 一行 = 一个 SKU，
     * 同一商品挂多个 SKU 会有多行，直接用行数当"商品数"会偏大。
     */
    private Map<String, ProductStat> loadProductStats(List<ShopPromotion> promotions) {
        return loadProductStatsByIds(promotions.stream().map(ShopPromotion::getId).collect(Collectors.toList()));
    }

    /**
     * 按活动 id 列表聚合关联商品：第一个商品 id（排序稳定）+ 去重后的商品 id 集合。
     */
    private Map<String, ProductStat> loadProductStatsByIds(List<String> promotionIds) {
        Map<String, ProductStat> stats = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(promotionIds)) {
            return stats;
        }
        QueryWrapper<ShopPromotionProduct> wrapper = new QueryWrapper<>();
        // sort_order / create_time / id 三级排序保证"第一个商品"是稳定的，
        // 不能只按 sort_order：并列时数据库返回顺序不保证，前端「立即抢购」会跳来跳去
        wrapper.in("promotion_id", promotionIds)
                .orderByAsc("sort_order")
                .orderByAsc("create_time")
                .orderByAsc("id");

        for (ShopPromotionProduct relation : promotionProductMapper.selectList(wrapper)) {
            if (StringUtils.isBlank(relation.getProductId())) {
                continue;
            }
            ProductStat stat = stats.computeIfAbsent(relation.getPromotionId(), key -> new ProductStat());
            if (stat.firstProductId == null) {
                // 排序已保证：第一条就是 sort_order 最小的那个商品
                stat.firstProductId = relation.getProductId();
            }
            stat.productIds.add(relation.getProductId());
        }
        return stats;
    }

    /** 单个活动的商品聚合结果（商品 id 去重） */
    private static class ProductStat {
        /** 排序后的第一个商品 id，用于前端「立即抢购」跳转 */
        private String firstProductId;
        /** 去重后的商品 id 集合，其 size 即"关联商品总数" */
        private final Set<String> productIds = new LinkedHashSet<>();
    }

    /**
     * 批量取多语言文案：{@code promotionId → (languageCode → 文案行)}。
     */
    private Map<String, Map<String, ShopPromotionTranslation>> loadTranslations(List<ShopPromotion> promotions) {
        List<String> ids = promotions.stream().map(ShopPromotion::getId).collect(Collectors.toList());
        Map<String, Map<String, ShopPromotionTranslation>> result = new HashMap<>();
        if (CollectionUtils.isEmpty(ids)) {
            return result;
        }
        for (ShopPromotionTranslation translation : translationMapper.selectByPromotionIds(ids)) {
            if (StringUtils.isBlank(translation.getPromotionId())
                    || StringUtils.isBlank(translation.getLanguageCode())) {
                continue;
            }
            result.computeIfAbsent(translation.getPromotionId(), key -> new HashMap<>())
                    .put(translation.getLanguageCode(), translation);
        }
        return result;
    }

    /**
     * 按语言取值：{@code translation[lang] → translation['zh'] → 主表兜底}。
     *
     * <p>三级回退是契约明确要求的顺序。注意"命中语言但该字段为空"也要继续回退，
     * 否则运营只填了英文 title、没填英文 subTitle 时，副标题会直接消失。
     */
    private String resolveText(Map<String, ShopPromotionTranslation> byLang, String language,
                               java.util.function.Function<ShopPromotionTranslation, String> getter,
                               String fallback) {
        ShopPromotionTranslation matched = byLang.get(language);
        if (matched != null && StringUtils.isNotBlank(getter.apply(matched))) {
            return getter.apply(matched);
        }
        ShopPromotionTranslation zh = byLang.get(DEFAULT_LANGUAGE);
        if (zh != null && StringUtils.isNotBlank(getter.apply(zh))) {
            return getter.apply(zh);
        }
        return fallback;
    }

    private String normalizeLanguage(String language) {
        return StringUtils.isBlank(language) ? DEFAULT_LANGUAGE : language.trim().toLowerCase();
    }

    // ==================================================================
    // 管理端 —— 可见域
    // ==================================================================

    @Override
    public List<String> resolveVisibleStoreIds() {
        List<String> visibleOrgCodes = orgScopeResolver.resolveVisibleOrgCodes();
        if (visibleOrgCodes == null) {
            // 总部/管理员：不限店铺
            return null;
        }
        // 店铺 ↔ 部门 1:1，且只有一个绑定部门，所以直接把店铺表带出 departOrgCode 再按前缀过滤。
        // 店铺数量是"十几个"量级，内存过滤比拼 SQL 子查询更简单也更好读。
        List<ShopStore> stores = shopStoreMapper.selectEnabledWithDepart();
        Set<String> allowed = new LinkedHashSet<>();
        for (ShopStore store : stores) {
            if (orgScopeResolver.inScope(store.getDepartOrgCode(), visibleOrgCodes)) {
                allowed.add(store.getId());
            }
        }
        // 停用店铺也纳入（停用≠不能管理历史活动）；但**只对总部/管理员放开**，
        // 运营的可见域始终锚定在自己部门的启用店铺上，避免越权看到别店历史数据
        if (orgScopeResolver.canManageAll()) {
            for (ShopStore store : shopStoreMapper.selectList(null)) {
                allowed.add(store.getId());
            }
        }
        return new ArrayList<>(allowed);
    }

    /**
     * 把可见店铺作用到条件上。{@code scope == null} 不限；空列表恒假（查不到任何数据）。
     */
    private void applyStoreScope(QueryWrapper<ShopPromotion> wrapper, List<String> scope) {
        if (scope == null) {
            return;
        }
        if (scope.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in("store_id", scope);
    }

    // ==================================================================
    // 管理端 —— 查询
    // ==================================================================

    @Override
    public IPage<PromotionListVO> queryPageList(PromotionQuery query, QueryWrapper<ShopPromotion> base,
                                                Integer pageNo, Integer pageSize) {
        int current = pageNo == null ? 1 : pageNo;
        int size = pageSize == null ? 10 : pageSize;

        // 数据可见域 = 店铺筛选 ∩ 账号可见店铺。
        // 两种"空"必须分清：null = 不限（管理员未筛选）；空列表 = 没有交集 → 空分页。
        // 交集为空时**绝不能把条件丢掉**——那等于把全部店铺的活动返回给调用方。
        List<String> visible = resolveVisibleStoreIds();
        String storeIdFilter = query == null ? null : StringUtils.trimToNull(query.getStoreId());
        List<String> scope;
        if (storeIdFilter == null) {
            scope = visible;
        } else if (visible == null) {
            // 管理员按店铺筛选：先确认店铺存在且绑定了部门，避免"筛了个不存在的店铺却返回全量"
            orgScopeResolver.resolveStoreOrgCode(storeIdFilter);
            scope = Collections.singletonList(storeIdFilter);
        } else if (visible.contains(storeIdFilter)) {
            scope = Collections.singletonList(storeIdFilter);
        } else {
            return emptyListPage(current, size);
        }
        if (scope != null && scope.isEmpty()) {
            return emptyListPage(current, size);
        }

        // 基础条件来自控制器：QueryGenerator.initQueryWrapper(promotion, parameterMap)，
        // 里面已经包含「高级查询」(superQueryParams) 与同名字段条件。
        QueryWrapper<ShopPromotion> wrapper = base == null ? new QueryWrapper<>() : base;
        applyStoreScope(wrapper, scope);
        applyBusinessQuery(wrapper, query);
        // 排序必须最后追加：QueryGenerator 也已按需追加过排序，这里再补默认排序，
        // 保证"运营排序号"始终优先于字段条件带来的顺序影响。
        // 默认按 sort_order 升序（数字越小越靠前），同级按创建时间倒序（新活动在前）
        wrapper.orderByAsc("sort_order").orderByDesc("create_time");

        Page<ShopPromotion> page = new Page<>(current, size);
        IPage<ShopPromotion> pageList = super.page(page, wrapper);
        List<ShopPromotion> records = pageList.getRecords();
        if (records.isEmpty()) {
            return emptyListPage(current, size);
        }

        List<String> ids = records.stream().map(ShopPromotion::getId).collect(Collectors.toList());
        Map<String, ProductStat> stats = loadProductStatsByIds(ids);
        Map<String, Map<String, ShopPromotionTranslation>> translations = loadTranslations(records);
        Map<String, String> storeNames = loadStoreNames(records.stream()
                .map(ShopPromotion::getStoreId).filter(StringUtils::isNotBlank).collect(Collectors.toList()));

        List<PromotionListVO> voList = new ArrayList<>(records.size());
        for (ShopPromotion promotion : records) {
            PromotionListVO vo = new PromotionListVO();
            BeanUtils.copyProperties(promotion, vo);
            vo.setStoreName(storeNames.get(promotion.getStoreId()));
            ProductStat stat = stats.get(promotion.getId());
            vo.setProductCount(stat == null ? 0 : stat.productIds.size());
            // 列表只显示"中文优先"的一份文案：zh 翻译 → 主表
            Map<String, ShopPromotionTranslation> byLang =
                    translations.getOrDefault(promotion.getId(), Collections.emptyMap());
            vo.setTitle(resolveText(byLang, DEFAULT_LANGUAGE, ShopPromotionTranslation::getTitle, promotion.getTitle()));
            vo.setSubTitle(resolveText(byLang, DEFAULT_LANGUAGE,
                    ShopPromotionTranslation::getSubTitle, promotion.getSubTitle()));
            voList.add(vo);
        }
        Page<PromotionListVO> result = new Page<>(pageList.getCurrent(), pageList.getSize(), pageList.getTotal());
        result.setRecords(voList);
        return result;
    }

    /**
     * 追加促销活动的业务筛选条件（这些是 {@code PromotionQuery} 自己的语义，
     * {@code QueryGenerator} 不认识：它只看实体字段）。
     */
    private void applyBusinessQuery(QueryWrapper<ShopPromotion> wrapper, PromotionQuery query) {
        if (query == null) {
            return;
        }
        wrapper.eq(StringUtils.isNotBlank(query.getType()), "type", query.getType());
        wrapper.eq(StringUtils.isNotBlank(query.getStatus()), "status", query.getStatus());
        if (StringUtils.isNotBlank(query.getCode())) {
            wrapper.like("code", promotionSupport.escapeSqlLike(query.getCode()));
        }
        if (StringUtils.isNotBlank(query.getTitle())) {
            // 列表显示的是 zh 翻译（缺失才回退主表），所以两处都要能搜到：
            // 只搜主表会漏掉"主表还是旧文案、运营只改了翻译"的活动。
            // 用两个 and 包住 OR 组，避免 OR 把前面的可见域条件"吃掉"。
            String kw = promotionSupport.escapeSqlLike(query.getTitle());
            wrapper.and(w -> w.like("title", kw).or().inSql("id",
                    "SELECT promotion_id FROM ck_shop_promotion_translation WHERE title LIKE '%" + kw + "%'"));
        }
        wrapper.ge(StringUtils.isNotBlank(query.getStartTimeBegin()), "start_time",
                promotionSupport.parseDate(query.getStartTimeBegin(), "startTimeBegin"));
        wrapper.le(StringUtils.isNotBlank(query.getStartTimeEnd()), "start_time",
                promotionSupport.parseDate(query.getStartTimeEnd(), "startTimeEnd"));
        wrapper.ge(StringUtils.isNotBlank(query.getEndTimeBegin()), "end_time",
                promotionSupport.parseDate(query.getEndTimeBegin(), "endTimeBegin"));
        wrapper.le(StringUtils.isNotBlank(query.getEndTimeEnd()), "end_time",
                promotionSupport.parseDate(query.getEndTimeEnd(), "endTimeEnd"));
    }

    /** 空分页（可见域无交集 / 无记录时用），保持与正常分页一致的 current/size */
    private Page<PromotionListVO> emptyListPage(Integer pageNo, Integer pageSize) {
        Page<PromotionListVO> empty = new Page<>(pageNo == null ? 1 : pageNo, pageSize == null ? 10 : pageSize, 0);
        empty.setRecords(new ArrayList<>());
        return empty;
    }

    @Override
    public PromotionDTO getDetailById(String id) {
        ShopPromotion promotion = loadAccessible(id);
        PromotionDTO dto = new PromotionDTO();
        BeanUtils.copyProperties(promotion, dto);
        dto.setTranslations(translationMapper.selectByPromotionId(id));
        QueryWrapper<ShopPromotionProduct> wrapper = new QueryWrapper<>();
        wrapper.eq("promotion_id", id).orderByAsc("sort_order").orderByAsc("create_time").orderByAsc("id");
        dto.setProducts(promotionProductMapper.selectList(wrapper));
        return dto;
    }

    /**
     * 按 id 取活动并做越权校验。
     * 所有"按 id 操作"的入口都必须走这里——列表已经过滤了，但接口是可以被直接按 id 调的。
     */
    private ShopPromotion loadAccessible(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("未找到活动主键!");
        }
        ShopPromotion promotion = super.getById(id);
        if (Objects.isNull(promotion)) {
            throw new JeecgBootException("活动不存在或已删除!");
        }
        checkStoreAccessible(promotion.getStoreId());
        return promotion;
    }

    /**
     * 校验"这个店铺在本人可见域内"。
     *
     * <p>可见域判定沿用 {@link #resolveVisibleStoreIds()}——它是**唯一**的实现，
     * 列表、详情、改删、导出共用，任何一个入口漏掉都会变成越权。
     */
    private void checkStoreAccessible(String storeId) {
        List<String> visible = resolveVisibleStoreIds();
        if (visible == null) {
            return;
        }
        if (StringUtils.isBlank(storeId) || !visible.contains(storeId)) {
            throw new JeecgBootException("无权访问该活动：它不属于当前账号可见的店铺范围");
        }
    }

    private Map<String, String> loadStoreNames(List<String> storeIds) {
        Map<String, String> names = new HashMap<>();
        if (CollectionUtils.isEmpty(storeIds)) {
            return names;
        }
        List<String> distinct = storeIds.stream().filter(StringUtils::isNotBlank).distinct()
                .collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return names;
        }
        for (ShopStore store : baseMapper.selectStoreNames(distinct)) {
            names.put(store.getId(), store.getName());
        }
        return names;
    }

    // ==================================================================
    // 管理端 —— 写入
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String savePromotion(PromotionDTO dto) {
        if (dto == null) {
            throw new JeecgBootException("活动数据不能为空!");
        }
        String storeId = resolveWritableStoreId(dto.getStoreId(), null);
        // 校验顺序刻意固定：先"缺什么"→ 再"编码占用"→ 再"类型参数"→ 再"多语言"→ 最后"商品归属"。
        // 越靠后的校验越贵（要查库/查商品），先做便宜的校验能让用户一次就看到最该改的那个错。
        promotionSupport.requireNotBlank(dto.getCode(), "活动编码不能为空!");
        promotionSupport.requireNotBlank(dto.getType(), "活动类型不能为空!");
        requireTitle(dto);
        promotionSupport.validateTimeWindow(dto.getStartTime(), dto.getEndTime());
        validateStatus(dto.getStatus());
        checkCodeUnique(dto.getCode(), storeId, null);
        validateTypeFields(dto.getType(), dto.getDiscountValue(), dto.getThresholdAmount(), dto.getSeckillStock());
        List<ShopPromotionTranslation> translations = validateTranslations(dto.getTranslations());
        List<ShopPromotionProduct> products = validateProducts(dto.getProducts(), storeId);

        ShopPromotion promotion = new ShopPromotion();
        BeanUtils.copyProperties(dto, promotion);
        promotion.setId(IdWorker.getIdStr());
        promotion.setStoreId(storeId);
        promotion.setDelFlag(null);
        // 编辑态字段不落值：新增必须为 null，避免脏数据/克隆来源的 id 覆盖
        promotion.setCreateBy(null).setCreateTime(null).setUpdateBy(null).setUpdateTime(null);
        promotion.setStoreName(null).setStoreCode(null);
        if (StringUtils.isBlank(promotion.getStatus())) {
            promotion.setStatus(STATUS_DISABLED);
        }
        String username = ShopAuditUtils.currentUsername();
        Date now = new Date();
        promotion.setCreateBy(username).setCreateTime(now).setUpdateBy(username).setUpdateTime(now);

        super.save(promotion);
        saveTranslations(promotion.getId(), translations, username, now);
        saveProducts(promotion.getId(), products, username, now);
        return promotion.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updatePromotion(PromotionDTO dto) {
        if (dto == null) {
            throw new JeecgBootException("活动数据不能为空!");
        }
        ShopPromotion exist = loadAccessible(dto.getId());
        String storeId = resolveWritableStoreId(dto.getStoreId(), exist.getStoreId());
        promotionSupport.requireNotBlank(dto.getCode(), "活动编码不能为空!");
        promotionSupport.requireNotBlank(dto.getType(), "活动类型不能为空!");
        requireTitle(dto);
        promotionSupport.validateTimeWindow(dto.getStartTime(), dto.getEndTime());
        validateStatus(dto.getStatus());
        checkCodeUnique(dto.getCode(), storeId, dto.getId());
        validateTypeFields(dto.getType(), dto.getDiscountValue(), dto.getThresholdAmount(), dto.getSeckillStock());
        List<ShopPromotionTranslation> translations = validateTranslations(dto.getTranslations());
        List<ShopPromotionProduct> products = validateProducts(dto.getProducts(), storeId);

        ShopPromotion promotion = new ShopPromotion();
        BeanUtils.copyProperties(dto, promotion);
        promotion.setId(dto.getId());
        promotion.setStoreId(storeId);
        promotion.setDelFlag(null);
        // 审计字段不覆盖；派生字段不参与更新
        promotion.setCreateBy(null).setCreateTime(null);
        promotion.setStoreName(null).setStoreCode(null);
        String username = ShopAuditUtils.currentUsername();
        Date now = new Date();
        promotion.setUpdateBy(username).setUpdateTime(now);

        /*
         * 用 updateById 的部分更新语义有问题：它默认跳过 null 字段，
         * 运营想把 linkUrl / bgImage / discountValue 清空时，null 会被静默忽略，旧值留在库里
         * （用户看到"保存成功"但值没删掉）。这里改用显式 set 更新，让清空真正生效。
         */
        updatePromotionColumns(promotion, now);

        saveTranslations(dto.getId(), translations, username, now);
        saveProducts(dto.getId(), products, username, now);
        return dto.getId();
    }

    /**
     * 主表显式列更新：null 也会写进库（清空操作要生效），且只更新业务列，
     * 不碰 create_by / create_time / del_flag。
     */
    private void updatePromotionColumns(ShopPromotion promotion, Date now) {
        LambdaUpdateWrapper<ShopPromotion> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ShopPromotion::getId, promotion.getId())
                .set(ShopPromotion::getStoreId, promotion.getStoreId())
                .set(ShopPromotion::getCode, promotion.getCode())
                .set(ShopPromotion::getType, promotion.getType())
                .set(ShopPromotion::getTitle, promotion.getTitle())
                .set(ShopPromotion::getSubTitle, promotion.getSubTitle())
                .set(ShopPromotion::getLinkUrl, promotion.getLinkUrl())
                .set(ShopPromotion::getBgImage, promotion.getBgImage())
                .set(ShopPromotion::getSortOrder, promotion.getSortOrder())
                .set(ShopPromotion::getStartTime, promotion.getStartTime())
                .set(ShopPromotion::getEndTime, promotion.getEndTime())
                .set(ShopPromotion::getDiscountValue, promotion.getDiscountValue())
                .set(ShopPromotion::getThresholdAmount, promotion.getThresholdAmount())
                .set(ShopPromotion::getSeckillStock, promotion.getSeckillStock())
                .set(ShopPromotion::getLimitPerUser, promotion.getLimitPerUser())
                .set(ShopPromotion::getStatus, promotion.getStatus())
                .set(ShopPromotion::getUpdateBy, promotion.getUpdateBy())
                .set(ShopPromotion::getUpdateTime, now);
        // update 返回 false 表示 id 不存在或已被软删除，不能对用户谎报成功
        if (!super.update(wrapper)) {
            throw new JeecgBootException("活动不存在或已删除，保存失败!");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String copyPromotion(String id, String newCode) {
        ShopPromotion source = loadAccessible(id);
        String username = ShopAuditUtils.currentUsername();
        Date now = new Date();

        ShopPromotion copy = new ShopPromotion();
        BeanUtils.copyProperties(source, copy);
        copy.setId(IdWorker.getIdStr());
        // 编码：入参优先，否则 原code-COPY-yyyyMMddHHmmss
        copy.setCode(resolveCopyCode(newCode, source.getCode()));
        // 复制出来的活动带 (副本) 后缀，列表里一眼能区分原活动与副本
        copy.setTitle(appendCopySuffix(source.getTitle()));
        // 时间窗原样复制（运营自己改）
        copy.setStartTime(source.getStartTime());
        copy.setEndTime(source.getEndTime());
        // 强制停用：复制出来的活动不应直接生效，必须人工确认后启用
        copy.setStatus(STATUS_DISABLED);
        copy.setDelFlag(null);
        copy.setStoreName(null).setStoreCode(null);
        copy.setCreateBy(username).setCreateTime(now).setUpdateBy(username).setUpdateTime(now);
        // 复制出来的 code 是服务端生成的，必须再过一次唯一性校验（同一秒内二次复制会撞码）
        checkCodeUnique(copy.getCode(), copy.getStoreId(), null);
        super.save(copy);

        // 复制全部多语言：zh 的 title 同步加后缀，与主表保持一致，否则列表显示副本、表单显示原文案
        saveTranslations(copy.getId(), copyTranslations(source.getId(), copy.getTitle()), username, now);

        // 复制全部关联商品；promoPrice 为空的行保持为空（= 自动算），绝不写成 0
        List<ShopPromotionProduct> sourceProducts = loadProductRelations(source.getId());
        List<ShopPromotionProduct> copies = new ArrayList<>(sourceProducts.size());
        for (ShopPromotionProduct sp : sourceProducts) {
            ShopPromotionProduct product = new ShopPromotionProduct();
            BeanUtils.copyProperties(sp, product);
            product.setId(null).setPromotionId(copy.getId())
                    .setCreateBy(null).setCreateTime(null).setUpdateBy(null).setUpdateTime(null);
            copies.add(product);
        }
        saveProducts(copy.getId(), copies, username, now);
        return copy.getId();
    }

    /**
     * 复制多语言行：zh 的 title 加「(副本)」后缀副本化，其它语言原样（原活动文案仍可参考）。
     */
    private List<ShopPromotionTranslation> copyTranslations(String sourceId, String newMainTitle) {
        List<ShopPromotionTranslation> result = new ArrayList<>();
        for (ShopPromotionTranslation source : translationMapper.selectByPromotionId(sourceId)) {
            ShopPromotionTranslation copy = new ShopPromotionTranslation();
            BeanUtils.copyProperties(source, copy);
            copy.setId(null).setPromotionId(null)
                    .setCreateBy(null).setCreateTime(null).setUpdateBy(null).setUpdateTime(null);
            if (DEFAULT_LANGUAGE.equals(copy.getLanguageCode())) {
                copy.setTitle(newMainTitle);
            }
            result.add(copy);
        }
        return result;
    }

    /** 复制活动编码：入参优先（仍需唯一），否则 原code-COPY-yyyyMMddHHmmss */
    private String resolveCopyCode(String newCode, String sourceCode) {
        String target = StringUtils.trimToNull(newCode);
        if (target == null) {
            String base = StringUtils.trimToEmpty(sourceCode);
            target = base + "-COPY-" + new SimpleDateFormat(COPY_CODE_TIME_PATTERN).format(new Date());
        }
        // 列宽 50：超长直接报错，别让数据库抛 "Data too long"（对用户完全不可读）
        if (target.length() > 50) {
            throw new JeecgBootException("活动编码长度不能超过 50 个字符：" + target);
        }
        return target;
    }

    private String appendCopySuffix(String title) {
        String target = StringUtils.trimToEmpty(title);
        if (target.endsWith(COPY_TITLE_SUFFIX)) {
            // 幂等：副本再复制副本时不再叠加，避免 "(副本)(副本)(副本)"
            return target;
        }
        return target + COPY_TITLE_SUFFIX;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removePromotion(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("请选择要删除的活动!");
        }
        loadAccessible(id);
        // 软删除主表：留痕（谁在什么时候删的），同时**物理清理**翻译与关联
        // （两张子表都没有 del_flag，留着会让已删除活动继续占用商品、继续被前台读到）
        translationMapper.deleteByPromotionId(id);
        promotionProductMapper.deleteByPromotionId(id);
        super.removeById(id);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removePromotions(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            throw new JeecgBootException("请至少选择一条数据!");
        }
        List<String> distinctIds = ids.stream().filter(StringUtils::isNotBlank).distinct()
                .collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            throw new JeecgBootException("请至少选择一条数据!");
        }
        // 与单条删除口径一致：有 id 不存在就直接报错，不做"部分成功"（否则前端以为全删了）
        List<ShopPromotion> found = this.listByIds(distinctIds);
        if (found.size() < distinctIds.size()) {
            Set<String> foundIds = found.stream().map(ShopPromotion::getId).collect(Collectors.toSet());
            List<String> missing = distinctIds.stream().filter(item -> !foundIds.contains(item))
                    .collect(Collectors.toList());
            throw new JeecgBootException("部分活动不存在或已删除：" + String.join(",", missing));
        }
        // 批量删除同样要逐条校验越权：listByIds 已自动排除软删除行
        for (ShopPromotion item : found) {
            checkStoreAccessible(item.getStoreId());
        }
        translationMapper.deleteByPromotionIds(distinctIds);
        promotionProductMapper.deleteByPromotionIds(distinctIds);
        super.removeByIds(distinctIds);
        return true;
    }

    @Override
    public boolean updateStatus(String id, String status) {
        ShopPromotion promotion = loadAccessible(id);
        validateStatus(status);
        ShopPromotion update = new ShopPromotion();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateBy(ShopAuditUtils.currentUsername());
        update.setUpdateTime(new Date());
        // updateById 返回 false 表示 id 不存在或已被软删除，不能对用户谎报成功
        if (!super.updateById(update)) {
            throw new JeecgBootException("活动不存在或已删除，操作失败!");
        }
        log.info("活动状态变更：id={}, code={}, {} -> {}",
                promotion.getId(), promotion.getCode(), promotion.getStatus(), status);
        return true;
    }

    // ==================================================================
    // 管理端 —— 冲突检测
    // ==================================================================

    @Override
    public List<PromotionConflictVO> checkConflict(String productIds, String startTime, String endTime,
                                                   String excludeId) {
        List<String> ids = promotionSupport.splitIds(productIds);
        if (ids.isEmpty()) {
            // 没有商品就没有冲突可言；不报错（前端新建活动时商品还是空的）
            return new ArrayList<>();
        }
        // 商品归属校验：不允许拿别店商品来做冲突探测（否则能从冲突结果里读出别店的活动名）。
        // 可见店铺只解析一次，避免逐个商品重复查库。
        List<String> visibleStores = resolveVisibleStoreIds();
        for (String productId : ids) {
            ShopProduct product = shopProductMapper.selectById(productId);
            if (product == null) {
                throw new JeecgBootException("商品不存在或已删除：" + productId);
            }
            checkProductAccessible(product, visibleStores);
        }
        Date start = promotionSupport.parseDate(startTime, "startTime");
        Date end = promotionSupport.parseDate(endTime, "endTime");
        promotionSupport.validateTimeWindow(start, end);

        // 非管理员：冲突结果只能包含可见店铺的活动，做一层显式收口
        String exclude = StringUtils.trimToNull(excludeId);
        if (exclude != null) {
            loadAccessible(exclude);
        }

        List<PromotionConflictRow> rows = baseMapper.selectConflicts(ids, start, end, exclude);
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, String> storeNames = loadStoreNames(rows.stream()
                .map(PromotionConflictRow::getStoreId).filter(StringUtils::isNotBlank).collect(Collectors.toList()));
        List<String> productIdList = rows.stream().map(PromotionConflictRow::getProductId)
                .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
        Map<String, String> productTitles = loadProductTitles(productIdList);

        List<PromotionConflictVO> result = new ArrayList<>(rows.size());
        for (PromotionConflictRow row : rows) {
            PromotionConflictVO vo = new PromotionConflictVO();
            BeanUtils.copyProperties(row, vo);
            vo.setPromotionTitle(row.getPromotionTitle());
            vo.setProductTitle(productTitles.get(row.getProductId()));
            vo.setStoreName(storeNames.get(row.getStoreId()));
            result.add(vo);
        }
        return result;
    }

    /** 商品名优先取 zh 翻译（与列表页口径一致），没有翻译行时回退 spu_code */
    private Map<String, String> loadProductTitles(List<String> productIds) {
        Map<String, String> titles = new HashMap<>();
        if (CollectionUtils.isEmpty(productIds)) {
            return titles;
        }
        QueryWrapper<ShopProduct> wrapper = new QueryWrapper<>();
        wrapper.in("id", productIds);
        Map<String, String> codeById = new HashMap<>();
        for (ShopProduct product : shopProductMapper.selectList(wrapper)) {
            codeById.put(product.getId(), product.getSpuCode());
        }
        Map<String, String> translationTitles = productTitlesFromTranslations(productIds);
        for (String productId : productIds) {
            String title = translationTitles.get(productId);
            titles.put(productId, StringUtils.isNotBlank(title) ? title : codeById.get(productId));
        }
        return titles;
    }

    /** zh 商品名映射：productId → title（无翻译行则不含该键） */
    private Map<String, String> productTitlesFromTranslations(List<String> productIds) {
        Map<String, String> titles = new HashMap<>();
        if (CollectionUtils.isEmpty(productIds)) {
            return titles;
        }
        for (ShopProductTranslation translation : baseMapper.selectProductTitles(productIds)) {
            if (StringUtils.isNotBlank(translation.getProductId())
                    && StringUtils.isNotBlank(translation.getTitle())) {
                titles.putIfAbsent(translation.getProductId(), translation.getTitle());
            }
        }
        return titles;
    }

    // ==================================================================
    // 校验
    // ==================================================================



    private void validateStatus(String status) {
        if (StringUtils.isBlank(status)) {
            // 允许缺省（前端表单默认 Y）；缺省时由调用方补默认值
            return;
        }
        if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new JeecgBootException("状态值不合法，只能为 Y 或 N!");
        }
    }

    /**
     * 活动编码在**同一店铺内**唯一，且**含软删除行**一起判重。
     *
     * <p>为什么要算上软删除：编码会被写进前台站点配置（和 {@code spu_code} 同理），
     * 删了再建一个同码活动，历史配置就会指向新活动；恢复数据时也会直接撞唯一索引。
     *
     * <p>实现上不能用 {@code super.count(wrapper)} —— {@code @TableLogic} 会自动追加
     * {@code del_flag = 0}，恰好把"已删除但编码仍被占用"的行过滤掉。
     * 因此走 mapper 里那条显式 SQL（见 {@code countByCodeIncludingDeleted}）。
     */
    private void checkCodeUnique(String code, String storeId, String excludeId) {
        if (baseMapper.countByCodeIncludingDeleted(code, storeId, excludeId) > 0) {
            throw new JeecgBootException("该店铺下活动编码已存在（含已删除的活动，编码不会被回收）：" + code);
        }
    }

    /**
     * 类型必填项校验：SECKILL 限量 > 0；PERCENT 折扣率 ∈ (0,10)；DIRECT 直降 > 0；FULL_CUT 门槛 > 0。
     */
    private void validateTypeFields(String type, BigDecimal discountValue, BigDecimal thresholdAmount,
                                    Integer seckillStock) {
        if (!VALID_TYPES.contains(type)) {
            throw new JeecgBootException("活动类型不合法，只能为 DIRECT / PERCENT / FULL_CUT / SECKILL!");
        }
        switch (type) {
            case "SECKILL":
                if (seckillStock == null || seckillStock <= 0) {
                    throw new JeecgBootException("秒杀活动必须填写大于 0 的秒杀限量!");
                }
                break;
            case "PERCENT":
                if (discountValue == null
                        || discountValue.compareTo(BigDecimal.ZERO) <= 0
                        || discountValue.compareTo(BigDecimal.TEN) >= 0) {
                    throw new JeecgBootException("折扣活动的折扣率必须在 0 与 10 之间（8.5 表示 8.5 折）!");
                }
                break;
            case "DIRECT":
                if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new JeecgBootException("直降活动必须填写大于 0 的直降金额!");
                }
                break;
            case "FULL_CUT":
                if (thresholdAmount == null || thresholdAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new JeecgBootException("满减活动必须填写大于 0 的满减门槛!");
                }
                break;
            default:
                break;
        }
    }

    /**
     * 校验活动名称，并在主表文案缺失时**从多语言文案里推导**（顺带补 subTitle）。
     *
     * <p>主表的 {@code title} / {@code subTitle} 是「默认/兜底文案」：列表页显示它、
     * 某语言没有翻译时回退到它。而契约里标题只放在 {@code translations} 里，前端只传多语言那一份。
     * 如果这里要求调用方把同一个标题再传一遍，迟早有人只传一份然后撞上「活动名称不能为空」。
     * 一个标题存两份、还要求两边始终一致，本身就是接口设计问题 —— 所以由服务端推导。
     *
     * <p>只做取值、不做合法性判断：缺 zh、languageCode 为空等仍由
     * {@link #validateTranslations(List)} 统一报错，保证同一类错误只有一处信息源。
     */
    private void requireTitle(PromotionDTO dto) {
        if (dto != null && !CollectionUtils.isEmpty(dto.getTranslations())) {
            ShopPromotionTranslation picked = null;
            for (ShopPromotionTranslation translation : dto.getTranslations()) {
                if (translation == null || StringUtils.isBlank(translation.getLanguageCode())) {
                    continue;
                }
                if (DEFAULT_LANGUAGE.equalsIgnoreCase(translation.getLanguageCode().trim())) {
                    picked = translation;
                    break;
                }
                if (picked == null) {
                    // 没有 zh 时先用第一条兜底，让「必须包含 zh」的错误由 validateTranslations 报出来
                    picked = translation;
                }
            }
            if (picked != null) {
                if (StringUtils.isBlank(dto.getTitle())) {
                    dto.setTitle(picked.getTitle());
                }
                if (StringUtils.isBlank(dto.getSubTitle())) {
                    dto.setSubTitle(picked.getSubTitle());
                }
            }
        }
        promotionSupport.requireNotBlank(dto == null ? null : dto.getTitle(),
                "活动名称不能为空!(请在「多语言标题」里填写至少中文标题)");
    }

    /**
     * 多语言校验：至少一条，且必须包含 zh。
     *
     * <p>同时过滤掉语言码为空的行——它们落库只会变成"永远读不到的垃圾行"。
     */
    private List<ShopPromotionTranslation> validateTranslations(List<ShopPromotionTranslation> translations) {
        if (CollectionUtils.isEmpty(translations)) {
            throw new JeecgBootException("至少需要一条多语言文案!");
        }
        List<ShopPromotionTranslation> result = new ArrayList<>(translations.size());
        Set<String> languages = new LinkedHashSet<>();
        for (ShopPromotionTranslation translation : translations) {
            if (translation == null || StringUtils.isBlank(translation.getLanguageCode())) {
                continue;
            }
            String lang = translation.getLanguageCode().trim().toLowerCase();
            translation.setLanguageCode(lang);
            languages.add(lang);
            result.add(translation);
        }
        if (result.isEmpty()) {
            throw new JeecgBootException("多语言文案缺少 languageCode!");
        }
        if (!languages.contains(DEFAULT_LANGUAGE)) {
            throw new JeecgBootException("多语言文案必须包含中文（zh）!");
        }
        return result;
    }

    /**
     * 关联商品校验：
     * <ol>
     *   <li>不允许同一活动重复关联同一 {@code productId + skuId}；</li>
     *   <li>商品必须属于该活动所在店铺（店铺 → 部门 org_code → 商品 sys_org_code 前缀匹配）；</li>
     *   <li>{@code promoPrice} 为空保持为空（= 自动算），**不填成 0**。</li>
     * </ol>
     */
    private List<ShopPromotionProduct> validateProducts(List<ShopPromotionProduct> products, String storeId) {
        List<ShopPromotionProduct> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(products)) {
            // 允许为空：先建活动后加商品是正常流程
            return result;
        }
        // 本活动所在店铺 → 部门 org_code，用于**本店归属**校验
        String storeOrgCode = orgScopeResolver.resolveStoreOrgCode(storeId);
        // 账号可见店铺，用于**越权**校验（与冲突检测共用同一个方法，避免两处口径分叉）
        List<String> visibleStores = resolveVisibleStoreIds();
        Set<String> seen = new HashSet<>();
        for (ShopPromotionProduct product : products) {
            if (product == null || StringUtils.isBlank(product.getProductId())) {
                continue;
            }
            String productId = product.getProductId().trim();
            // 空 skuId 归一成 null：空串与 null 在唯一性判断上是同一件事，
            // 不归一的话 "" 与 null 会被当成两个不同的 SKU，唯一性形同虚设
            String skuId = StringUtils.trimToNull(product.getSkuId());
            String key = productId + "::" + (skuId == null ? "" : skuId);
            if (!seen.add(key)) {
                throw new JeecgBootException("同一商品（含 SKU）不能重复关联：" + productId);
            }
            ShopProduct exist = shopProductMapper.selectById(productId);
            if (exist == null) {
                throw new JeecgBootException("关联的商品不存在或已删除：" + productId);
            }
            // 契约第 3 节的硬校验：关联商品必须属于**该活动所在店铺**。
            // 这一条对管理员同样生效——它和"账号可见域"是两件事：
            // 管理员能管所有店铺，但一个活动只能挂自己店铺的商品。
            // 判定方式与前台商品可见性一致：sys_org_code 以店铺部门 org_code 为前缀。
            if (!orgScopeResolver.inScope(exist.getSysOrgCode(), Collections.singletonList(storeOrgCode))) {
                throw new JeecgBootException("商品[" + promotionSupport.labelOf(exist) + "]不属于当前活动所在店铺，不能关联");
            }
            // 再叠加账号可见域：运营不能通过"把活动建到自己店、再挂别店商品"绕过隔离
            checkProductAccessible(exist, visibleStores);
            product.setProductId(productId);
            product.setSkuId(skuId);
            // promoPrice 保持原样：null = 按活动类型自动算，绝不能补 0（0 元会把商品白送出去）
            product.setId(null).setPromotionId(null)
                    .setCreateBy(null).setCreateTime(null).setUpdateBy(null).setUpdateTime(null);
            result.add(product);
        }
        return result;
    }


    /**
     * 校验商品可访问（冲突检测入口用）。
     *
     * <p>判定与 {@link #validateProducts} 同一条路径：
     * 商品 {@code sys_org_code} 必须落在**某个可见店铺**绑定部门的 org_code 前缀之下。
     * 这里的循环只在"非管理员 + 冲突检测"这条少见的路径上跑，店铺数量是个位数，不必优化成 SQL。
     *
     * @param visibleStores {@link #resolveVisibleStoreIds()} 的结果；{@code null} = 不限店铺
     */
    private void checkProductAccessible(ShopProduct product, List<String> visibleStores) {
        if (visibleStores == null) {
            // 总部/管理员：不限店铺
            return;
        }
        if (StringUtils.isBlank(product.getSysOrgCode())) {
            throw new JeecgBootException("商品[" + promotionSupport.labelOf(product) + "]没有归属部门，无法判断是否越权访问");
        }
        for (ShopStore store : shopStoreMapper.selectEnabledWithDepart()) {
            if (visibleStores.contains(store.getId())
                    && orgScopeResolver.inScope(product.getSysOrgCode(),
                    Collections.singletonList(store.getDepartOrgCode()))) {
                return;
            }
        }
        throw new JeecgBootException("无权访问该商品：它不属于当前账号可见的店铺范围");
    }

    /**
     * 解析"这条活动最终归属哪个店铺"，并完成越权校验。
     *
     * <p>与商品的 {@code resolveWritableOrgCode} 同思路：
     * <ul>
     *   <li>请求为空 → 编辑沿用原值 / 新增报错（店铺必填）；</li>
     *   <li>归属变化时，校验目标店铺是否在本人可见域内——运营不能把活动建到别店，
     *       也不能把别店的活动"搬"到自己店名下。</li>
     * </ul>
     */
    private String resolveWritableStoreId(String requested, String existing) {
        String target = StringUtils.trimToNull(requested);
        if (target == null) {
            target = StringUtils.trimToNull(existing);
        }
        if (target == null) {
            throw new JeecgBootException("活动所属店铺不能为空!");
        }
        ShopStore store = shopStoreMapper.selectById(target);
        if (store == null) {
            throw new JeecgBootException("所属店铺不存在或已删除：" + target);
        }
        // 目标店铺必须在本人可见域内：运营不能把活动建到别店，也不能把别店的活动"搬"到自己店名下。
        // 与"归属是否变化"无关，**每次写入都校验**——否则一处漏判就是跨店写入。
        checkStoreAccessible(target);
        // 店铺必须绑定部门（否则活动在任何前台站点都看不到）。
        // 编辑时归属没变也校验：活动本身改不动商品归属，但后面的关联商品校验需要这个 org_code；
        // 若部门绑定真的丢了，必须在这里给出可读的错误，而不是让运营存不进去却不知道为什么。
        orgScopeResolver.resolveStoreOrgCode(target);
        return target;
    }

    // ==================================================================
    // 子表写入
    // ==================================================================

    /**
     * 多语言写入：按 {@code languageCode} 增量匹配。
     *
     * <p>不做"删了重建"：翻译表有唯一键 {@code (promotion_id, language_code)}，
     * 删了重建虽然不撞键，但会让文案行的主键每次都变，任何按主键的引用都会失效；
     * 增量匹配还能保留 {@code create_by/create_time} 的原始留痕。
     */
    private void saveTranslations(String promotionId, List<ShopPromotionTranslation> incoming,
                                  String username, Date now) {
        List<ShopPromotionTranslation> existList = translationMapper.selectByPromotionId(promotionId);
        Map<String, ShopPromotionTranslation> existByLang = new HashMap<>();
        for (ShopPromotionTranslation exist : existList) {
            if (StringUtils.isNotBlank(exist.getLanguageCode())) {
                existByLang.put(exist.getLanguageCode(), exist);
            }
        }
        Set<String> keptLanguages = new HashSet<>();
        for (ShopPromotionTranslation item : CollectionUtils.isEmpty(incoming)
                ? Collections.<ShopPromotionTranslation>emptyList() : incoming) {
            String lang = item.getLanguageCode();
            if (StringUtils.isBlank(lang)) {
                continue;
            }
            keptLanguages.add(lang);
            ShopPromotionTranslation exist = existByLang.get(lang);
            if (exist != null) {
                // 原地更新：同一 (promotion_id, language_code) 只保留一行
                item.setId(exist.getId());
                item.setPromotionId(promotionId);
                item.setCreateBy(null).setCreateTime(null);
                item.setUpdateBy(username).setUpdateTime(now);
                translationMapper.updateById(item);
            } else {
                item.setId(IdWorker.getIdStr());
                item.setPromotionId(promotionId);
                item.setCreateBy(username).setCreateTime(now);
                item.setUpdateBy(username).setUpdateTime(now);
                translationMapper.insert(item);
            }
        }
        for (ShopPromotionTranslation exist : existList) {
            if (!keptLanguages.contains(exist.getLanguageCode())) {
                // 该语言被运营删除了：表无 del_flag，直接物理删除
                translationMapper.deleteById(exist.getId());
            }
        }
    }

    /**
     * 关联商品写入：整体替换（先删后插）。
     *
     * <p>为什么这里可以整体替换而商品模块要增量：活动关联行没有任何外部引用
     * （订单记的是商品/SKU，不是活动关联行），且不存在"历史留痕"需求，
     * 整体替换的实现最简单、也最不容易出现"删一半"的中间态（整个过程在同一事务里）。
     */
    private void saveProducts(String promotionId, List<ShopPromotionProduct> products,
                              String username, Date now) {
        promotionProductMapper.deleteByPromotionId(promotionId);
        if (CollectionUtils.isEmpty(products)) {
            return;
        }
        int index = 0;
        for (ShopPromotionProduct product : products) {
            product.setId(IdWorker.getIdStr());
            product.setPromotionId(promotionId);
            // sort_order 缺省按提交顺序补齐，保证前端「立即抢购」取到的第一个商品是用户排的第一个
            if (product.getSortOrder() == null) {
                product.setSortOrder(index);
            }
            product.setCreateBy(username).setCreateTime(now);
            product.setUpdateBy(username).setUpdateTime(now);
            promotionProductMapper.insert(product);
            index++;
        }
    }

    /** 读取某活动的全部关联商品（复制用，顺序稳定） */
    private List<ShopPromotionProduct> loadProductRelations(String promotionId) {
        QueryWrapper<ShopPromotionProduct> wrapper = new QueryWrapper<>();
        wrapper.eq("promotion_id", promotionId)
                .orderByAsc("sort_order").orderByAsc("create_time").orderByAsc("id");
        return promotionProductMapper.selectList(wrapper);
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /** 逗号分隔 id 串 → 去重后的列表（空串/空白项直接丢弃） */

    /**
     * 解析 {@code yyyy-MM-dd HH:mm:ss}。
     *
     * <p>刻意**不**在这里兜"任意格式"：查询参数格式不对时应该明确报错，
     * 而不是静默按别的格式解析出一个用户没预期的区间（那会查出错误的数据还说成功了）。
     */

}
