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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

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
        translationMapper.insert(translations);
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
        }
        translationMapper.insert(translations);

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
}
