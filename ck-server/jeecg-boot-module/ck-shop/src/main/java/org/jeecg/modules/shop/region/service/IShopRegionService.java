package org.jeecg.modules.shop.region.service;

import org.jeecg.modules.shop.region.entity.RegionResolution;
import org.jeecg.modules.shop.region.entity.vo.RegionCityVO;
import org.jeecg.modules.shop.region.entity.vo.RegionCountryVO;
import org.jeecg.modules.shop.region.entity.vo.RegionStateVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 国家/地区级联 Service。
 * </p>
 *
 * <h3>两类职责，分清楚才不会互相拖累</h3>
 * <ol>
 *   <li><b>下拉接口</b>（{@code listCountries/listStates/listCities}）：
 *       面向"界面怎么选"，入参出参都是访客语言；</li>
 *   <li><b>地址侧</b>（{@code resolveAddressRegion} 与三个批量展示名方法）：
 *       面向"库里存什么、界面显示什么"。前者算权威码 + 当地语言快照，
 *       后者只算展示名（<b>不落库</b>）。</li>
 * </ol>
 *
 * <p>地址与订单都依赖"写时校验"这一份实现，就是为了让"内联下单的地址"
 * 与"地址簿里的地址"长得一模一样——两份逻辑早晚会漂移。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-28
 */
public interface IShopRegionService {

    // ==================================================================
    // 一、下拉接口（契约 §3.1 ~ §3.3）
    // ==================================================================

    /** 国家全量（250 条），{@code name} 已按访客 locale 解析 */
    List<RegionCountryVO> listCountries(String locale);

    /**
     * 某国的一级行政区。
     *
     * <p><b>非法/空国家返回空数组而不是报错</b>：前端首次进页面时还不知道选哪个国家，
     * 这时候报错只会让页面白屏。返回空数组前端自然什么都不显示。
     */
    List<RegionStateVO> listStates(String countryCode, String locale);

    /**
     * 城市查询。
     *
     * @param countryCode 必填（ISO alpha-2），非法返回空数组
     * @param stateCode   可选；部分国家没有州级（新加坡、文莱…），前端会跳过这一级。
     *                    同时接受 {@code iso3166_2} 与 {@code "S"+id} 两种形式
     * @param keyword     可选；同时匹配 name / native / 任意语言译文
     *                    （译文用前缀匹配，原因见 Mapper）
     * @param limit       默认 200，硬上限 500（下拉场景，不做分页）
     */
    List<RegionCityVO> listCities(String countryCode, String stateCode, String keyword,
                                  Integer limit, String locale);

    // ==================================================================
    // 二、地址侧：写时校验（权威码 + 当地语言快照名）
    // ==================================================================

    /**
     * 校验国家/州/市三级关系，并给出"可以直接落库"的名称。
     *
     * <p>顺序即校验顺序（契约 §3.4）：国家存在 → 州属于该国家 → 城市属于该国家（及该州）。
     * 任一层级不合法直接抛 {@code JeecgBootException}，
     * <b>绝不静默降级成手填</b>：静默降级会让脏数据一路流到物流面单上。
     *
     * @return {@code countryCode} 为空时返回 {@code null}，表示"老客户端/未改造入口"，
     *         调用方应完全按旧逻辑走（不校验、不覆盖）
     */
    RegionResolution resolveAddressRegion(String countryCode, String provinceCode, Integer cityId);

    // ==================================================================
    // 三、地址侧：读时展示名（当前访客语言，不落库）
    // ==================================================================

    /** 国名展示：key = ISO alpha-2 */
    Map<String, String> countryDisplayNames(Collection<String> countryCodes, String locale);

    /**
     * 省/州展示名：key = 原样的 {@code province_code}
     * （ISO 3166-2 自带国家前缀，或回退的 {@code "S"+id}，两者都可作 key）
     */
    Map<String, String> stateDisplayNames(Collection<String> provinceCodes, String locale);

    /** 城市展示名：key = {@code cities.id} */
    Map<Integer, String> cityDisplayNames(Collection<Integer> cityIds, String locale);
}
