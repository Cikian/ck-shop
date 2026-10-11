package org.jeecg.modules.shop.brand.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.brand.entity.ShopBrand;
import org.jeecg.modules.shop.brand.entity.ShopBrandTranslation;
import org.jeecg.modules.shop.brand.entity.dto.BrandDTO;
import org.jeecg.modules.shop.brand.mapper.ShopBrandMapper;
import org.jeecg.modules.shop.brand.mapper.ShopBrandTranslationMapper;
import org.jeecg.modules.shop.brand.service.IShopBrandService;
import org.jeecg.modules.shop.common.util.ShopAuditUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 品牌 Service 实现
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Service
public class ShopBrandServiceImpl extends ServiceImpl<ShopBrandMapper, ShopBrand> implements IShopBrandService {

    @Autowired
    private ShopBrandTranslationMapper translationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveBrand(BrandDTO dto) {
        checkCodeUnique(dto.getCode(), null);

        ShopBrand brand = new ShopBrand();
        BeanUtils.copyProperties(dto, brand);
        brand.setId(IdWorker.getIdStr());
        String username = ShopAuditUtils.currentUsername();
        Date now = new Date();
        brand.setCreateBy(username).setCreateTime(now).setUpdateBy(username).setUpdateTime(now);
        if (StringUtils.isBlank(brand.getIsShow())) {
            brand.setIsShow("Y");
        }

        super.save(brand);
        saveTranslations(brand.getId(), dto.getTranslations(), username, now);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateBrand(BrandDTO dto) {
        if (StringUtils.isBlank(dto.getId())) {
            throw new JeecgBootException("未找到品牌主键!");
        }
        checkCodeUnique(dto.getCode(), dto.getId());

        ShopBrand brand = new ShopBrand();
        BeanUtils.copyProperties(dto, brand);
        brand.setCreateBy(null).setCreateTime(null);
        brand.setUpdateBy(ShopAuditUtils.currentUsername()).setUpdateTime(new Date());
        super.updateById(brand);

        String brandId = dto.getId();
        baseMapper.deleteTranslationsByBrandId(brandId);
        saveTranslations(brandId, dto.getTranslations(), ShopAuditUtils.currentUsername(), new Date());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeBrand(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("请选择要删除的品牌!");
        }
        super.removeById(id);
        baseMapper.deleteTranslationsByBrandId(id);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeBrands(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            throw new JeecgBootException("请至少选择一条数据!");
        }
        super.removeByIds(ids);
        baseMapper.deleteTranslationsByBrandIds(ids);
        return true;
    }

    @Override
    public BrandDTO getDtoById(String id) {
        ShopBrand brand = super.getById(id);
        if (Objects.isNull(brand)) {
            throw new JeecgBootException("未找到数据!");
        }
        BrandDTO dto = new BrandDTO();
        BeanUtils.copyProperties(brand, dto);
        dto.setTranslations(translationMapper.selectByBrandId(id));
        return dto;
    }

    private void saveTranslations(String brandId, List<ShopBrandTranslation> translations, String username, Date now) {
        if (CollectionUtils.isEmpty(translations)) {
            return;
        }
        for (ShopBrandTranslation translation : translations) {
            translation.setId(null);
            translation.setBrandId(brandId);
            translation.setCreateBy(username);
            translation.setCreateTime(now);
            translation.setUpdateBy(username);
            translation.setUpdateTime(now);
            translationMapper.insert(translation);
        }
    }

    private void checkCodeUnique(String code, String excludeId) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        ShopBrand exist = lambdaQuery().eq(ShopBrand::getCode, code).one();
        if (exist != null && !exist.getId().equals(excludeId)) {
            throw new JeecgBootException("品牌编码已存在：" + code);
        }
    }
}
