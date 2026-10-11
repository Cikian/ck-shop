package org.jeecg.modules.shop.store.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.jeecg.modules.shop.store.mapper.ShopStoreMapper;
import org.jeecg.modules.shop.store.service.IShopStoreService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 店铺 Service 实现
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */
@Service
public class ShopStoreServiceImpl extends ServiceImpl<ShopStoreMapper, ShopStore> implements IShopStoreService {

    @Override
    public String saveStore(ShopStore store) {
        checkCode(store, null);
        return this.save(store) ? store.getId() : null;
    }

    @Override
    public boolean updateStore(ShopStore store) {
        checkCode(store, store.getId());
        return this.updateById(store);
    }

    @Override
    public List<ShopStore> listEnabled() {
        return this.list(new LambdaQueryWrapper<ShopStore>()
                .eq(ShopStore::getStatus, "Y")
                .orderByAsc(ShopStore::getSortOrder));
    }

    /**
     * 校验编码唯一。
     * 刻意**把已删除行也算进来**（用裸 SQL 查，绕开 @TableLogic）：
     * 店铺编码会写进前端站点的服务端配置，一旦被回收后复用，
     * 老配置就可能指向另一个店铺——这与 spu_code 的处理原则一致。
     */
    private void checkCode(ShopStore store, String selfId) {
        if (StringUtils.isBlank(store.getCode())) {
            throw new JeecgBootException("店铺编码不能为空");
        }
        if (StringUtils.isBlank(store.getName())) {
            throw new JeecgBootException("店铺名称不能为空");
        }
        // 编码只允许字母数字下划线中划线：它要作为配置项与请求头传递
        if (!store.getCode().matches("^[A-Za-z0-9_-]{1,50}$")) {
            throw new JeecgBootException("店铺编码只能包含字母、数字、下划线和中划线");
        }
        String existId = baseMapper.selectAnyIdByCode(store.getCode());
        if (existId != null && !existId.equals(selfId)) {
            throw new JeecgBootException("店铺编码已存在：" + store.getCode());
        }
        if (StringUtils.isBlank(store.getStatus())) {
            store.setStatus("Y");
        }
        if (store.getSortOrder() == null) {
            store.setSortOrder(0);
        }
    }
}
