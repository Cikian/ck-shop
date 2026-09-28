package org.jeecg.modules.shop.home.controller;


import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.shop.home.service.CommonMongoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 首页轮播图
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026/7/20
 */

@RestController
@RequestMapping("/home/slide")
@Tag(name = "首页轮播图")
public class SlideshowController {
    @Autowired
    private CommonMongoService mongoService;

    @PostMapping
    @Operation(summary = "保存轮播图")
    public Result<?> save(@RequestBody JSONObject params) {
        mongoService.saveSlideshow(params);
        return Result.OK("保存成功");
    }

    @PutMapping
    @Operation(summary = "编辑轮播图（update one）")
    public Result<?> edit(@RequestBody JSONObject params) {
        mongoService.editSlideshow(params);
        return Result.OK("编辑成功");
    }

    @PostMapping("/all")
    @Operation(summary = "全量替换轮播图")
    public Result<?> replaceAll(@RequestBody List<Map<String, Object>> params) {
        mongoService.replaceAll(params);
        return Result.OK("保存成功");
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除轮播图（单条）")
    @RequiresPermissions("ck:shop:slideshow:delete")
    public Result<?> delete(@RequestParam(name = "id") String id) {
        if (StringUtils.isBlank(id)) {
            return Result.error("轮播图主键不能为空");
        }
        mongoService.removeSlideshow(Collections.singletonList(id));
        return Result.OK("删除成功");
    }

    @DeleteMapping("/deleteBatch")
    @Operation(summary = "删除轮播图（批量）")
    @RequiresPermissions("ck:shop:slideshow:deleteBatch")
    public Result<?> deleteBatch(@RequestParam(name = "ids") String ids) {
        if (StringUtils.isBlank(ids)) {
            return Result.error("请选择要删除的轮播图");
        }
        mongoService.removeSlideshow(Arrays.asList(ids.split(",")));
        return Result.OK("删除成功");
    }

    @PutMapping("/sort")
    @Operation(summary = "保存轮播图排序")
    @RequiresPermissions("ck:shop:slideshow:edit")
    public Result<?> saveSort(@RequestBody List<Map<String, Object>> sortList) {
        mongoService.updateSlideshowSort(sortList);
        return Result.OK("排序已保存");
    }

    @GetMapping
    @Operation(summary = "获取对应语言的轮播图")
    public Result<?> list(HttpServletRequest request) {
         Enumeration<String> headerNames = request.getHeaderNames();
        Map<String, String> headerMap = new HashMap<>();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                String value = request.getHeader(name);
                headerMap.put(name, value);
            }
        }
        String lang = request.getHeader("x-language");
        if (lang == null) {
            lang = "en";
        }
        List<Map<String, Object>> maps = mongoService.listSlideshow(lang);
        return Result.OK(maps);
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有轮播图（以language分组）")
    public Result<?> listAll() {
        JSONArray maps = mongoService.getSlideshowGroupByLang();
        return Result.OK(maps);
    }
}
