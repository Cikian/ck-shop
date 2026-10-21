package org.jeecg.modules.shop.store.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.jeecg.modules.shop.store.entity.vo.StoreOptionVO;
import org.jeecg.modules.shop.store.service.IShopStoreService;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * 分页列表（管理端店铺管理页）
     *
     * <p>必须带 {@code shop-store:list}：这里返回的是完整实体（含 departId/domain/themeConfig），
     * 而 JeecgBoot 没有 URL 级权限过滤器——不写注解就等于"任何登录账号都能列出全部店铺"。
     */
    @Operation(summary = "店铺-分页列表查询")
    @RequiresPermissions("shop-store:list")
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
     *
     * <p><b>只返回安全投影</b>：id/code/name/logoUrl/sortOrder。
     * 不再直出实体，避免把 departId/departOrgCode/domain/themeConfig/createBy 交给调用方
     * （本接口是"任意已登录账号"可调，包括店铺端买家 token）。
     *
     * <p>{@code departOrgCode} 只有 {@code shop-store:manage-all} 持有者才返回：
     * 商品表单需要它把"所属店铺"映射成商品的 sys_org_code，普通运营不需要也不应该看到。
     */
    @Operation(summary = "店铺-启用列表")
    @GetMapping(value = "/all")
    public Result<List<StoreOptionVO>> all() {
        boolean canManageAll = SecurityUtils.getSubject().isPermitted("shop-store:manage-all");
        List<StoreOptionVO> options = shopStoreService.listEnabled().stream()
                .map(store -> new StoreOptionVO()
                        .setId(store.getId())
                        .setCode(store.getCode())
                        .setName(store.getName())
                        .setLogoUrl(store.getLogoUrl())
                        .setSortOrder(store.getSortOrder())
                        .setDepartOrgCode(canManageAll ? store.getDepartOrgCode() : null))
                .collect(Collectors.toList());
        return Result.OK(options);
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
     * 通过id查询（管理端编辑店铺用，返回完整实体）
     */
    @Operation(summary = "店铺-通过id查询")
    @RequiresPermissions("shop-store:list")
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
     *
     * <p>对象级授权在 service 内完成：只能删可见域内的店铺。
     */
    @AutoLog(value = "店铺-通过id删除")
    @Operation(summary = "店铺-通过id删除")
    @RequiresPermissions("shop-store:delete")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name = "id", required = true) String id) {
        shopStoreService.deleteStore(id);
        return Result.OK("删除成功!");
    }

    /**
     * 批量删除（逐个做对象级授权，任一越权整体失败）
     */
    @AutoLog(value = "店铺-批量删除")
    @Operation(summary = "店铺-批量删除")
    @RequiresPermissions("shop-store:deleteBatch")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        List<String> idList = Arrays.asList(ids.split(","));
        shopStoreService.deleteStores(idList);
        return Result.OK("批量删除成功！");
    }

    /**
     * 导出（**必须有 shop-store:exportXls 权限**）
     *
     * 注：以前这里没有权限注解，而 JeecgBoot 并没有 URL 级的权限过滤器
     * （ResourceCheckFilter 定义了但从未注册），等于任何登录用户都能导出全部店铺。
     */
    @AutoLog(value = "店铺-导出")
    @RequiresPermissions("shop-store:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, ShopStore shopStore) {
        return super.exportXls(request, shopStore, ShopStore.class, "店铺");
    }

    /**
     * 导入（**必须有 shop-store:importExcel 权限**）
     *
     * <p>刻意不用框架的 {@code super.importExcel}：它直接走 {@code service.saveBatch}，
     * 会绕过 {@code checkCode}/{@code checkDepart}——可能造出"两个店铺绑同一个部门"或
     * "把店铺绑到公司节点"，从而把整套数据隔离搞坏。这里逐行走 service 的校验：
     * 编码已存在则更新，否则新增。
     */
    @AutoLog(value = "店铺-导入")
    @RequiresPermissions("shop-store:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        if (!(request instanceof MultipartHttpServletRequest)) {
            return Result.error("导入失败：请使用 multipart/form-data 上传 Excel");
        }
        Map<String, MultipartFile> fileMap = ((MultipartHttpServletRequest) request).getFileMap();
        if (fileMap.isEmpty()) {
            return Result.error("导入失败：未获取到上传文件");
        }
        int insertCount = 0;
        int updateCount = 0;
        for (Map.Entry<String, MultipartFile> entity : fileMap.entrySet()) {
            MultipartFile file = entity.getValue();
            ImportParams params = new ImportParams();
            params.setTitleRows(2);
            params.setHeadRows(1);
            try (InputStream in = file.getInputStream()) {
                List<ShopStore> list = ExcelImportUtil.importExcel(in, ShopStore.class, params);
                for (ShopStore item : list) {
                    if (item == null || StringUtils.isBlank(item.getCode())) {
                        continue;
                    }
                    // 主键交给 service 生成；导入只负责"按编码新增或更新"
                    item.setId(null);
                    item.setDelFlag(null);
                    if (StringUtils.isBlank(item.getStatus())) {
                        item.setStatus("Y");
                    }
                    ShopStore exist = shopStoreService.getOne(new LambdaQueryWrapper<ShopStore>()
                            .eq(ShopStore::getCode, item.getCode()).last("LIMIT 1"), false);
                    if (exist == null) {
                        shopStoreService.saveStore(item);
                        insertCount++;
                    } else {
                        item.setId(exist.getId());
                        shopStoreService.updateStore(item);
                        updateCount++;
                    }
                }
            } catch (JeecgBootException e) {
                // 校验失败（部门不存在/不是部门节点/部门被别的店铺占用等）直接回给用户
                return Result.error("导入失败：" + e.getMessage());
            } catch (Exception e) {
                log.error("店铺导入失败", e);
                String msg = e.getMessage();
                if (msg != null && msg.contains("Duplicate entry")) {
                    return Result.error("导入失败：有重复数据（店铺编码重复）");
                }
                return Result.error("导入失败：" + msg);
            }
        }
        return Result.OK("文件导入成功！新增 " + insertCount + " 条，更新 " + updateCount + " 条");
    }
}

