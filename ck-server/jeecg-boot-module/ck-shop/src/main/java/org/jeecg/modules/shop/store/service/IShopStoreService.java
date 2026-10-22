package org.jeecg.modules.shop.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.shop.store.entity.ShopStore;

import java.util.List;

/**
 * <p>
 * 店铺 Service
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */
public interface IShopStoreService extends IService<ShopStore> {

    /**
     * 新增店铺（校验编码唯一）
     */
    String saveStore(ShopStore store);

    /**
     * 编辑店铺（校验编码唯一 + **对象级授权**）
     *
     * <p>非 {@code shop-store:manage-all} 账号只能编辑"绑定部门落在本人可见域内"的店铺，
     * 且不能修改绑定部门 / 域名 / 状态。
     */
    boolean updateStore(ShopStore store);

    /**
     * Excel 导入：整个文件一个事务，任一行失败全部回滚。
     *
     * @param rows 已解析的店铺行（编码为空的行会被跳过）
     * @return [新增数, 更新数]
     */
    int[] importStores(List<ShopStore> rows);

    /**
     * 删除店铺（对象级授权：不能删别的部门的店铺）
     */
    boolean deleteStore(String id);

    /**
     * 批量删除店铺（逐个做对象级授权；任何一条越权则整体失败，不做"部分删除"）
     */
    boolean deleteStores(List<String> ids);

    /**
     * 全部启用中的店铺（前端站点下拉、解析器兜底用）
     */
    List<ShopStore> listEnabled();
}
