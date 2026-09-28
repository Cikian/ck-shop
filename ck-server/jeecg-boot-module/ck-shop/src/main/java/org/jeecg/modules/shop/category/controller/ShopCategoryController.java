package org.jeecg.modules.shop.category.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.shop.category.entity.ShopCategory;
import org.jeecg.modules.shop.category.entity.dto.CategoryDTO;
import org.jeecg.modules.shop.category.service.IShopCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.Arrays;

/**
 * <p>
 * 商品分类
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-08-03
 */

@Tag(name = "商品分类")
@RestController
@RequestMapping("/shop/category")
@Slf4j
public class ShopCategoryController extends JeecgController<ShopCategory, IShopCategoryService> {
    @Autowired
    private IShopCategoryService shopCategoryService;

    /**
     * 分页列表查询
     *
     * @param shopCategory
     * @param pageNo
     * @param pageSize
     * @param req
     * @return
     */
    //@AutoLog(value = "商品分类-分页列表查询")
    @Operation(summary = "商品分类-分页列表查询")
    @GetMapping(value = "/list")
    public Result<IPage<ShopCategory>> queryPageList(ShopCategory shopCategory,
                                                     @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                     @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                     HttpServletRequest req) {


        QueryWrapper<ShopCategory> queryWrapper = QueryGenerator.initQueryWrapper(shopCategory, req.getParameterMap());
        Page<ShopCategory> page = new Page<ShopCategory>(pageNo, pageSize);
        IPage<ShopCategory> pageList = shopCategoryService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    /**
     * 添加
     *
     * @param category
     * @return
     */
    @AutoLog(value = "商品分类-添加")
    @Operation(summary = "商品分类-添加")
    @RequiresPermissions("shop-category:add")
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody CategoryDTO category) {
        shopCategoryService.save(category);
        return Result.OK("添加成功！");
    }

    /**
     * 编辑
     *
     * @param category
     * @return
     */
    @AutoLog(value = "商品分类-编辑")
    @Operation(summary = "商品分类-编辑")
    @RequiresPermissions("shop-category:edit")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<String> edit(@RequestBody CategoryDTO category) {
        shopCategoryService.updateById(category);
        return Result.OK("编辑成功!");
    }

    /**
     * 通过id删除
     *
     * @param id
     * @return
     */
    @AutoLog(value = "商品分类-通过id删除")
    @Operation(summary = "商品分类-通过id删除")
    @RequiresPermissions("shop-category:delete")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name = "id", required = true) String id) {
        shopCategoryService.removeById(id);
        return Result.OK("删除成功!");
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @AutoLog(value = "商品分类-批量删除")
    @Operation(summary = "商品分类-批量删除")
    @RequiresPermissions("shop-category:deleteBatch")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        this.shopCategoryService.removeByIds(Arrays.asList(ids.split(",")));
        return Result.OK("批量删除成功!");
    }

    /**
     * 通过id查询
     *
     * @param id
     * @return
     */
    //@AutoLog(value = "商品分类-通过id查询")
    @Operation(summary = "商品分类-通过id查询")
    @GetMapping(value = "/queryById")
    public Result<CategoryDTO> queryById(@RequestParam(name = "id", required = true) String id) {
        CategoryDTO shopCategory = shopCategoryService.getDtoById(id);
        if (shopCategory == null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(shopCategory);
    }

    /**
     * 导出excel
     *
     * @param request
     * @param shopCategory
     */
    @RequiresPermissions("shop-category:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, ShopCategory shopCategory) {
        return super.exportXls(request, shopCategory, ShopCategory.class, "商品分类");
    }

    /**
     * 通过excel导入数据
     *
     * @param request
     * @param response
     * @return
     */
    @RequiresPermissions("shop-category:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, ShopCategory.class);
    }

}
