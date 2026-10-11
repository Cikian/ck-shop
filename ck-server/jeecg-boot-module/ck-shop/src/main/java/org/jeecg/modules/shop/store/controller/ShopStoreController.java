package org.jeecg.modules.shop.store.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.jeecg.modules.shop.store.service.IShopStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * 店铺管理（一个管理端、多个前台站点）
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Tag(name = "店铺管理")
@RestController
@RequestMapping("/shop/store")
@Slf4j
public class ShopStoreController extends JeecgController<ShopStore, IShopStoreService> {

    @Autowired
    private IShopStoreService shopStoreService;

    /**
     * 分页列表
     */
    @Operation(summary = "店铺-分页列表查询")
    @GetMapping(value = "/list")
    public Result<IPage<ShopStore>> queryPageList(ShopStore shopStore,
                                                  @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                  @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                  HttpServletRequest req) {
        QueryWrapper<ShopStore> queryWrapper = QueryGenerator.initQueryWrapper(shopStore, req.getParameterMap());
        queryWrapper.orderByAsc("sort_order");
        Page<ShopStore> page = new Page<>(pageNo, pageSize);
        IPage<ShopStore> pageList = shopStoreService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    /**
     * 全部启用店铺（商品表单的「销售渠道」选择、前端站点下拉用）
     */
    @Operation(summary = "店铺-启用列表")
    @GetMapping(value = "/all")
    public Result<List<ShopStore>> all() {
        return Result.OK(shopStoreService.listEnabled());
    }

    /**
     * 新增
     */
    @AutoLog(value = "店铺-添加")
    @Operation(summary = "店铺-添加")
    @RequiresPermissions("shop-store:add")
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody ShopStore shopStore) {
        return Result.OK("添加成功！", shopStoreService.saveStore(shopStore));
    }

    /**
     * 编辑
     */
    @AutoLog(value = "店铺-编辑")
    @Operation(summary = "店铺-编辑")
    @RequiresPermissions("shop-store:edit")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<String> edit(@RequestBody ShopStore shopStore) {
        if (shopStore.getId() == null || shopStore.getId().isEmpty()) {
            return Result.error("缺少店铺ID");
        }
        shopStoreService.updateStore(shopStore);
        // 与其他模块保持一致：返回主键，前端才能安全地"保存并继续编辑"
        return Result.OK("编辑成功!", shopStore.getId());
    }

    /**
     * 通过id查询
     */
    @Operation(summary = "店铺-通过id查询")
    @GetMapping(value = "/queryById")
    public Result<ShopStore> queryById(@RequestParam(name = "id", required = true) String id) {
        ShopStore shopStore = shopStoreService.getById(id);
        if (shopStore == null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(shopStore);
    }

    /**
     * 通过id删除（逻辑删除：已删除的店铺编码不会被回收，避免老配置指向别的店铺）
     */
    @AutoLog(value = "店铺-通过id删除")
    @Operation(summary = "店铺-通过id删除")
    @RequiresPermissions("shop-store:delete")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name = "id", required = true) String id) {
        shopStoreService.removeById(id);
        return Result.OK("删除成功!");
    }

    /**
     * 批量删除
     */
    @AutoLog(value = "店铺-批量删除")
    @Operation(summary = "店铺-批量删除")
    @RequiresPermissions("shop-store:deleteBatch")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        List<String> idList = Arrays.asList(ids.split(","));
        shopStoreService.removeByIds(idList);
        return Result.OK("批量删除成功！");
    }

    /**
     * 导出
     */
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, ShopStore shopStore) {
        return super.exportXls(request, shopStore, ShopStore.class, "店铺");
    }

    /**
     * 导入
     */
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, ShopStore.class);
    }
}
