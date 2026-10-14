package org.jeecg.modules.shop.promotion.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.promotion.entity.ShopPromotion;

/**
 * <p>
 * 促销活动列表 VO（管理端列表页使用）
 * </p>
 *
 * 继承主表实体：实体上已有 {@code @Excel} 与时间格式注解，导出列表时不必再维护一份字段镜像。
 * 只额外补列表需要的派生字段（storeName / productCount）。
 *
 * <p>注意：列表**不返回**「未开始 / 进行中 / 已结束」——
 * 服务端时间与浏览器时区/时钟可能不一致，前端按 {@code startTime}/{@code endTime} 现算
 * 才符合用户看到的时间，服务端算出来的状态反而容易和倒计时对不上。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class PromotionListVO extends ShopPromotion {

    /**
     * 关联商品数（**按 product_id 去重**）。
     *
     * <p>关联表一行 = 一个 SKU，同一商品挂多个 SKU 会有多行；
     * 直接用行数当"商品数"会偏大，运营会以为挂了 3 个商品其实是 1 个的 3 个 SKU。
     */
    @Schema(description = "关联商品数(按 product_id 去重)")
    private Integer productCount;
}
