package org.jeecg.modules.shop.category.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.category.entity.ShopCategory;
import org.jeecg.modules.shop.category.entity.ShopCategoryTranslation;
import org.jeecg.modules.shop.category.entity.dto.CategoryDTO;
import org.jeecg.modules.shop.category.mapper.ShopCategoryMapper;
import org.jeecg.modules.shop.category.mapper.ShopCategoryTranslationMapper;
import org.jeecg.modules.shop.category.service.IShopCategoryService;
import org.jeecg.modules.shop.category.entity.vo.StoreCategoryVO;
import org.jeecg.modules.shop.product.mapper.ShopProductMapper;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品分类
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-08-03
 */

@Service
public class ShopCategoryServiceImpl extends ServiceImpl<ShopCategoryMapper, ShopCategory> implements IShopCategoryService {
    @Autowired
    private ShopCategoryTranslationMapper translationMapper;
    /** 分类页的"本部门已上架商品数"直接来自商品表的归属列（不再有商品-店铺关系表） */
    @Autowired
    private ShopProductMapper productMapper;
    /** 归属部门/可见域的唯一解析出口 */
    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    @Override
    @Transactional
    public boolean save(CategoryDTO dto) {
        ShopCategory category = new ShopCategory();
        BeanUtils.copyProperties(dto, category);

        String mainId = IdWorker.getIdStr();
        category.setId(mainId);
        // 归属部门：默认取操作人当前部门；运营不允许指定别的部门（后端硬校验），管理员可跨部门
        category.setSysOrgCode(orgScopeResolver.resolveWritableOrgCode(dto.getSysOrgCode(), null));
        // 删除标记永远由平台控制：DTO 直接 copy 自实体，若不清洗，客户端带 delFlag=1 就能
        // "新建即不可见"，或绕过 shop-category:delete 权限把分类逻辑删除
        category.setDelFlag(null);
        applyParent(category);

        List<ShopCategoryTranslation> translations = dto.getTranslations();
        if (translations == null) {
            translations = new ArrayList<>();
        }
        for (ShopCategoryTranslation translation : translations) {
            translation.setCategoryId(mainId);
        }

        super.save(category);
        // BaseMapper 只有单条 insert(T)，没有 insert(List)，必须逐条插入
        for (ShopCategoryTranslation translation : translations) {
            translation.setId(null);
            translationMapper.insert(translation);
        }
        return true;
    }

    @Override
    @Transactional
    public boolean updateById(CategoryDTO dto) {
        ShopCategory category = new ShopCategory();
        BeanUtils.copyProperties(dto, category);

        String mainId = category.getId();
        if (StringUtils.isEmpty(mainId)) {
            throw new JeecgBootException("未找到主键!");
        }
        ShopCategory exist = super.getById(mainId);
        if (Objects.isNull(exist)) {
            throw new JeecgBootException("分类不存在或已删除!");
        }
        // 越权防线：接口可被直接按 id 调用，列表过滤不算数
        orgScopeResolver.checkAccessible(exist.getSysOrgCode(), "分类");
        String newOrgCode = orgScopeResolver.resolveWritableOrgCode(dto.getSysOrgCode(), exist.getSysOrgCode());
        category.setSysOrgCode(newOrgCode);
        category.setDelFlag(null);

        /*
         * 改归属前先看有没有商品还挂在这个分类上：
         * 分类改名/改排序无所谓，但一改归属，原店铺商品的 category_id 就指向了别部门的分类，
         * 前台分类树里没有这个节点 → 商品在"按分类浏览"里消失（列表还显示分类名，掩盖问题）。
         */
        if (!StringUtils.equals(newOrgCode, exist.getSysOrgCode())) {
            int refCount = productMapper.countAliveByCategoryId(mainId);
            if (refCount > 0) {
                throw new JeecgBootException("该分类下还有 " + refCount + " 个商品，不能修改归属；"
                        + "请先把这些商品改到目标店铺的分类，或先移除分类归属");
            }
        }
        applyParent(category);

        super.updateById(category);
        translationMapper.deleteByCategoryId(mainId);

        List<ShopCategoryTranslation> translations = dto.getTranslations();
        if (translations == null) {
            translations = new ArrayList<>();
        }
        for (ShopCategoryTranslation translation : translations) {
            translation.setCategoryId(mainId);
            translation.setId(null);
            translationMapper.insert(translation);
        }

        return true;
    }

    /**
     * 校验并规范化父级分类。
     *
     * <p>以前完全没校验：把父级指向别部门的分类后，前台 {@code buildStoreTree} 会因
     * "父节点不在可见范围"而**静默丢弃**该节点，分类在前台凭空消失，运营完全无感。
     * 这里把三种非法情况都拦下：父级不存在/已删除、父级不同部门、父级指向自己。
     * 同时自动算 level（一级 1，往下 +1），前端不必关心层级。
     */
    private void applyParent(ShopCategory category) {
        String parentId = StringUtils.trimToNull(category.getParentId());
        if (parentId == null || "0".equals(parentId)) {
            category.setParentId("0");
            category.setLevel(1);
            return;
        }
        if (parentId.equals(category.getId())) {
            throw new JeecgBootException("父级分类不能是自己");
        }
        ShopCategory parent = super.getById(parentId);
        if (parent == null) {
            throw new JeecgBootException("父级分类不存在或已删除：" + parentId);
        }
        if (!StringUtils.equals(parent.getSysOrgCode(), category.getSysOrgCode())) {
            throw new JeecgBootException("父级分类必须与当前分类属于同一个店铺（部门）");
        }
        int parentLevel = parent.getLevel() == null ? 1 : parent.getLevel();
        if (parentLevel >= 3) {
            throw new JeecgBootException("分类最多 3 级，当前父级已是第 " + parentLevel + " 级");
        }
        // 禁止把父级挂到自己的后代上（否则树里会出现环，构建时整支被丢弃）
        List<String> descendants = collectDescendantIds(category.getId());
        if (descendants.contains(parentId)) {
            throw new JeecgBootException("父级分类不能是自己的下级分类");
        }
        category.setLevel(parentLevel + 1);
    }

    /** 收集某个分类的全部后代 id（分类最多 3 级，内存展开即可） */
    private List<String> collectDescendantIds(String categoryId) {
        if (StringUtils.isBlank(categoryId)) {
            return new ArrayList<>();
        }
        List<ShopCategory> all = super.list();
        List<String> result = new ArrayList<>();
        Set<String> frontier = new HashSet<>();
        frontier.add(categoryId);
        while (!frontier.isEmpty()) {
            Set<String> next = new HashSet<>();
            for (ShopCategory item : all) {
                if (item.getParentId() != null && frontier.contains(item.getParentId())
                        && !result.contains(item.getId())) {
                    result.add(item.getId());
                    next.add(item.getId());
                }
            }
            frontier = next;
        }
        return result;
    }

    @Override
    @Transactional
    public boolean removeById(String id) {
        ShopCategory exist = super.getById(id);
        if (Objects.isNull(exist)) {
            throw new JeecgBootException("分类不存在或已删除!");
        }
        orgScopeResolver.checkAccessible(exist.getSysOrgCode(), "分类");
        checkRemovable(Collections.singletonList(id));
        super.removeById(id);
        translationMapper.deleteByCategoryId(id);
        return true;
    }

    @Override
    @Transactional
    public boolean removeByIds(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            throw new JeecgBootException("请至少选择一条数据!");
        }
        List<String> distinctIds = ids.stream().filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
        List<ShopCategory> found = this.listByIds(distinctIds);
        // 与单条删除口径一致：有 id 不存在就报错，不做静默的"部分成功"
        if (found.size() < distinctIds.size()) {
            Set<String> foundIds = found.stream().map(ShopCategory::getId).collect(Collectors.toSet());
            List<String> missing = distinctIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
            throw new JeecgBootException("部分分类不存在或已删除：" + String.join(",", missing));
        }
        for (ShopCategory item : found) {
            orgScopeResolver.checkAccessible(item.getSysOrgCode(), "分类");
        }
        checkRemovable(distinctIds);
        super.removeByIds(distinctIds);
        translationMapper.deleteBatchCategoryId(distinctIds);
        // 以前这里恒返回 false（返回值契约错误），调用方一旦判断返回值就会把成功当失败
        return true;
    }

    /**
     * 删除前检查引用：还有商品挂在该分类、或还有子分类时拒绝。
     * 否则商品会留下指向已删分类的 category_id，前台分类树里永远找不到它，属"静默孤儿数据"。
     */
    private void checkRemovable(List<String> ids) {
        for (String id : ids) {
            int refCount = productMapper.countAliveByCategoryId(id);
            if (refCount > 0) {
                throw new JeecgBootException("该分类下还有 " + refCount + " 个商品，无法删除；请先移动或删除这些商品");
            }
            long children = this.count(new LambdaQueryWrapper<ShopCategory>().eq(ShopCategory::getParentId, id));
            if (children > 0) {
                throw new JeecgBootException("该分类下还有 " + children + " 个子分类，无法删除；请先删除子分类");
            }
        }
    }

    @Override
    public CategoryDTO getDtoById(String id) {
        ShopCategory category = super.getById(id);
        if (Objects.isNull(category)) {
            throw new JeecgBootException("未找到数据!");
        }
        // 越权防线：与编辑/删除一致（评审发现这里曾漏掉，运营能按 id 读到别部门分类详情）
        orgScopeResolver.checkAccessible(category.getSysOrgCode(), "分类");

        CategoryDTO dto = new CategoryDTO();
        BeanUtils.copyProperties(category, dto);

        List<ShopCategoryTranslation> translations = translationMapper.selectByCategoryId(id);
        dto.setTranslations(translations);
        return dto;
    }

    @Override
    public List<StoreCategoryVO> buildStoreTree(String orgCode, String lang) {
        if (StringUtils.isBlank(orgCode)) {
            // 解析不到店铺归属部门时宁可报错，也绝不返回"全部店铺的分类"
            throw new JeecgBootException("无法确定店铺归属部门，已拒绝查询分类树");
        }
        // 1) 本店（本部门及其下级）可见的分类：is_show='Y' 且未删除，条件写在 SQL 里
        List<ShopCategory> all = baseMapper.selectVisibleByOrg(orgCode);
        if (CollectionUtils.isEmpty(all)) {
            return new ArrayList<>();
        }
        List<String> ids = all.stream().map(ShopCategory::getId).collect(Collectors.toList());

        // 2) 分类名按语言解析，缺失则回退到任意可用语言（再回退内部名）
        Map<String, String> nameById = new HashMap<>();
        List<ShopCategoryTranslation> translations = translationMapper.selectByCategoryIds(ids);
        if (!CollectionUtils.isEmpty(translations)) {
            String wanted = lang == null ? "" : lang.trim().toLowerCase();
            for (ShopCategoryTranslation t : translations) {
                if (t.getLanguageCode() != null && t.getLanguageCode().toLowerCase().startsWith(wanted)
                        && StringUtils.isNotBlank(t.getName())) {
                    nameById.put(t.getCategoryId(), t.getName());
                }
            }
            for (ShopCategoryTranslation t : translations) {
                if (StringUtils.isNotBlank(t.getName())) {
                    nameById.putIfAbsent(t.getCategoryId(), t.getName());
                }
            }
        }

        // 3) 本部门已上架商品数：一次分组查询，绝不在遍历分类时逐个 COUNT
        Map<String, Integer> countByCategory = new HashMap<>();
        for (Map<String, Object> row : productMapper.countCategoryProducts(orgCode)) {
            Object cid = row.get("categoryId");
            Object cnt = row.get("productCount");
            if (cid != null) {
                countByCategory.put(String.valueOf(cid), cnt instanceof Number ? ((Number) cnt).intValue() : 0);
            }
        }

        Map<String, StoreCategoryVO> voById = new LinkedHashMap<>();
        for (ShopCategory category : all) {
            StoreCategoryVO vo = new StoreCategoryVO();
            BeanUtils.copyProperties(category, vo);
            vo.setName(nameById.getOrDefault(category.getId(), category.getName()));
            vo.setProductCount(countByCategory.getOrDefault(category.getId(), 0));
            vo.setChildren(new ArrayList<>());
            voById.put(category.getId(), vo);
        }

        // 4) 组装：父节点不可见的子节点**直接丢弃**，而不是提升为顶级。
        //    否则会出现"某分类从别店的父分类下冒出来"这种跨店串扰。
        List<StoreCategoryVO> roots = new ArrayList<>();
        for (StoreCategoryVO vo : voById.values()) {
            String parentId = vo.getParentId();
            if (StringUtils.isBlank(parentId) || "0".equals(parentId)) {
                roots.add(vo);
                continue;
            }
            StoreCategoryVO parent = voById.get(parentId);
            if (parent == null) {
                continue;
            }
            parent.getChildren().add(vo);
        }

        // 5) 商品数向上汇总：父分类要把子分类的商品算进来
        roots.forEach(this::rollUpCount);
        return roots;
    }

    /**
     * 递归汇总子节点的商品数到父节点（分类最多 3 级，深度可控）
     */
    private int rollUpCount(StoreCategoryVO node) {
        int total = node.getProductCount() == null ? 0 : node.getProductCount();
        for (StoreCategoryVO child : node.getChildren()) {
            total += rollUpCount(child);
        }
        node.setProductCount(total);
        return total;
    }
}
