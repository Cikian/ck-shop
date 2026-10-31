package org.jeecg.modules.shop.region.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.vo.Result;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.shop.region.RegionLangResolver;
import org.jeecg.modules.shop.region.entity.vo.RegionCityVO;
import org.jeecg.modules.shop.region.entity.vo.RegionCountryVO;
import org.jeecg.modules.shop.region.entity.vo.RegionStateVO;
import org.jeecg.modules.shop.region.service.IShopRegionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 国家/地区级联（契约 §3，公开接口）。
 * </p>
 *
 * <h3>为什么三个接口都要 {@code @IgnoreAuth} 且必须写明确的 path</h3>
 * 地址填写页在<b>未登录</b>状态下也要能用（先填地址再登录是常见路径），
 * 所以这三个接口不带任何登录要求。
 * 而 JeecgBoot 的 {@code @IgnoreAuth} 由 {@code IgnoreAuthPostProcessor}
 * 扫描<b>方法级 mapping 的 value()</b> 生成免登录 URL 表——{@code @GetMapping}
 * 不写路径时 value() 是空数组，扫描结果为空，注解形同没加。
 * 因此每个方法都显式写 {@code value = "/xxx"}（与 {@code /home/slide/store} 同一套做法）。
 *
 * <p><b>接口不返回店铺相关内容</b>：国家/地区是世界公共数据，
 * 与店铺、用户、权限都无关，不该因为"反正都在 shop 包下"而拉进任何上下文解析。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-28
 */
@Tag(name = "国家/地区级联")
@RestController
@RequestMapping("/shop/region")
public class ShopRegionController {

    @Autowired
    private IShopRegionService regionService;

    /**
     * 国家全量（250 条）。
     *
     * <p>前端一次加载全量做可搜索下拉；排序按 {@code countries.name}（拉丁名），
     * 译名排序交给前端（SQL 里折腾排序规则只会让全表扫得更慢）。
     */
    @IgnoreAuth
    @GetMapping(value = "/countries")
    @Operation(summary = "国家/地区列表（公开）")
    public Result<List<RegionCountryVO>> countries(
            @RequestParam(name = "lang", required = false) String lang,
            HttpServletRequest request) {
        return Result.OK(regionService.listCountries(RegionLangResolver.resolveLocale(request, lang)));
    }

    /**
     * 某国的一级行政区。
     *
     * <p>{@code country} 必填但**非法/为空时返回空数组**：
     * 前端首次进页面时还没选国家，报错只会白屏（契约 §3.2）。
     */
    @IgnoreAuth
    @GetMapping(value = "/states")
    @Operation(summary = "一级行政区列表（公开）")
    public Result<List<RegionStateVO>> states(
            @RequestParam(name = "country", required = false) String country,
            @RequestParam(name = "lang", required = false) String lang,
            HttpServletRequest request) {
        return Result.OK(regionService.listStates(country, RegionLangResolver.resolveLocale(request, lang)));
    }

    /**
     * 城市列表。
     *
     * <p>{@code stateCode} 与 {@code keyword} 都可选：有 21 个国家没有州级，
     * 前端会跳过那一级直接按国家查城市；城市多的国家（菲律宾 4,374）靠 {@code keyword} 搜索。
     * （{@code type} 过滤、两步关键词搜索、{@code limit} 上限都在 Service 里收口，
     * 不放在 Controller，避免有人新开接口时漏掉。）
     */
    @IgnoreAuth
    @GetMapping(value = "/cities")
    @Operation(summary = "城市列表（公开）")
    public Result<List<RegionCityVO>> cities(
            @RequestParam(name = "country", required = false) String country,
            @RequestParam(name = "stateCode", required = false) String stateCode,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "lang", required = false) String lang,
            HttpServletRequest request) {
        return Result.OK(regionService.listCities(country, stateCode, keyword, limit,
                RegionLangResolver.resolveLocale(request, lang)));
    }
}
