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
     * 编辑店铺（校验编码唯一）
     */
    boolean updateStore(ShopStore store);

    /**
     * 全部启用中的店铺（前端站点下拉、解析器兜底用）
     */
    List<ShopStore> listEnabled();
}
