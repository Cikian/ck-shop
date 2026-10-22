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
     * 管理端分页查询（含分类名、默认语言标题、标签）。
     *
     * 可见域：调用方若带 storeId，会先解析成该店铺绑定部门的 org_code；
     * 再与当前账号的可见部门域取交集。运营只能看到本部门商品，管理员不限。
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
     * Excel 导入：整个文件一个事务，逐行复用 saveProduct（默认值/校验/SPU 编码生成一致）。
     *
     * @param rows 已解析并转换好的商品行
     * @return 成功导入条数
     */
    int importProducts(java.util.List<ProductDTO> rows);

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
     *
     * @param orgCode 店铺绑定部门的 org_code（由 StoreResolver → ck_shop_store.depart_id → sys_depart.org_code 解析而来）。
     *                直接查 ck_shop_product 的归属列，**不再 join 任何上架关系表**——
     *                一商品只属于一个店铺，归属列本身就是店铺身份。
     * @param sortBy  排序方式，取值白名单（大小写不敏感，未识别或为空时使用默认排序）：
     *                <ul>
     *                  <li>{@code sales}      销量优先：month_sales_count DESC, sales_count DESC, sort_order ASC</li>
     *                  <li>{@code newest}     最新上架：listed_at DESC, create_time DESC</li>
     *                  <li>{@code price_asc}  价格升序：min_price ASC</li>
     *                  <li>{@code price_desc} 价格降序：min_price DESC</li>
     *                  <li>其他/空            默认：is_recommend DESC, sort_order ASC, create_time DESC</li>
     *                </ul>
     */
    IPage<StoreProductCardVO> queryStorePageList(String orgCode, String language, String categoryId,
                                                 String keyword, Integer pageNo, Integer pageSize, String sortBy);

    /**
     * 店铺端商品详情（含规格树与存在的SKU组合，供前端做组合置灰）。
     *
     * @param orgCode     店铺绑定部门的 org_code；商品归属必须落在该部门的可见域内
     * @param idOrSpuCode 商品主键或 spu_code（前台路由用 spu_code，分享链接更短）
     */
    StoreProductDetailVO getStoreDetail(String orgCode, String idOrSpuCode, String language);

    /**
     * 店铺端：按商品主键取**单张**商品卡片，并完成可见性校验。
     *
     * <p>存在的意义是给"以商品为对象的写操作"（收藏 / 取消收藏）一个统一的准入判断：
     * 商品必须存在、未软删除、归属落在该店铺部门可见域内、且处于上架状态；
     * 任一不满足都抛业务异常（{@link org.jeecg.common.exception.JeecgBootException}）。
     *
     * <p>之所以不直接调 {@link #getStoreDetail}：详情要额外装配规格树与全部 SKU 组合，
     * 而收藏只需要"这件商品能不能被这个店铺的用户看到"，用详情接口等于每次收藏都多查 3 张表。
     *
     * @param orgCode   店铺绑定部门的 org_code
     * @param productId 商品主键（不接受 spu_code：写操作必须锁定唯一主键）
     * @param language  界面语言
     * @return 商品卡片（含价格 / 库存 / 收藏数等展示字段）
     */
    StoreProductCardVO getStoreCard(String orgCode, String productId, String language);

    /**
     * 店铺端：按商品主键批量取卡片（我的收藏列表用）。
     *
     * <p>过滤条件与店铺端列表**完全一致**（可见部门 + status='Y' + 未软删除），
     * 因此"收藏了但商品已下架"的商品会自动从收藏列表消失，不会出现点了就报错的僵尸条目。
     * 返回顺序不保证，调用方按自己的顺序（如收藏时间倒序）重排。
     *
     * @param productIds 商品主键集合；为空直接返回空列表
     * @param orgCode    店铺绑定部门的 org_code
     * @param language   界面语言
     */
    List<StoreProductCardVO> listStoreCardsByIds(List<String> productIds, String orgCode, String language);
}
