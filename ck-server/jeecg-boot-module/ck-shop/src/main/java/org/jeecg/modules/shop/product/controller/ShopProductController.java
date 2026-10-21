package org.jeecg.modules.shop.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.modules.shop.common.util.ShopExportSupport;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.dto.ProductDTO;
import org.jeecg.modules.shop.product.entity.dto.ProductQuery;
import org.jeecg.modules.shop.product.entity.vo.ProductDetailVO;
import org.jeecg.modules.shop.product.entity.vo.ProductListVO;
import org.jeecg.modules.shop.product.entity.vo.ProductSkuVO;
import org.jeecg.modules.shop.product.service.IShopProductService;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
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

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 商品管理
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Tag(name = "商品管理")
@RestController
@RequestMapping("/shop/product")
@Slf4j
public class ShopProductController extends JeecgController<ShopProduct, IShopProductService> {

    @Autowired
    private IShopProductService shopProductService;

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    @Autowired
    private ShopExportSupport exportSupport;

    /**
     * 分页列表查询
     */
    @Operation(summary = "商品-分页列表查询")
    @RequiresPermissions("shop-product:list")
    @GetMapping(value = "/list")
    public Result<IPage<ProductListVO>> queryPageList(ProductQuery query,
                                                     @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                     @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                     HttpServletRequest req) {
        String language = resolveLanguage(req);
        IPage<ProductListVO> pageList = shopProductService.queryPageList(query, pageNo, pageSize, language);
        return Result.OK(pageList);
    }

    /**
     * 添加
     */
    @AutoLog(value = "商品-添加")
    @Operation(summary = "商品-添加")
    @RequiresPermissions("shop-product:add")
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody ProductDTO product) {
        // 返回新商品ID：前端"保存并继续编辑"要据此回填，不能只回一句提示语
        return Result.OK("添加成功！", shopProductService.saveProduct(product));
    }

    /**
     * 编辑
     */
    @AutoLog(value = "商品-编辑")
    @Operation(summary = "商品-编辑")
    @RequiresPermissions("shop-product:edit")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<String> edit(@RequestBody ProductDTO product) {
        // 与新增保持一致的返回契约：result 恒为商品ID，绝不放提示语
        return Result.OK("编辑成功!", shopProductService.updateProduct(product));
    }

    /**
     * 上下架
     */
    @AutoLog(value = "商品-上下架")
    @Operation(summary = "商品-上下架")
    @RequiresPermissions("shop-product:edit")
    @RequestMapping(value = "/updateStatus", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<String> updateStatus(@RequestParam(name = "id") String id,
                                       @RequestParam(name = "status") String status) {
        shopProductService.updateStatus(id, status);
        return Result.OK("Y".equals(status) ? "上架成功!" : "下架成功!");
    }

    /**
     * 通过id删除（软删除）
     */
    @AutoLog(value = "商品-通过id删除")
    @Operation(summary = "商品-通过id删除")
    @RequiresPermissions("shop-product:delete")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name = "id", required = true) String id) {
        shopProductService.removeProduct(id);
        return Result.OK("删除成功!");
    }

    /**
     * 批量删除（软删除）
     */
    @AutoLog(value = "商品-批量删除")
    @Operation(summary = "商品-批量删除")
    @RequiresPermissions("shop-product:deleteBatch")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        shopProductService.removeProducts(Arrays.asList(ids.split(",")));
        return Result.OK("批量删除成功!");
    }

    /**
     * 通过id查询（编辑页一次性取全）
     */
    @Operation(summary = "商品-通过id查询")
    @RequiresPermissions("shop-product:list")
    @GetMapping(value = "/queryById")
    public Result<ProductDetailVO> queryById(@RequestParam(name = "id", required = true) String id,
                                             HttpServletRequest req) {
        return Result.OK(shopProductService.getDetailById(id, resolveLanguage(req)));
    }

    /**
     * 查询某商品的 SKU 组合（列表页展开行 / SKU 库存查看）
     */
    @Operation(summary = "商品-SKU组合查询")
    @RequiresPermissions("shop-product:list")
    @GetMapping(value = "/skuList")
    public Result<List<ProductSkuVO>> skuList(@RequestParam(name = "id", required = true) String id) {
        return Result.OK(shopProductService.listSkuByProductId(id));
    }

    /**
     * 导出excel（带数据可见域）
     *
     * 注：不能直接用 {@code super.exportXls} —— 它只用 QueryGenerator 拼条件，
     * 没有 sys_org_code 过滤，会把所有部门的商品导出去（列表有可见域、导出却没有）。
     */
    @RequiresPermissions("shop-product:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, ShopProduct shopProduct) {
        // 与列表同一套可见域：店铺筛选（storeId）先解析成部门，再与账号可见域取交集
        String storeOrgCode = orgScopeResolver.resolveStoreOrgCode(request.getParameter("storeId"));
        List<String> scope = orgScopeResolver.mergeScope(storeOrgCode, orgScopeResolver.resolveVisibleOrgCodes());
        return exportSupport.export(request, shopProduct, ShopProduct.class, "商品", shopProductService,
                w -> orgScopeResolver.applyScope(w, scope));
    }

    /**
     * 通过excel导入数据
     *
     * <p><b>刻意不再调用框架的 {@code super.importExcel}</b>：
     * 它只做 {@code ExcelImportUtil.importExcel} + {@code service.saveBatch(list)}，
     * 既不校验归属也不落到调用方可写的部门 —— Excel 里的 {@code sys_org_code} 列是
     * **上传方可控**的，一个门店运营只要把该列填成别家店铺的部门编码，
     * 就能把商品直接写进别的店铺（多店铺隔离被绕过，且列表里还看不见自己的数据）。
     *
     * <p>这里改成手工导入：逐行先把归属收敛成"调用方真正可写的部门"
     * （{@link ShopOrgScopeResolver#resolveWritableOrgCode}：无权限时直接抛错、
     * 该部门还必须绑定到启用店铺），再批量落库。行数、返回文案与框架保持一致。
     */
    @AutoLog(value = "商品-导入")
    @RequiresPermissions("shop-product:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        if (!(request instanceof MultipartHttpServletRequest)) {
            return Result.error("文件导入失败:请使用 multipart/form-data 上传 Excel");
        }
        Map<String, MultipartFile> fileMap = ((MultipartHttpServletRequest) request).getFileMap();
        if (fileMap.isEmpty()) {
            return Result.error("文件导入失败:未获取到上传文件");
        }

        int total = 0;
        try {
            for (MultipartFile file : fileMap.values()) {
                // 与框架 super.importExcel 完全相同的解析参数，保证"哪些行会被导入"不变
                ImportParams params = new ImportParams();
                params.setTitleRows(2);
                params.setHeadRows(1);
                params.setNeedSave(false);

                List<ShopProduct> rows;
                try (InputStream in = file.getInputStream()) {
                    rows = ExcelImportUtil.importExcel(in, ShopProduct.class, params);
                }
                if (rows == null || rows.isEmpty()) {
                    continue;
                }

                for (ShopProduct row : rows) {
                    if (row == null) {
                        continue;
                    }
                    // 主键交给框架/数据库生成，导入只负责内容
                    row.setId(null);
                    row.setDelFlag(null);
                    // ★ 归属收敛：Excel 里填了别的部门 -> 这里直接抛异常拒绝整批导入
                    row.setSysOrgCode(orgScopeResolver.resolveWritableOrgCode(row.getSysOrgCode(), null));
                }
                shopProductService.saveBatch(rows);
                total += rows.size();
            }
        } catch (JeecgBootException e) {
            return Result.error("文件导入失败:" + e.getMessage());
        } catch (Exception e) {
            log.error("商品导入失败", e);
            String msg = e.getMessage();
            if (msg != null && msg.contains("Duplicate entry")) {
                return Result.error("文件导入失败:有重复数据！");
            }
            return Result.error("文件导入失败:" + msg);
        }
        return Result.ok("文件导入成功！数据行数：" + total);
    }

    /**
     * 解析语言：优先 x-language，其次 Accept-Language，兜底 zh
     */
    private String resolveLanguage(HttpServletRequest request) {
        String language = request.getHeader("x-language");
        if (language == null || language.isEmpty()) {
            language = request.getHeader("Accept-Language");
        }
        if (language == null || language.isEmpty()) {
            return "zh";
        }
        // 形如 zh-CN / en-US 时截断到主语言
        if (language.contains(",")) {
            language = language.split(",")[0];
        }
        if (language.contains("-")) {
            language = language.split("-")[0];
        }
        return language.trim().toLowerCase();
    }
}
