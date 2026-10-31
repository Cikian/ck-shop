package org.jeecg.modules.shop.region.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.shop.region.entity.vo.RegionCityVO;
import org.jeecg.modules.shop.region.entity.vo.RegionCountryVO;
import org.jeecg.modules.shop.region.entity.vo.RegionStateVO;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 国家/地区级联数据访问（只读参考表）。
 * </p>
 *
 * <h3>为什么这些查询全部手写 XML</h3>
 * 三张表是上游整表重灌的"参考数据"，没有实体类（也不该有：不需要 MyBatis-Plus 的
 * 逻辑删除/乐观锁那套业务机制），查询又需要 {@code translations} 左连与
 * {@code type} 过滤这类业务语义很强的条件，用 Wrapper 拼反而看不清 SQL 长什么样。
 *
 * <h3>四条不可省略的约束（漏任何一条都会出线上问题）</h3>
 * <ol>
 *   <li><b>{@code cities} 必须带 {@code type IN ('city','municipality','town','adm2')}</b>：
 *       该表混了 {@code adm1}（省级）、{@code village}、{@code section} 等层级，
 *       不过滤会让"城市"下拉里出现省名，用户选完得到一条没有具体地点的地址；</li>
 *   <li><b>译文一律 左连 + COALESCE</b>，不允许"先查主表再逐行查译文"（N+1）——
 *       {@code translations} 有 300 万行，N+1 是灾难；</li>
 *   <li><b>城市关键词搜索拆成两条</b>（名称/当地名 与 译文前缀），
 *       理由见 {@link #selectCitiesByTranslation} 的注释；</li>
 *   <li><b>不写 UPDATE/INSERT/DELETE</b>：这几张表是 ODbL 下游数据，
 *       只能整表重灌（见 {@code docs/国家地区级联数据-导入与维护-2026-10-28.md}）。</li>
 * </ol>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-28
 */
@Mapper
public interface ShopRegionMapper {

    /**
     * 国家全量（250 行）。
     *
     * @param language 译文语言；{@code null} 表示不查译文（访客语言是 en 时走这条路）
     */
    List<RegionCountryVO> selectCountries(@Param("language") String language);

    /** 一次取多个国家的展示名（地址列表批量渲染用，避免每条地址查一次） */
    List<RegionCountryVO> selectCountriesByCodes(@Param("codes") Collection<String> codes,
                                                 @Param("language") String language);

    /** 按 ISO alpha-2 取单个国家的落库快照名（native 优先）；不存在返回 null */
    RegionCountryVO selectCountryByCode(@Param("country") String country);

    /**
     * 某国的一级行政区。
     *
     * @param country 已规整为大写的 ISO alpha-2；调用方负责"国家为空/非法直接返回空数组"
     */
    List<RegionStateVO> selectStates(@Param("country") String country,
                                     @Param("language") String language);

    /**
     * 按编码取单个州。
     *
     * <p>同时匹配 {@code iso3166_2} 与 {@code "S"+id} 两种形式：前端把
     * §3.2 回退出来的 {@code "S3901"} 原样提交回来时也要能命中，
     * 否则那 28 个没有 ISO 编码的州选完等于没选。
     */
    RegionStateVO selectStateByCode(@Param("country") String country,
                                    @Param("code") String code,
                                    @Param("language") String language);

    /**
     * 把州编码解析成 {@code states.id}（城市查询用它过滤，比 join 更好走索引）。
     *
     * @return 不属于该国家或不存在时返回 null
     */
    Integer selectStateIdByCode(@Param("country") String country, @Param("code") String code);

    /** 批量取州的展示名（地址列表批量渲染用） */
    List<RegionStateVO> selectStatesByCodes(@Param("codes") Collection<String> codes,
                                            @Param("ids") Collection<Integer> ids,
                                            @Param("language") String language);

    /**
     * 城市查询：按国家（可选按州）+ 名称/当地名模糊匹配。
     *
     * <p>{@code keyword} 为 null 时是"按人口铺开"的常规下拉；不为 null 时
     * 只匹配 {@code name/native}，<b>不做译文匹配</b>（译文匹配由另一条语句负责）。
     * 这里允许 {@code LIKE '%kw%'} 是因为候选集已经先被 {@code country_code}
     * 缩到几千行以内（吃 {@code idx_cities_country_type_pop}）。
     */
    List<RegionCityVO> selectCities(@Param("country") String country,
                                    @Param("stateId") Integer stateId,
                                    @Param("keyword") String keyword,
                                    @Param("limit") int limit);

    /**
     * 城市查询的第二步：译文<b>前缀</b>匹配。
     *
     * <p>为什么必须拆开、且译文只能用前缀：实测把"名称/当地名/任意语言译文"
     * 写成一个 {@code OR EXISTS} 时耗时 495ms（对每个 city 做一次相关子查询，
     * 且吃不到 {@code idx_tr_prefix}）；拆成两条、译文用前缀匹配后分别是 5ms / 8ms。
     * 拼音/英文输入本来也是从左往右打的，前缀匹配的体验损失可以忽略。
     */
    List<RegionCityVO> selectCitiesByTranslation(@Param("country") String country,
                                                 @Param("stateId") Integer stateId,
                                                 @Param("language") String language,
                                                 @Param("keywordPrefix") String keywordPrefix,
                                                 @Param("limit") int limit);

    /**
     * 批量取城市译文（只填 {@code id} 与 {@code name=译文}）。
     *
     * <p>走 {@code PRIMARY KEY(place_type, place_id, language)}：{@code place_type}
     * 是常量 + {@code place_id IN (...)}，是主键前缀点查，不会扫表。
     */
    List<RegionCityVO> selectCityTranslations(@Param("ids") Collection<Integer> ids,
                                              @Param("language") String language);

    /** 批量取城市的展示名（地址列表批量渲染用） */
    List<RegionCityVO> selectCitiesByIds(@Param("ids") Collection<Integer> ids,
                                         @Param("language") String language);

    /**
     * 取单个城市用于<b>写时校验</b>：必须属于该国家，且（若给了州）必须属于该州。
     *
     * <p>归属不符与"城市不存在"返回同一个 null：错误文案一致，
     * 不给探测"某个 cityId 是否存在"的接口。
     */
    RegionCityVO selectCityForRegion(@Param("id") Integer id,
                                     @Param("country") String country,
                                     @Param("stateId") Integer stateId);
}
