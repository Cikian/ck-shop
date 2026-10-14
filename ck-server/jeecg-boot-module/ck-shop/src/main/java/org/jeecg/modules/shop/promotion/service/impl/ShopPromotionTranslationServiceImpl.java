package org.jeecg.modules.shop.promotion.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.shop.promotion.entity.ShopPromotionTranslation;
import org.jeecg.modules.shop.promotion.mapper.ShopPromotionTranslationMapper;
import org.jeecg.modules.shop.promotion.service.IShopPromotionTranslationService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 促销活动多语言表 Service 实现
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */
@Service
public class ShopPromotionTranslationServiceImpl
        extends ServiceImpl<ShopPromotionTranslationMapper, ShopPromotionTranslation>
        implements IShopPromotionTranslationService {
}
