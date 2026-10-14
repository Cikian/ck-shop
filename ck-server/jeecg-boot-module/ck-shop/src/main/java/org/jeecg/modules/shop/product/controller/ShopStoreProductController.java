package org.jeecg.modules.shop.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.jeecg.modules.shop.store.StoreResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

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
 * 多店铺改造后，商品的归属列是部门编码 {@code sys_org_code}，
 * 因此这里由 {@link ShopOrgScopeResolver} 把"店铺"翻译成"店铺绑定部门的 org_code"，
 * 业务层一律只用 org_code 过滤——少一层店铺-商品的间接关系，就少一处漏条件的风险。
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

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    /**
     * 店铺端商品分页列表（当前店铺 + 已上架）
     */
    @IgnoreAuth
    @Operation(summary = "店铺端-商品分页列表")
    @GetMapping(value = "/list")
    public Result<IPage<StoreProductCardVO>> list(@RequestParam(name = "lang", required = false) String lang,
                                                  @RequestParam(name = "categoryId", required = false) String categoryId,
                                                  @RequestParam(name = "keyword", required = false) String keyword,
                                                  @Parameter(description = "排序方式: sales-销量 / newest-最新上架 / price_asc-价格升序 / price_desc-价格降序，留空或不识别时用默认排序")
                                                  @RequestParam(name = "sortBy", required = false) String sortBy,
                                                  @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                  @RequestParam(name = "pageSize", defaultValue = "12") Integer pageSize,
                                                  HttpServletRequest req) {
        String language = lang != null && !lang.isEmpty() ? lang : resolveLanguage(req);
        String orgCode = orgScopeResolver.resolveStoreOrgCodeOrNull(storeResolver.resolve(req));
        if (orgCode == null) {
            // 店铺没绑部门（或部门被删）属于**数据缺失**，不是配置错误：
            // 返回空目录而不是 500，整站仍可访问；日志里已经 warn，运维能发现
            return Result.OK(emptyPage(pageNo, pageSize));
        }
        return Result.OK(shopProductService.queryStorePageList(
                orgCode, language, categoryId, keyword, pageNo, pageSize, sortBy));
    }

    /** 店铺归属缺失时返回的空分页（与正常分页结构一致，前端无需特殊处理） */
    private IPage<StoreProductCardVO> emptyPage(Integer pageNo, Integer pageSize) {
        Page<StoreProductCardVO> empty = new Page<>(pageNo == null ? 1 : pageNo, pageSize == null ? 12 : pageSize, 0);
        empty.setRecords(new ArrayList<>());
        return empty;
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
        String orgCode = orgScopeResolver.resolveStoreOrgCodeOrNull(storeResolver.resolve(req));
        if (orgCode == null) {
            // 同列表：归属缺失时按"商品不存在"返回业务错误，而不是 500
            return Result.error("商品在当前店铺不存在或已下架");
        }
        return Result.OK(shopProductService.getStoreDetail(orgCode, key, language));
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
