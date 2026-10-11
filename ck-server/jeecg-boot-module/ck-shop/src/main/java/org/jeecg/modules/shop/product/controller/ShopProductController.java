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
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.dto.ProductDTO;
import org.jeecg.modules.shop.product.entity.dto.ProductQuery;
import org.jeecg.modules.shop.product.entity.vo.ProductDetailVO;
import org.jeecg.modules.shop.product.entity.vo.ProductListVO;
import org.jeecg.modules.shop.product.entity.vo.ProductSkuVO;
import org.jeecg.modules.shop.product.service.IShopProductService;
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

import java.util.Arrays;
import java.util.List;

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

    /**
     * 分页列表查询
     */
    @Operation(summary = "商品-分页列表查询")
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
    @GetMapping(value = "/queryById")
    public Result<ProductDetailVO> queryById(@RequestParam(name = "id", required = true) String id,
                                             HttpServletRequest req) {
        return Result.OK(shopProductService.getDetailById(id, resolveLanguage(req)));
    }

    /**
     * 查询某商品的 SKU 组合（列表页展开行 / SKU 库存查看）
     */
    @Operation(summary = "商品-SKU组合查询")
    @GetMapping(value = "/skuList")
    public Result<List<ProductSkuVO>> skuList(@RequestParam(name = "id", required = true) String id) {
        return Result.OK(shopProductService.listSkuByProductId(id));
    }

    /**
     * 导出excel
     */
    @RequiresPermissions("shop-product:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, ShopProduct shopProduct) {
        return super.exportXls(request, shopProduct, ShopProduct.class, "商品");
    }

    /**
     * 通过excel导入数据
     */
    @RequiresPermissions("shop-product:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, ShopProduct.class);
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
