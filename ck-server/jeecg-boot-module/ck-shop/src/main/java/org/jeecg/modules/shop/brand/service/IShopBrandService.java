package org.jeecg.modules.shop.brand.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.brand.entity.ShopBrand;
import org.jeecg.modules.shop.brand.entity.dto.BrandDTO;

import java.util.List;

/**
 * <p>
 * 品牌 Service
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */
public interface IShopBrandService extends IService<ShopBrand> {

    /**
     * 新增品牌（含多语言）
     */
    boolean saveBrand(BrandDTO dto);

    /**
     * 编辑品牌（含多语言）
     */
    boolean updateBrand(BrandDTO dto);

    /**
     * 删除品牌（含多语言与软删除）
     */
    boolean removeBrand(String id);

    /**
     * 批量删除品牌
     */
    boolean removeBrands(List<String> ids);

    /**
     * 按ID查询品牌（含多语言）
     */
    BrandDTO getDtoById(String id);
}
