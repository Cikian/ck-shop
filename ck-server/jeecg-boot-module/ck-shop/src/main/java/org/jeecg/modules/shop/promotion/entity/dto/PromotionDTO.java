package org.jeecg.modules.shop.promotion.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.promotion.entity.ShopPromotion;
import org.jeecg.modules.shop.promotion.entity.ShopPromotionProduct;
import org.jeecg.modules.shop.promotion.entity.ShopPromotionTranslation;

import java.util.List;

/**
 * <p>
 * 促销活动新增&修改&详情 DTO
 * </p>
 *
 * 一个对象带全：主表字段 + 多语言 + 关联商品。
 * 详情接口也返回它，前端"列表 → 编辑页"只需一次请求。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "促销活动新增/编辑/详情")
public class PromotionDTO extends ShopPromotion {

    /**
     * 多语言文案。至少一条，且必须包含 zh（见业务规则）。
     */
    @Schema(description = "多语言文案(至少一条且必须含 zh)")
    private List<ShopPromotionTranslation> translations;

    /**
     * 关联商品。允许为空数组（先建活动后加商品）。
     * {@code promoPrice} 为空 = 按活动类型自动算，**不是 0 元**。
     */
    @Schema(description = "关联商品(promoPrice 为空=自动算, 不是0元)")
    private List<ShopPromotionProduct> products;
}
