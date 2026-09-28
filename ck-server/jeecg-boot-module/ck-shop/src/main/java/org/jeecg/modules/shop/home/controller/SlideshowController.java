package org.jeecg.modules.shop.home.controller;


import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.shop.home.service.CommonMongoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
