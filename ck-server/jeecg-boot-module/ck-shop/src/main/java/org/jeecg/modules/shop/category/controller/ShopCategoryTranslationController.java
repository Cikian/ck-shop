package org.jeecg.modules.shop.category.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.shop.category.entity.ShopCategory;
import org.jeecg.modules.shop.category.entity.ShopCategoryTranslation;
import org.jeecg.modules.shop.category.mapper.ShopCategoryMapper;
import org.jeecg.modules.shop.category.service.IShopCategoryTranslationService;
import org.jeecg.modules.shop.common.util.ShopExportSupport;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    @Autowired
    private ShopCategoryMapper shopCategoryMapper;

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    @Autowired
    private ShopExportSupport exportSupport;

    /**
     * 分类文案表本身没有归属列，可见域只能借由**父分类**反查。
     *
     * 这组接口以前完全没有归属校验：list/queryById 连权限注解都没有（任何登录用户可读全部文案），
     * 写接口用的是代码生成器遗留的权限码 —— 相当于绕开分类隔离的一个侧门。
     */
    private List<String> visibleCategoryIds() {
        List<String> scope = orgScopeResolver.resolveVisibleOrgCodes();
        List<ShopCategory> all = shopCategoryMapper.selectList(null);
        if (scope == null) {
            return all.stream().map(ShopCategory::getId).collect(Collectors.toList());
        }
        return all.stream()
                .filter(c -> orgScopeResolver.inScope(c.getSysOrgCode(), scope))
                .map(ShopCategory::getId)
                .collect(Collectors.toList());
    }

    /** 目标分类必须在本人可见域内 */
    private void assertCategoryAccessible(String categoryId) {
        if (StringUtils.isBlank(categoryId)) {
            throw new JeecgBootException("缺少分类ID");
        }
        ShopCategory category = shopCategoryMapper.selectById(categoryId);
        if (category == null) {
            throw new JeecgBootException("分类不存在或已删除：" + categoryId);
        }
        orgScopeResolver.checkAccessible(category.getSysOrgCode(), "分类文案");
    }

    /** 按文案主键反查其父分类并校验可见性 */
    private ShopCategoryTranslation loadAccessibleTranslation(String id) {
        ShopCategoryTranslation translation = shopCategoryTranslationService.getById(id);
        if (translation == null) {
            throw new JeecgBootException("分类文案不存在或已删除：" + id);
        }
        assertCategoryAccessible(translation.getCategoryId());
        return translation;
    }

    /**
     * 分页列表查询（仅在本人可见的分类范围内）
     */
    //@AutoLog(value = "分类多语言表-分页列表查询")
    @Operation(summary = "分类多语言表-分页列表查询")
    @GetMapping(value = "/list")
    public Result<IPage<ShopCategoryTranslation>> queryPageList(ShopCategoryTranslation shopCategoryTranslation,
                                                                @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                                @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                                HttpServletRequest req) {


        QueryWrapper<ShopCategoryTranslation> queryWrapper = QueryGenerator.initQueryWrapper(shopCategoryTranslation, req.getParameterMap());
        List<String> visibleIds = visibleCategoryIds();
        if (visibleIds.isEmpty()) {
            queryWrapper.apply("1 = 0");
        } else {
            queryWrapper.in("category_id", visibleIds);
        }
        Page<ShopCategoryTranslation> page = new Page<ShopCategoryTranslation>(pageNo, pageSize);
        IPage<ShopCategoryTranslation> pageList = shopCategoryTranslationService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    /**
     * 添加
     */
    @AutoLog(value = "分类多语言表-添加")
    @Operation(summary = "分类多语言表-添加")
    @RequiresPermissions("category:ck_shop_category_translation:add")
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody ShopCategoryTranslation shopCategoryTranslation) {
        assertCategoryAccessible(shopCategoryTranslation.getCategoryId());
        shopCategoryTranslationService.save(shopCategoryTranslation);

        return Result.OK("添加成功！");
    }

    /**
     * 编辑
     */
    @AutoLog(value = "分类多语言表-编辑")
    @Operation(summary = "分类多语言表-编辑")
    @RequiresPermissions("category:ck_shop_category_translation:edit")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<String> edit(@RequestBody ShopCategoryTranslation shopCategoryTranslation) {
        // 原记录与新归属都要校验：否则可以把别店的文案"改到"本店，或反过来
        loadAccessibleTranslation(shopCategoryTranslation.getId());
        assertCategoryAccessible(shopCategoryTranslation.getCategoryId());
        shopCategoryTranslationService.updateById(shopCategoryTranslation);
        return Result.OK("编辑成功!");
    }

    /**
     * 通过id删除
     */
    @AutoLog(value = "分类多语言表-通过id删除")
    @Operation(summary = "分类多语言表-通过id删除")
    @RequiresPermissions("category:ck_shop_category_translation:delete")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name = "id", required = true) String id) {
        loadAccessibleTranslation(id);
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
        for (String id : Arrays.asList(ids.split(","))) {
            if (StringUtils.isNotBlank(id)) {
                // 逐条校验：不允许"按 id 批量删掉别店的文案"
                loadAccessibleTranslation(id);
            }
        }
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
        ShopCategoryTranslation shopCategoryTranslation = loadAccessibleTranslation(id);
        return Result.OK(shopCategoryTranslation);
    }

    /**
     * 导出excel（只在本人可见的分类范围内）
     */
    @RequiresPermissions("category:ck_shop_category_translation:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, ShopCategoryTranslation shopCategoryTranslation) {
        List<String> visibleIds = visibleCategoryIds();
        return exportSupport.export(request, shopCategoryTranslation, ShopCategoryTranslation.class, "分类多语言表",
                shopCategoryTranslationService,
                w -> {
                    if (visibleIds.isEmpty()) {
                        w.apply("1 = 0");
                    } else {
                        w.in("category_id", visibleIds);
                    }
                });
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
        // 逐行校验归属后再落库：框架的 importExcel 会直接 saveBatch，绕过可见域
        if (!(request instanceof org.springframework.web.multipart.MultipartHttpServletRequest)) {
            return Result.error("导入失败：请使用 multipart/form-data 上传 Excel");
        }
        for (org.springframework.web.multipart.MultipartFile file
                : ((org.springframework.web.multipart.MultipartHttpServletRequest) request).getFileMap().values()) {
            org.jeecgframework.poi.excel.entity.ImportParams params = new org.jeecgframework.poi.excel.entity.ImportParams();
            params.setTitleRows(2);
            params.setHeadRows(1);
            try (java.io.InputStream in = file.getInputStream()) {
                List<ShopCategoryTranslation> list = org.jeecgframework.poi.excel.ExcelImportUtil
                        .importExcel(in, ShopCategoryTranslation.class, params);
                for (ShopCategoryTranslation item : list) {
                    assertCategoryAccessible(item.getCategoryId());
                }
                shopCategoryTranslationService.saveBatch(list);
            } catch (JeecgBootException e) {
                return Result.error("导入失败：" + e.getMessage());
            } catch (Exception e) {
                log.error("分类文案导入失败", e);
                return Result.error("导入失败：" + e.getMessage());
            }
        }
        return Result.OK("文件导入成功！");
    }

}
