package org.jeecg.modules.shop.cart.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.shop.common.ShopRequestUtils;
import org.jeecg.modules.shop.cart.entity.dto.CartStatsQuery;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsEventVO;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsOverviewVO;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsRankVO;
import org.jeecg.modules.shop.cart.entity.vo.CartStatsTrendVO;
import org.jeecg.modules.shop.cart.service.IShopCartStatsService;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 后台加购统计（运营看板）
 * </p>
 *
 * 统计口径与"当前购物车快照"的差别见 {@link CartStatsOverviewVO}。
 *
 * <b>权限</b>：所有接口都要 {@code shop-cart:stats}。
 * 这不是"多此一举"——加购数据能反推出各店铺的转化情况，
 * 让没有该菜单权限的账号读到，等于绕过了菜单级授权。
 *
 * <b>可见域</b>：由 {@code IShopCartStatsService} 内部按登录人的部门解析，
 * 指定了无权查看的店铺会直接报错（而不是静默返回空，那样会让人误以为"这家店没数据"）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Tag(name = "后台-加购统计")
@RestController
@RequestMapping("/shop/cart/admin")
@Slf4j
public class ShopCartStatsController {

    @Autowired
    private IShopCartStatsService cartStatsService;

    @Operation(summary = "加购统计-总览")
    @RequiresPermissions("shop-cart:stats")
    @GetMapping(value = "/overview")
    public Result<CartStatsOverviewVO> overview(CartStatsQuery query, HttpServletRequest req) {
        return Result.OK(cartStatsService.overview(query, resolveLanguage(req)));
    }

    @Operation(summary = "加购统计-商品/SKU热度排行")
    @RequiresPermissions("shop-cart:stats")
    @GetMapping(value = "/rank")
    public Result<List<CartStatsRankVO>> rank(CartStatsQuery query, HttpServletRequest req) {
        return Result.OK(cartStatsService.rank(query, resolveLanguage(req)));
    }

    @Operation(summary = "加购统计-按日趋势")
    @RequiresPermissions("shop-cart:stats")
    @GetMapping(value = "/trend")
    public Result<List<CartStatsTrendVO>> trend(CartStatsQuery query, HttpServletRequest req) {
        return Result.OK(cartStatsService.trend(query, resolveLanguage(req)));
    }

    @Operation(summary = "加购统计-加购明细")
    @RequiresPermissions("shop-cart:stats")
    @GetMapping(value = "/eventPage")
    public Result<IPage<CartStatsEventVO>> eventPage(CartStatsQuery query, HttpServletRequest req) {
        return Result.OK(cartStatsService.eventPage(query, resolveLanguage(req)));
    }

    /**
     * 店铺下拉选项。
     * 不额外加权限：能进这个页面就已经有 shop-cart:stats，
     * 而且返回的只是自己可见域内的店铺名，不含任何经营数据。
     */
    @Operation(summary = "加购统计-可选店铺列表")
    @GetMapping(value = "/storeOptions")
    public Result<List<ShopStore>> storeOptions() {
        return Result.OK(cartStatsService.storeOptions());
    }

    /** 语言解析统一收敛到 ShopRequestUtils（query lang → x-language → Accept-Language → zh） */
    private String resolveLanguage(HttpServletRequest request) {
        return ShopRequestUtils.resolveLanguage(request);
    }
}