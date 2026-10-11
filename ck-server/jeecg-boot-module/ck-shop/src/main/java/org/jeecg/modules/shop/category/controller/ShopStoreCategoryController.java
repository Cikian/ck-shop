package org.jeecg.modules.shop.category.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.shop.category.entity.ShopCategory;
import org.jeecg.modules.shop.category.entity.vo.StoreCategoryVO;
import org.jeecg.modules.shop.category.service.IShopCategoryService;
import org.jeecg.modules.shop.store.StoreResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 店铺端（前台）分类
 * </p>
 *
 * 与商品接口同一套店铺解析规则：请求头 X-Store-Code 优先（由前台站点服务端注入），
 * 其次按域名兜底；解析不到就报错，绝不返回"全部店铺的分类"。
 *
 * 返回的结构里已经按店铺过滤好了，前端不需要（也无法）自己过滤——
 * 这是刻意的：把可见域收口在服务端，前端就没有"忘了过滤"的机会。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Tag(name = "店铺端-分类")
@RestController
@RequestMapping("/shop/store/category")
@Slf4j
public class ShopStoreCategoryController {

    @Autowired
    private IShopCategoryService shopCategoryService;

    @Autowired
    private StoreResolver storeResolver;

    /**
     * 分类树（仅当前店铺可见的分类，含每个分类下本店的已上架商品数）
     */
    @IgnoreAuth
    @Operation(summary = "店铺端-分类树")
    @GetMapping(value = "/tree")
    public Result<List<StoreCategoryVO>> tree(@RequestParam(name = "lang", required = false) String lang,
                                              HttpServletRequest req) {
        String language = lang != null && !lang.isEmpty() ? lang : resolveLanguage(req);
        String storeId = storeResolver.resolveStoreId(req);
        return Result.OK(shopCategoryService.buildStoreTree(storeId, language));
    }

    private String resolveLanguage(HttpServletRequest request) {
        String language = request.getHeader("x-language");
        if (language == null || language.isEmpty()) {
            language = request.getHeader("Accept-Language");
        }
        if (language == null || language.isEmpty()) {
            return "zh";
        }
        if (language.contains(",")) {
            language = language.split(",")[0];
        }
        if (language.contains("-")) {
            language = language.split("-")[0];
        }
        return language.trim().toLowerCase();
    }
}
