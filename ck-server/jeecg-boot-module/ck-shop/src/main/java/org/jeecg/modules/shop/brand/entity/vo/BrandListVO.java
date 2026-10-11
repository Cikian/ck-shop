package org.jeecg.modules.shop.brand.entity.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.brand.entity.ShopBrand;
import org.jeecg.modules.shop.brand.entity.ShopBrandTranslation;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 品牌列表 VO：在实体基础上带出多语言名称，
 * 避免管理端列表为每行单独请求 queryById
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class BrandListVO extends ShopBrand {

    /**
     * 多语言文案
     */
    private List<ShopBrandTranslation> translations = new ArrayList<>();
}
