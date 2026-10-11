package org.jeecg.modules.shop.product.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.dto.ProductDTO;
import org.jeecg.modules.shop.product.entity.dto.ProductQuery;
import org.jeecg.modules.shop.product.entity.vo.ProductDetailVO;
import org.jeecg.modules.shop.product.entity.vo.ProductListVO;
import org.jeecg.modules.shop.product.entity.vo.ProductSkuVO;
import org.jeecg.modules.shop.product.entity.vo.StoreProductCardVO;
import org.jeecg.modules.shop.product.entity.vo.StoreProductDetailVO;

import java.util.List;

/**
 * <p>
 * 商品 Service
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */
public interface IShopProductService extends IService<ShopProduct> {

    /**
     * 管理端分页查询（含分类名、品牌名、默认语言标题、标签）
     *
     * @param query    筛选条件
     * @param pageNo   页码
     * @param pageSize 每页条数
     * @param language 界面语言，用于标题与名称回退
     * @return 分页结果
     */
    IPage<ProductListVO> queryPageList(ProductQuery query, Integer pageNo, Integer pageSize, String language);

    /**
     * 新增商品（含多语言、图集、标签、规格、SKU组合）
     *
     * @return 新商品ID，供前端"保存并继续编辑"回填
     */
    String saveProduct(ProductDTO dto);

    /**
     * 编辑商品（含多语言、图集、标签、规格、SKU组合，全量覆盖子表）
     *
     * @return 商品ID（与入参一致），保持与 saveProduct 一致的返回契约
     */
    String updateProduct(ProductDTO dto);

    /**
     * 软删除商品并级联删除子表数据
     */
    boolean removeProduct(String id);

    /**
     * 批量软删除商品
     */
    boolean removeProducts(List<String> ids);

    /**
     * 上下架
     *
     * @param id     商品ID
     * @param status Y-上架, N-下架
     */
    boolean updateStatus(String id, String status);

    /**
     * 管理端商品详情（编辑页一次性取全）
     *
     * @param id       商品ID
     * @param language 界面语言，用于标题与名称回退；为空时回退 zh
     */
    ProductDetailVO getDetailById(String id, String language);

    /**
     * 查询某商品的 SKU 组合列表（列表页展开行）
     */
    List<ProductSkuVO> listSkuByProductId(String productId);

    /**
     * 店铺端商品分页。
     * storeId 必须由 StoreResolver 解析得到（请求头 X-Store-Code 或域名），
     * 一律以 ck_shop_product_store 为驱动表过滤，防止跨店展示。
     */
    IPage<StoreProductCardVO> queryStorePageList(String storeId, String language, String categoryId, String brandId,
                                                 String keyword, Integer pageNo, Integer pageSize);

    /**
     * 店铺端商品详情（含规格树与存在的SKU组合，供前端做组合置灰）。
     *
     * @param idOrSpuCode 商品主键或 spu_code（前台路由用 spu_code，分享链接更短）
     */
    StoreProductDetailVO getStoreDetail(String storeId, String idOrSpuCode, String language);
}
