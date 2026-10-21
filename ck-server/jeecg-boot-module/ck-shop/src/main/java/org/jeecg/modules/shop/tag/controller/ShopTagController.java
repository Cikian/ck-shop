package org.jeecg.modules.shop.tag.controller;

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
import org.jeecg.modules.shop.tag.entity.ShopTag;
import org.jeecg.modules.shop.tag.entity.ShopTagTranslation;
import org.jeecg.modules.shop.tag.entity.dto.TagDTO;
import org.jeecg.modules.shop.tag.entity.vo.TagListVO;
import org.jeecg.modules.shop.tag.mapper.ShopTagTranslationMapper;
import org.jeecg.modules.shop.tag.service.IShopTagService;
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
 * 标签管理
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Tag(name = "标签管理")
@RestController
@RequestMapping("/shop/tag")
@Slf4j
public class ShopTagController extends JeecgController<ShopTag, IShopTagService> {

    @Autowired
    private IShopTagService shopTagService;

    @Autowired
    private ShopTagTranslationMapper tagTranslationMapper;

    @Operation(summary = "标签-分页列表查询")
    @RequiresPermissions("shop-tag:list")
    @GetMapping(value = "/list")
    public Result<IPage<TagListVO>> queryPageList(ShopTag shopTag,
                                                 @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                 @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                 HttpServletRequest req) {
        QueryWrapper<ShopTag> queryWrapper = QueryGenerator.initQueryWrapper(shopTag, req.getParameterMap());
        queryWrapper.orderByDesc("create_time");
        Page<ShopTag> page = new Page<>(pageNo, pageSize);
        IPage<ShopTag> pageList = shopTagService.page(page, queryWrapper);

        List<String> tagIds = pageList.getRecords().stream().map(ShopTag::getId).collect(Collectors.toList());
        Map<String, List<ShopTagTranslation>> translationMap = tagIds.isEmpty()
                ? Collections.emptyMap()
                : tagTranslationMapper.selectByTagIds(tagIds).stream()
                        .collect(Collectors.groupingBy(ShopTagTranslation::getTagId));

        List<TagListVO> voList = new ArrayList<>();
        for (ShopTag tag : pageList.getRecords()) {
            TagListVO vo = new TagListVO();
            BeanUtils.copyProperties(tag, vo);
            vo.setTranslations(translationMap.getOrDefault(tag.getId(), new ArrayList<>()));
            voList.add(vo);
        }
        Page<TagListVO> result = new Page<>(pageList.getCurrent(), pageList.getSize(), pageList.getTotal());
        result.setRecords(voList);
        return Result.OK(result);
    }

    /**
     * 全部启用标签（商品编辑页下拉用，不分页）
     */
    @Operation(summary = "标签-全部启用标签")
    @GetMapping(value = "/all")
    public Result<List<TagListVO>> all() {
        QueryWrapper<ShopTag> wrapper = new QueryWrapper<>();
        wrapper.eq("is_active", "Y").orderByDesc("create_time");
        List<ShopTag> tags = shopTagService.list(wrapper);

        List<String> tagIds = tags.stream().map(ShopTag::getId).collect(Collectors.toList());
        Map<String, List<ShopTagTranslation>> translationMap = tagIds.isEmpty()
                ? Collections.emptyMap()
                : tagTranslationMapper.selectByTagIds(tagIds).stream()
                        .collect(Collectors.groupingBy(ShopTagTranslation::getTagId));

        List<TagListVO> voList = new ArrayList<>();
        for (ShopTag tag : tags) {
            TagListVO vo = new TagListVO();
            BeanUtils.copyProperties(tag, vo);
            vo.setTranslations(translationMap.getOrDefault(tag.getId(), new ArrayList<>()));
            voList.add(vo);
        }
        return Result.OK(voList);
    }

    @AutoLog(value = "标签-添加")
    @Operation(summary = "标签-添加")
    @RequiresPermissions("shop-tag:add")
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody TagDTO tag) {
        shopTagService.saveTag(tag);
        return Result.OK("添加成功！");
    }

    @AutoLog(value = "标签-编辑")
    @Operation(summary = "标签-编辑")
    @RequiresPermissions("shop-tag:edit")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<String> edit(@RequestBody TagDTO tag) {
        shopTagService.updateTag(tag);
        return Result.OK("编辑成功!");
    }

    @AutoLog(value = "标签-通过id删除")
    @Operation(summary = "标签-通过id删除")
    @RequiresPermissions("shop-tag:delete")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name = "id", required = true) String id) {
        shopTagService.removeTag(id);
        return Result.OK("删除成功!");
    }

    @AutoLog(value = "标签-批量删除")
    @Operation(summary = "标签-批量删除")
    @RequiresPermissions("shop-tag:deleteBatch")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        shopTagService.removeTags(Arrays.asList(ids.split(",")));
        return Result.OK("批量删除成功!");
    }

    @Operation(summary = "标签-通过id查询")
    @RequiresPermissions("shop-tag:list")
    @GetMapping(value = "/queryById")
    public Result<TagDTO> queryById(@RequestParam(name = "id", required = true) String id) {
        return Result.OK(shopTagService.getDtoById(id));
    }

    @RequiresPermissions("shop-tag:exportXls")
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, ShopTag shopTag) {
        return super.exportXls(request, shopTag, ShopTag.class, "标签");
    }

    @RequiresPermissions("shop-tag:importExcel")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, ShopTag.class);
    }
}
