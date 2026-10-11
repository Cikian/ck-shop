package org.jeecg.modules.shop.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.shop.product.entity.ShopProduct;
import org.jeecg.modules.shop.product.entity.vo.StoreProductCardVO;
import org.jeecg.modules.shop.product.entity.vo.StoreProductDetailVO;
import org.jeecg.modules.shop.product.service.IShopProductService;
import org.jeecg.modules.shop.store.StoreResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 店铺端（前台）商品查询
 * 只暴露**当前店铺**的已上架商品，无需登录即可访问。
 * 规格与 SKU 组合一并返回，由前端据此把"不存在 / 已停售"的规格值置灰。
 * </p>
 *
 * 店铺身份来自 {@link StoreResolver}：请求头 X-Store-Code 优先（由前台站点服务端注入，
 * 不出现在用户可见 URL 中），其次按域名兜底。解析不到就报错，**绝不返回全量**。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Tag(name = "店铺端-商品")
@RestController
@RequestMapping("/shop/product/store")
@Slf4j
public class ShopStoreProductController extends JeecgController<ShopProduct, IShopProductService> {

    @Autowired
    private IShopProductService shopProductService;

    @Autowired
    private StoreResolver storeResolver;

    /**
     * 店铺端商品分页列表（当前店铺 + 已上架）
     */
    @IgnoreAuth
    @Operation(summary = "店铺端-商品分页列表")
    @GetMapping(value = "/list")
    public Result<IPage<StoreProductCardVO>> list(@RequestParam(name = "lang", required = false) String lang,
                                                  @RequestParam(name = "categoryId", required = false) String categoryId,
                                                  @RequestParam(name = "brandId", required = false) String brandId,
                                                  @RequestParam(name = "keyword", required = false) String keyword,
                                                  @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                  @RequestParam(name = "pageSize", defaultValue = "12") Integer pageSize,
                                                  HttpServletRequest req) {
        String language = lang != null && !lang.isEmpty() ? lang : resolveLanguage(req);
        String storeId = storeResolver.resolveStoreId(req);
        return Result.OK(shopProductService.queryStorePageList(
                storeId, language, categoryId, brandId, keyword, pageNo, pageSize));
    }

    /**
     * 店铺端商品详情（含规格树与存在的 SKU 组合）
     *
     * @param id      商品主键
     * @param spuCode 商品编码（前台路由用它，分享链接更短）
     */
    @IgnoreAuth
    @Operation(summary = "店铺端-商品详情")
    @GetMapping(value = "/detail")
    public Result<StoreProductDetailVO> detail(@RequestParam(name = "id", required = false) String id,
                                               @RequestParam(name = "spuCode", required = false) String spuCode,
                                               @RequestParam(name = "lang", required = false) String lang,
                                               HttpServletRequest req) {
        String key = id != null && !id.isEmpty() ? id : spuCode;
        if (key == null || key.isEmpty()) {
            return Result.error("缺少商品标识（id 或 spuCode）");
        }
        String language = lang != null && !lang.isEmpty() ? lang : resolveLanguage(req);
        String storeId = storeResolver.resolveStoreId(req);
        return Result.OK(shopProductService.getStoreDetail(storeId, key, language));
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
