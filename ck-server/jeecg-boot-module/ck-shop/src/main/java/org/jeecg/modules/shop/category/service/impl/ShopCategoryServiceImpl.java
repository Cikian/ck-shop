package org.jeecg.modules.shop.category.service.impl;

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
import org.jeecg.modules.shop.product.mapper.ShopProductStoreMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    /** 分类页的"本店已上架商品数"来自商品-店铺关系表 */
    @Autowired
    private ShopProductStoreMapper productStoreMapper;

    @Override
    @Transactional
    public boolean save(CategoryDTO dto) {
        ShopCategory category = new ShopCategory();
        BeanUtils.copyProperties(dto, category);

        String mainId = IdWorker.getIdStr();
        category.setId(mainId);

        List<ShopCategoryTranslation> translations = dto.getTranslations();
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

        super.updateById(category);
        translationMapper.deleteByCategoryId(mainId);

        List<ShopCategoryTranslation> translations = dto.getTranslations();
        for (ShopCategoryTranslation translation : translations) {
            translation.setCategoryId(mainId);
            translation.setId(null);
            translationMapper.insert(translation);
        }

        return true;
    }

    @Override
    @Transactional
    public boolean removeById(String id) {
        super.removeById(id);
        translationMapper.deleteByCategoryId(id);
        return true;
    }

    @Override
    public boolean removeByIds(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            throw new JeecgBootException("请至少选择一条数据!");
        }
        super.removeByIds(ids);
        translationMapper.deleteBatchCategoryId(ids);
        return false;
    }

    @Override
    public CategoryDTO getDtoById(String id) {
        ShopCategory category = super.getById(id);
        if (Objects.isNull(category)) {
            throw new JeecgBootException("未找到数据!");
        }

        CategoryDTO dto = new CategoryDTO();
        BeanUtils.copyProperties(category, dto);

        List<ShopCategoryTranslation> translations = translationMapper.selectByCategoryId(id);
        dto.setTranslations(translations);
        return dto;
    }

    @Override
    public List<StoreCategoryVO> buildStoreTree(String storeId, String lang) {
        // 1) 本店可见的分类：全局共享(store_id 为空) + 本店专属，且 is_show='Y'
        List<ShopCategory> all = baseMapper.selectVisibleByStore(storeId);
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

        // 3) 本店已上架商品数：一次分组查询，绝不在遍历分类时逐个 COUNT
        Map<String, Integer> countByCategory = new HashMap<>();
        for (Map<String, Object> row : productStoreMapper.countCategoryProducts(storeId)) {
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
