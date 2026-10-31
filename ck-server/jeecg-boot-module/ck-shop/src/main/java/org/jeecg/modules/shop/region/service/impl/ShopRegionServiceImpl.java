package org.jeecg.modules.shop.region.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.shop.region.RegionLangResolver;
import org.jeecg.modules.shop.region.entity.RegionResolution;
import org.jeecg.modules.shop.region.entity.vo.RegionCityVO;
import org.jeecg.modules.shop.region.entity.vo.RegionCountryVO;
import org.jeecg.modules.shop.region.entity.vo.RegionStateVO;
import org.jeecg.modules.shop.region.mapper.ShopRegionMapper;
import org.jeecg.modules.shop.region.service.IShopRegionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * 国家/地区级联实现。
 * </p>
 *
 * <h3>几条写在这里就不容易走样的规矩</h3>
 * <ol>
 *   <li><b>空/非法入参一律返回空集合</b>（只有"写地址"那条路径才报错）：
 *       下拉接口是页面渲染的依赖，报错等于白屏；</li>
 *   <li><b>城市关键词搜索是两条 SQL 合并</b>，不是一条 OR EXISTS
 *       （实测 495ms → 5ms/8ms，原因见 Mapper 注释）；</li>
 *   <li><b>译文只查一次</b>：城市结果集最多 500 条，按 id 批量反查译文，
 *       绝不在循环里查 {@code translations}（300 万行）；</li>
 *   <li><b>展示名与落库名分开算</b>：展示名永远现算，不写回任何业务表。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-28
 */
@Slf4j
@Service
public class ShopRegionServiceImpl implements IShopRegionService {

    /** 城市下拉默认条数：够覆盖大部分国家的全量城市，又不至于一次把 4,374 条全推下去 */
    private static final int CITY_LIMIT_DEFAULT = 200;

    /** 城市下拉硬上限：前端下拉框铺不下更多，也防止有人用 limit=999999 把接口当导出用 */
    private static final int CITY_LIMIT_MAX = 500;

    /** 省份/城市名称落库列宽（varchar(100)）：超出就截断，不能让严格模式把保存打成 500 */
    private static final int REGION_NAME_MAX_LENGTH = 100;

    /** "S"+id 形式的州编码：iso3166_2 缺失时服务端回退出来的标识 */
    private static final Pattern FALLBACK_STATE_CODE = Pattern.compile("^[Ss](\\d{1,9})$");

    @Autowired
    private ShopRegionMapper regionMapper;

    // ==================================================================
    // 一、下拉接口
    // ==================================================================

    @Override
    public List<RegionCountryVO> listCountries(String locale) {
        return regionMapper.selectCountries(RegionLangResolver.toTranslationLanguage(locale));
    }

    @Override
    public List<RegionStateVO> listStates(String countryCode, String locale) {
        String country = normalizeCountry(countryCode);
        if (country == null) {
            // 前端首次进页面还不知道选哪个国家：空数组而不是报错（契约 §3.2）
            return new ArrayList<>();
        }
        return regionMapper.selectStates(country, RegionLangResolver.toTranslationLanguage(locale));
    }

    @Override
    public List<RegionCityVO> listCities(String countryCode, String stateCode, String keyword,
                                         Integer limit, String locale) {
        String country = normalizeCountry(countryCode);
        if (country == null) {
            return new ArrayList<>();
        }
        String language = RegionLangResolver.toTranslationLanguage(locale);
        int size = normalizeLimit(limit);

        // 州可选：该国没有州级（新加坡、文莱…）时前端会跳过这一级，直接按国家查
        Integer stateId = null;
        String state = StringUtils.trimToNull(stateCode);
        if (state != null) {
            stateId = regionMapper.selectStateIdByCode(country, state);
            if (stateId == null) {
                // 州不属于该国或不存在：返回空数组，不报错（下拉场景宁可空也不要 500）
                return new ArrayList<>();
            }
        }

        String kw = StringUtils.trimToNull(keyword);
        List<RegionCityVO> cities;
        if (kw == null) {
            cities = regionMapper.selectCities(country, stateId, null, size);
        } else {
            // 两步分开查再合并：① name/native 模糊 ② 译文前缀。
            // language 为 null（访客是 en）时第②步直接跳过：en 译文只覆盖 0.6%，
            // 查了会让"搜 bang 有的城市命中、有的不命中"，比不查更让人困惑。
            List<RegionCityVO> byName = regionMapper.selectCities(country, stateId, kw, size);
            List<RegionCityVO> byTranslation = language == null
                    ? new ArrayList<>()
                    : regionMapper.selectCitiesByTranslation(country, stateId, language, kw, size);
            cities = mergeCities(byName, byTranslation, size);
        }
        applyCityTranslations(cities, language);
        return cities;
    }

    // ==================================================================
    // 二、地址侧：写时校验
    // ==================================================================

    @Override
    public RegionResolution resolveAddressRegion(String countryCode, String provinceCode, Integer cityId) {
        String country = normalizeCountry(countryCode);
        if (country == null) {
            // 老客户端 / 未改造入口：不校验、不覆盖（契约 §3.4 第 5 条）
            return null;
        }
        RegionCountryVO countryVO = regionMapper.selectCountryByCode(country);
        if (countryVO == null) {
            throw new JeecgBootException("国家/地区不合法");
        }
        RegionResolution resolution = new RegionResolution()
                .setCountryCode(country)
                .setCountryName(truncateName(firstNonBlank(countryVO.getNameLocal(), countryVO.getName())));

        Integer stateId = null;
        String province = StringUtils.trimToNull(provinceCode);
        if (province != null) {
            RegionStateVO stateVO = regionMapper.selectStateByCode(country, province, null);
            if (stateVO == null) {
                // 注意：可能是"州不存在"也可能是"州属于别的国家"，两种情况用同一句文案，
                // 不给攻击者探测某个编码是否存在的机会
                throw new JeecgBootException("省/州与该国家或地区不匹配");
            }
            stateId = stateVO.getId();
            resolution.setProvinceCode(province)
                    .setProvinceName(truncateName(firstNonBlank(stateVO.getNameLocal(), stateVO.getName())));
        }

        if (cityId != null) {
            RegionCityVO cityVO = regionMapper.selectCityForRegion(cityId, country, stateId);
            if (cityVO == null) {
                throw new JeecgBootException("城市与该国家或地区不匹配");
            }
            resolution.setCityId(cityId)
                    .setCityName(truncateName(firstNonBlank(cityVO.getNameLocal(), cityVO.getName())));
        }
        return resolution;
    }

    // ==================================================================
    // 三、地址侧：读时展示名
    // ==================================================================

    @Override
    public Map<String, String> countryDisplayNames(Collection<String> countryCodes, String locale) {
        Map<String, String> result = new HashMap<>();
        Set<String> codes = new LinkedHashSet<>();
        if (countryCodes != null) {
            for (String code : countryCodes) {
                String normalized = normalizeCountry(code);
                if (normalized != null) {
                    codes.add(normalized);
                }
            }
        }
        if (codes.isEmpty()) {
            return result;
        }
        for (RegionCountryVO vo : regionMapper.selectCountriesByCodes(codes,
                RegionLangResolver.toTranslationLanguage(locale))) {
            result.put(vo.getCode(), vo.getName());
        }
        return result;
    }

    @Override
    public Map<String, String> stateDisplayNames(Collection<String> provinceCodes, String locale) {
        Map<String, String> result = new HashMap<>();
        Set<String> codes = new LinkedHashSet<>();
        Set<Integer> ids = new LinkedHashSet<>();
        if (provinceCodes != null) {
            for (String raw : provinceCodes) {
                String code = StringUtils.trimToNull(raw);
                if (code == null) {
                    continue;
                }
                Matcher fallback = FALLBACK_STATE_CODE.matcher(code);
                if (fallback.matches()) {
                    // "S3901" → 3901：回退编码里带的 id 直接可查，不必扫表达式
                    ids.add(Integer.valueOf(fallback.group(1)));
                } else {
                    codes.add(code);
                }
            }
        }
        if (codes.isEmpty() && ids.isEmpty()) {
            return result;
        }
        for (RegionStateVO vo : regionMapper.selectStatesByCodes(codes, ids,
                RegionLangResolver.toTranslationLanguage(locale))) {
            result.put(vo.getCode(), vo.getName());
        }
        return result;
    }

    @Override
    public Map<Integer, String> cityDisplayNames(Collection<Integer> cityIds, String locale) {
        Map<Integer, String> result = new HashMap<>();
        Set<Integer> ids = new LinkedHashSet<>();
        if (cityIds != null) {
            for (Integer id : cityIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            return result;
        }
        for (RegionCityVO vo : regionMapper.selectCitiesByIds(ids,
                RegionLangResolver.toTranslationLanguage(locale))) {
            result.put(vo.getId(), vo.getName());
        }
        return result;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /**
     * 合并两步关键词结果并按契约口径重排。
     *
     * <p>按 id 去重是必须的：一个城市完全可能既命中 name 又命中译文（"曼谷"既是译名，
     * 也有城市的 name 就叫 Bangkok），不去重会在下拉里出现两行一样的选项。
     */
    private List<RegionCityVO> mergeCities(List<RegionCityVO> primary, List<RegionCityVO> secondary, int limit) {
        Map<Integer, RegionCityVO> merged = new LinkedHashMap<>();
        append(merged, primary);
        append(merged, secondary);
        List<RegionCityVO> all = new ArrayList<>(merged.values());
        // 人口未知的排最后：不能因为 population 为 NULL 就把它排到人口百万的城市前面
        all.sort(Comparator
                .comparing(RegionCityVO::getPopulation,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RegionCityVO::getName,
                        Comparator.nullsLast(String::compareTo)));
        return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
    }

    private void append(Map<Integer, RegionCityVO> target, List<RegionCityVO> source) {
        if (source == null) {
            return;
        }
        for (RegionCityVO city : source) {
            if (city != null && city.getId() != null) {
                target.putIfAbsent(city.getId(), city);
            }
        }
    }

    /**
     * 给结果集补当前语言的城市名（回退链 译文 → name → native 里的第一环）。
     *
     * <p>一次 IN 查询取完全部译文，而不是逐条查：这是本文件最容易退化成 N+1 的地方。
     */
    private void applyCityTranslations(List<RegionCityVO> cities, String language) {
        if (language == null || cities == null || cities.isEmpty()) {
            return;
        }
        List<Integer> ids = new ArrayList<>(cities.size());
        for (RegionCityVO city : cities) {
            if (city != null && city.getId() != null) {
                ids.add(city.getId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Integer, String> translations = new HashMap<>();
        for (RegionCityVO translation : regionMapper.selectCityTranslations(ids, language)) {
            if (StringUtils.isNotBlank(translation.getName())) {
                translations.put(translation.getId(), translation.getName());
            }
        }
        if (translations.isEmpty()) {
            return;
        }
        for (RegionCityVO city : cities) {
            String translated = translations.get(city.getId());
            if (StringUtils.isNotBlank(translated)) {
                city.setName(translated);
            }
        }
    }

    /** 国家码规整：trim + 大写；不是两位字母就当作"没给/非法" */
    private String normalizeCountry(String countryCode) {
        String code = StringUtils.trimToNull(countryCode);
        if (code == null || code.length() != 2) {
            return null;
        }
        return code.toUpperCase(Locale.ROOT);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return CITY_LIMIT_DEFAULT;
        }
        return Math.min(limit, CITY_LIMIT_MAX);
    }

    /** {@code native} 优先（落库必须是当地文字），缺失才退拉丁名 */
    private String firstNonBlank(String first, String second) {
        String value = StringUtils.trimToNull(first);
        return value != null ? value : StringUtils.trimToNull(second);
    }

    /**
     * 名称按落库列宽截断。
     *
     * <p>泰文/阿拉伯文/西里尔文的正式地名比中文长得多，{@code varchar(100)} 在严格模式下
     * 会直接报错；这里截断而不是让保存失败（完整写法仍在 region 表里可查）。
     */
    private String truncateName(String value) {
        String trimmed = StringUtils.trimToNull(value);
        return trimmed == null ? null : StringUtils.left(trimmed, REGION_NAME_MAX_LENGTH);
    }
}
