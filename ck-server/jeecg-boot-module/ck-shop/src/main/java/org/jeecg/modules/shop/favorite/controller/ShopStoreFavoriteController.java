package org.jeecg.modules.shop.favorite.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.shop.favorite.entity.dto.FavoriteToggleDTO;
import org.jeecg.modules.shop.favorite.entity.vo.FavoriteStatusVO;
import org.jeecg.modules.shop.favorite.service.FavoriteContext;
import org.jeecg.modules.shop.favorite.service.IShopFavoriteService;
import org.jeecg.modules.shop.product.entity.vo.StoreProductCardVO;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.jeecg.modules.shop.store.StoreResolver;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 店铺端（前台）商品收藏
 * </p>
 *
 * <b>这个 Controller 刻意不加 {@code @IgnoreAuth}</b>：收藏必须知道"是谁收藏的"，
 * 匿名收藏没有任何意义（也没有地方存）。未登录时由 Shiro 直接拦掉（401），
 * 前端据此引导登录页 —— 与购物车接口完全一致的口径。
 *
 * 店铺身份来自 {@link StoreResolver}（请求头 X-Store-Code 优先，域名兜底），
 * 再经 {@link ShopOrgScopeResolver} 翻译成店铺绑定部门的 org_code：
 * 收藏按店铺隔离，A 店收藏的商品不会出现在 B 店的"我的收藏"里。
 *
 * 三个接口的分工：
 * <ul>
 *   <li>{@code GET /status}：详情页进入时查"我收藏没有 + 收藏总数"；</li>
 *   <li>{@code POST /toggle}：详情页心形按钮，取反并返回**操作后**的最终状态；</li>
 *   <li>{@code GET /list}："我的收藏"分页列表（商品信息实时读取）。</li>
 * </ul>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Tag(name = "店铺端-商品收藏")
@RestController
@RequestMapping("/shop/favorite/store")
@Slf4j
public class ShopStoreFavoriteController {

    @Autowired
    private IShopFavoriteService shopFavoriteService;

    @Autowired
    private StoreResolver storeResolver;

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    @Operation(summary = "店铺端-查询收藏状态")
    @GetMapping(value = "/status")
    public Result<FavoriteStatusVO> status(@RequestParam(name = "productId") String productId,
                                           HttpServletRequest req) {
        return Result.OK(shopFavoriteService.status(buildContext(req), productId, resolveLanguage(req)));
    }

    @Operation(summary = "店铺端-收藏/取消收藏（取反）")
    @PostMapping(value = "/toggle")
    public Result<FavoriteStatusVO> toggle(@RequestBody FavoriteToggleDTO dto, HttpServletRequest req) {
        String productId = dto == null ? null : dto.getProductId();
        return Result.OK(shopFavoriteService.toggle(buildContext(req), productId, resolveLanguage(req)));
    }

    @Operation(summary = "店铺端-我的收藏（分页）")
    @GetMapping(value = "/list")
    public Result<IPage<StoreProductCardVO>> list(@RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                  @RequestParam(name = "pageSize", defaultValue = "12") Integer pageSize,
                                                  @RequestParam(name = "lang", required = false) String lang,
                                                  HttpServletRequest req) {
        String language = lang != null && !lang.isEmpty() ? lang : resolveLanguage(req);
        return Result.OK(shopFavoriteService.listFavorites(
                buildContext(req), pageNo, pageSize, language));
    }

    // ==================================================================
    // 上下文构建
    // ==================================================================

    /**
     * 把"当前登录人 + 当前店铺"解析成 Service 需要的上下文。
     *
     * 登录人理论上不会为空（Shiro 已拦掉匿名请求），这里再判一次是为了：
     * 万一某个未来版本的鉴权配置把该路径放开，也要显式报错，
     * 而不是拿着 null 的 userId 去写出一条"谁的都不是"的收藏记录。
     *
     * {@code storeOrgCode} 允许为 null（店铺没绑部门）：读接口降级为空列表，
     * 写接口由 Service 直接拒绝 —— 两种情况都不会退化成"不过滤店铺"。
     */
    private FavoriteContext buildContext(HttpServletRequest req) {
        LoginUser user = orgScopeResolver.currentLoginUser();
        if (user == null || user.getId() == null) {
            throw new JeecgBootException("请先登录后再收藏商品");
        }
        ShopStore store = storeResolver.resolve(req);
        return new FavoriteContext()
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .setStoreId(store.getId())
                .setStoreName(store.getName())
                .setStoreOrgCode(orgScopeResolver.resolveStoreOrgCodeOrNull(store));
    }

    /**
     * 语言解析：与店铺端商品接口保持一致的优先级。
     * 前端 BFF 会把当前语言同时放进 query 与请求头，这里两条路都接住。
     */
    private String resolveLanguage(HttpServletRequest request) {
        String language = request.getParameter("lang");
        if (language == null || language.isEmpty()) {
            language = request.getHeader("x-language");
        }
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
