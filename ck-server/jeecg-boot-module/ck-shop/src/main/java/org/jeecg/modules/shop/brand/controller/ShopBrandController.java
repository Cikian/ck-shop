package org.jeecg.modules.shop.brand.controller;

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
import org.jeecg.modules.shop.brand.entity.ShopBrand;
import org.jeecg.modules.shop.brand.entity.ShopBrandTranslation;
import org.jeecg.modules.shop.brand.entity.dto.BrandDTO;
import org.jeecg.modules.shop.brand.entity.vo.BrandListVO;
import org.jeecg.modules.shop.brand.mapper.ShopBrandTranslationMapper;
import org.jeecg.modules.shop.brand.service.IShopBrandService;
import org.springframework.beans.BeanUtils;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 品牌管理
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Tag(name = "品牌管理")
@RestController
@RequestMapping("/shop/brand")
@Slf4j
public class ShopBrandController extends JeecgController<ShopBrand, IShopBrandService> {

    @Autowired
    private IShopBrandService shopBrandService;

    @Autowired
    private ShopBrandTranslationMapper brandTranslationMapper;

    @Operation(summary = "品牌-分页列表查询")
    @GetMapping(value = "/list")
    public Result<IPage<BrandListVO>> queryPageList(ShopBrand shopBrand,
                                                   @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                   @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                   HttpServletRequest req) {
        QueryWrapper<ShopBrand> queryWrapper = QueryGenerator.initQueryWrapper(shopBrand, req.getParameterMap());
        queryWrapper.orderByAsc("sort_order").orderByDesc("create_time");
        Page<ShopBrand> page = new Page<>(pageNo, pageSize);
        IPage<ShopBrand> pageList = shopBrandService.page(page, queryWrapper);

        List<String> brandIds = pageList.getRecords().stream()
                .map(ShopBrand::getId).collect(Collectors.toList());
        Map<String, List<ShopBrandTranslation>> translationMap = brandIds.isEmpty()
                ? Collections.emptyMap()
                : brandTranslationMapper.selectByBrandIds(brandIds).stream()
                        .collect(Collectors.groupingBy(ShopBrandTranslation::getBrandId));

        List<BrandListVO> voList = new ArrayList<>();
        for (ShopBrand brand : pageList.getRecords()) {
            BrandListVO vo = new BrandListVO();
            BeanUtils.copyProperties(brand, vo);
            vo.setTranslations(translationMap.getOrDefault(brand.getId(), new ArrayList<>()));
            voList.add(vo);
        }
        Page<BrandListVO> result = new Page<>(pageList.getCurrent(), pageList.getSize(), pageList.getTotal());
        result.setRecords(voList);
        return Result.OK(result);
    }

    @AutoLog(value = "品牌-添加")
    @Operation(summary = "品牌-添加")
    @RequiresPermissions("shop-brand:add")
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody BrandDTO brand) {
        shopBrandService.saveBrand(brand);
        return Result.OK("添加成功！");
    }

    @AutoLog(value = "品牌-编辑")
    @Operation(summary = "品牌-编辑")
    @RequiresPermissions("shop-brand:edit")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<String> edit(@RequestBody BrandDTO brand) {
        shopBrandService.updateBrand(brand);
        return Result.OK("编辑成功!");
    }

    @AutoLog(value = "品牌-通过id删除")
    @Operation(summary = "品牌-通过id删除")
    @RequiresPermissions("shop-brand:delete")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name = "id", required = true) String id) {
        shopBrandService.removeBrand(id);
        return Result.OK("删除成功!");
    }

    @AutoLog(value = "品牌-批量删除")
    @Operation(summary = "品牌-批量删除")
    @RequiresPermissions("shop-brand:deleteBatch")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        shopBrandService.removeBrands(Arrays.asList(ids.split(",")));
        return Result.OK("批量删除成功!");
    }

    @Operation(summary = "品牌-通过id查询")
    @GetMapping(value = "/queryById")
    public Result<BrandDTO> queryById(@RequestParam(name = "id", required = true) String id) {
        return Result.OK(shopBrandService.getDtoById(id));
    }

    @RequiresPermissions("shop-brand:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, ShopBrand shopBrand) {
        return super.exportXls(request, shopBrand, ShopBrand.class, "品牌");
    }

    @RequiresPermissions("shop-brand:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, ShopBrand.class);
    }
}
