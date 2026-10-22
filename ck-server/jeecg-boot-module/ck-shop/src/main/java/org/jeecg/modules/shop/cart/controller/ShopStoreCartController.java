package org.jeecg.modules.shop.cart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.shop.common.ShopRequestUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.shop.cart.entity.dto.CartAddDTO;
import org.jeecg.modules.shop.cart.entity.dto.CartBatchDTO;
import org.jeecg.modules.shop.cart.entity.dto.CartQuantityDTO;
import org.jeecg.modules.shop.cart.entity.dto.CartRemoveDTO;
import org.jeecg.modules.shop.cart.entity.vo.StoreCartCountVO;
import org.jeecg.modules.shop.cart.entity.vo.StoreCartVO;
import org.jeecg.modules.shop.cart.service.CartContext;
import org.jeecg.modules.shop.cart.service.IShopCartService;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.jeecg.modules.shop.store.StoreResolver;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 店铺端（前台）购物车
 * </p>
 *
 * <b>这个 Controller 刻意不加 {@code @IgnoreAuth}</b>：购物车必须知道"是谁的车"，
 * 匿名访问没有任何意义。未登录时由 Shiro 直接拦掉（401），前端据此跳登录页。
 *
 * 店铺身份仍然来自 {@link StoreResolver}（请求头 X-Store-Code 优先，域名兜底），
 * 与商品接口完全一致——购物车按店铺隔离，A 店的购物车不能被 B 店看到。
 *
 * 所有写操作都返回**操作后的完整购物车**，前端一次请求拿到最新状态。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Tag(name = "店铺端-购物车")
@RestController
@RequestMapping("/shop/cart/store")
@Slf4j
public class ShopStoreCartController {

    @Autowired
    private IShopCartService shopCartService;

    @Autowired
    private StoreResolver storeResolver;

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    @Operation(summary = "店铺端-购物车列表")
    @GetMapping(value = "/list")
    public Result<StoreCartVO> list(HttpServletRequest req) {
        return Result.OK(shopCartService.listCart(buildContext(req), resolveLanguage(req)));
    }

    @Operation(summary = "店铺端-购物车角标（条目数与件数）")
    @GetMapping(value = "/count")
    public Result<StoreCartCountVO> count(HttpServletRequest req) {
        return Result.OK(shopCartService.countCart(buildContext(req)));
    }

    @Operation(summary = "店铺端-加入购物车")
    @PostMapping(value = "/add")
    public Result<StoreCartVO> add(@RequestBody CartAddDTO dto, HttpServletRequest req) {
        return Result.OK(shopCartService.addItem(buildContext(req), dto, resolveLanguage(req)));
    }

    @Operation(summary = "店铺端-修改购物车数量")
    @PostMapping(value = "/quantity")
    public Result<StoreCartVO> quantity(@RequestBody CartQuantityDTO dto, HttpServletRequest req) {
        return Result.OK(shopCartService.updateQuantity(buildContext(req), dto, resolveLanguage(req)));
    }

    @Operation(summary = "店铺端-删除单条")
    @PostMapping(value = "/remove")
    public Result<StoreCartVO> remove(@RequestBody CartRemoveDTO dto, HttpServletRequest req) {
        String itemId = dto == null ? null : dto.getItemId();
        return Result.OK(shopCartService.removeItem(buildContext(req), itemId, resolveLanguage(req)));
    }

    @Operation(summary = "店铺端-批量删除")
    @PostMapping(value = "/batch-remove")
    public Result<StoreCartVO> batchRemove(@RequestBody CartBatchDTO dto, HttpServletRequest req) {
        return Result.OK(shopCartService.batchRemove(buildContext(req), dto, resolveLanguage(req)));
    }

    @Operation(summary = "店铺端-清空购物车")
    @PostMapping(value = "/clear")
    public Result<StoreCartVO> clear(HttpServletRequest req) {
        return Result.OK(shopCartService.clearCart(buildContext(req), resolveLanguage(req)));
    }

    @Operation(summary = "店铺端-一键清理失效商品")
    @PostMapping(value = "/clear-invalid")
    public Result<StoreCartVO> clearInvalid(HttpServletRequest req) {
        return Result.OK(shopCartService.clearInvalidItems(buildContext(req), resolveLanguage(req)));
    }

    // ==================================================================
    // 上下文构建
    // ==================================================================

    /**
     * 把"当前登录人 + 当前店铺"解析成 Service 需要的上下文。
     *
     * 登录人理论上不会为空（Shiro 已拦掉匿名请求），这里再判一次是为了：
     * 万一某个未来版本的鉴权配置把该路径放开，也要显式报错，
     * 而不是拿着 null 的 userId 去查出一堆"谁的都不是"的数据。
     */
    private CartContext buildContext(HttpServletRequest req) {
        LoginUser user = orgScopeResolver.currentLoginUser();
        if (user == null || user.getId() == null) {
            throw new JeecgBootException("请先登录后再操作购物车");
        }
        ShopStore store = storeResolver.resolve(req);
        return new CartContext()
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .setStoreId(store.getId())
                .setStoreName(store.getName())
                // 店铺没绑部门时返回 null：读接口降级为"条目全部失效"，写接口直接拒绝，
                // 两种情况都不会退化成"不过滤店铺"
                .setStoreOrgCode(orgScopeResolver.resolveStoreOrgCodeOrNull(store));
    }

    /** 语言解析统一收敛到 ShopRequestUtils（query lang → x-language → Accept-Language → zh） */
    private String resolveLanguage(HttpServletRequest request) {
        return ShopRequestUtils.resolveLanguage(request);
    }
}