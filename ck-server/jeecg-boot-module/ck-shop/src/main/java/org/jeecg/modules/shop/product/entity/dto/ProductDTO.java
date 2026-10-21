package org.jeecg.modules.shop.product.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.ShopProductImage;
import org.jeecg.modules.shop.product.entity.ShopProductSkuCombination;
import org.jeecg.modules.shop.product.entity.ShopProductSpec;
import org.jeecg.modules.shop.product.entity.ShopProductTranslation;

import java.util.List;

/**
 * <p>
 * 商品新增&修改 DTO
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class ProductDTO extends ShopProduct {
    /**
     * 多语言文案
     */
    private List<ShopProductTranslation> translations;
    /**
     * 图集（含主图）
     */
    private List<ShopProductImage> images;
    /**
     * 关联标签ID集合
     */
    private List<String> tagIds;
    /**
     * 规格定义（含可选值）
     */
    private List<SpecDTO> specs;
    /**
     * SKU 规格组合（笛卡尔积的每一行）
     */
    private List<SkuCombinationDTO> combinations;
    /**
     * 商品详情模块（多语言）。
     * <p>替换语义（契约 2.1）：<b>null/缺省 = 不动现有数据；[] = 清空该商品全部语言的模块；
     * 非空 = 整体替换</b>（先物理删除全部语言，再按数组顺序插入并重排 sortOrder）。
     * null 的语义是为兼容旧客户端/局部保存而特意保留的——旧版管理端不带这个字段时不能把新数据抹掉。</p>
     */
    private List<DetailBlockDTO> detailBlocks;
    /**
     * 规格参数 KV（多语言），替换语义同 {@link #detailBlocks}。
     */
    private List<ParamDTO> params;
}
