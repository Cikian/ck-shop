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
import org.jeecg.modules.shop.category.entity.ShopCategoryTranslation;
import org.jeecg.modules.shop.category.service.IShopCategoryTranslationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.Arrays;

/**
 * <p>
 * 分类多语言表
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-08-03
 */

@Tag(name = "分类多语言表")
@RestController
@RequestMapping("/shop/category-translation")
@Slf4j
public class ShopCategoryTranslationController extends JeecgController<ShopCategoryTranslation, IShopCategoryTranslationService> {
    @Autowired
    private IShopCategoryTranslationService shopCategoryTranslationService;

    /**
     * 分页列表查询
     *
     * @param shopCategoryTranslation
     * @param pageNo
     * @param pageSize
     * @param req
     * @return
     */
    //@AutoLog(value = "分类多语言表-分页列表查询")
    @Operation(summary = "分类多语言表-分页列表查询")
    @GetMapping(value = "/list")
    public Result<IPage<ShopCategoryTranslation>> queryPageList(ShopCategoryTranslation shopCategoryTranslation,
                                                                @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                                @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                                HttpServletRequest req) {


        QueryWrapper<ShopCategoryTranslation> queryWrapper = QueryGenerator.initQueryWrapper(shopCategoryTranslation, req.getParameterMap());
        Page<ShopCategoryTranslation> page = new Page<ShopCategoryTranslation>(pageNo, pageSize);
        IPage<ShopCategoryTranslation> pageList = shopCategoryTranslationService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    /**
     * 添加
     *
     * @param shopCategoryTranslation
     * @return
     */
    @AutoLog(value = "分类多语言表-添加")
    @Operation(summary = "分类多语言表-添加")
    @RequiresPermissions("category:ck_shop_category_translation:add")
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody ShopCategoryTranslation shopCategoryTranslation) {
        shopCategoryTranslationService.save(shopCategoryTranslation);

        return Result.OK("添加成功！");
    }

    /**
     * 编辑
     *
     * @param shopCategoryTranslation
     * @return
     */
    @AutoLog(value = "分类多语言表-编辑")
    @Operation(summary = "分类多语言表-编辑")
    @RequiresPermissions("category:ck_shop_category_translation:edit")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<String> edit(@RequestBody ShopCategoryTranslation shopCategoryTranslation) {
        shopCategoryTranslationService.updateById(shopCategoryTranslation);
        return Result.OK("编辑成功!");
    }

    /**
     * 通过id删除
     *
     * @param id
     * @return
     */
    @AutoLog(value = "分类多语言表-通过id删除")
    @Operation(summary = "分类多语言表-通过id删除")
    @RequiresPermissions("category:ck_shop_category_translation:delete")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name = "id", required = true) String id) {
        shopCategoryTranslationService.removeById(id);
        return Result.OK("删除成功!");
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @AutoLog(value = "分类多语言表-批量删除")
    @Operation(summary = "分类多语言表-批量删除")
    @RequiresPermissions("category:ck_shop_category_translation:deleteBatch")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        this.shopCategoryTranslationService.removeByIds(Arrays.asList(ids.split(",")));
        return Result.OK("批量删除成功!");
    }

    /**
     * 通过id查询
     *
     * @param id
     * @return
     */
    //@AutoLog(value = "分类多语言表-通过id查询")
    @Operation(summary = "分类多语言表-通过id查询")
    @GetMapping(value = "/queryById")
    public Result<ShopCategoryTranslation> queryById(@RequestParam(name = "id", required = true) String id) {
        ShopCategoryTranslation shopCategoryTranslation = shopCategoryTranslationService.getById(id);
        if (shopCategoryTranslation == null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(shopCategoryTranslation);
    }

    /**
     * 导出excel
     *
     * @param request
     * @param shopCategoryTranslation
     */
    @RequiresPermissions("category:ck_shop_category_translation:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, ShopCategoryTranslation shopCategoryTranslation) {
        return super.exportXls(request, shopCategoryTranslation, ShopCategoryTranslation.class, "分类多语言表");
    }

    /**
     * 通过excel导入数据
     *
     * @param request
     * @param response
     * @return
     */
    @RequiresPermissions("category:ck_shop_category_translation:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, ShopCategoryTranslation.class);
    }

}
