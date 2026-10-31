package org.jeecg.modules.shop.region.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 级联码解析结果：一份"可以直接落库"的地址地区字段。
 * </p>
 *
 * <h3>为什么不直接回传 VO</h3>
 * 地址保存时需要的不是"下拉展示"，而是<b>两个东西</b>：
 * 权威码（{@code countryCode / provinceCode / cityId}，用于校验与运费分区）
 * 和当地语言快照名（{@code countryName / provinceName / cityName}，落库给快递员看）。
 * 把这两件事揉进下拉 VO 会让"下拉接口返回什么"和"落库写什么"互相牵制，
 * 所以单独用一个内部对象承载，VO 只服务展示。
 *
 * <p>名称一律是 {@code native ?: name}（上游没有 native 的才退拉丁名）——
 * 落库必须是当地文字，见契约 §0 第 4 条。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-28
 */
@Data
@Accessors(chain = true)
public class RegionResolution implements Serializable {
    private static final long serialVersionUID = 1L;

    /** ISO 3166-1 alpha-2（已规整为大写） */
    private String countryCode;

    /** 国名，当地语言（countries.native ?: name） */
    private String countryName;

    /** ISO 3166-2；省份未选时为 null */
    private String provinceCode;

    /** 省名，当地语言（states.native ?: name）；省份未选时为 null */
    private String provinceName;

    /** cities.id；城市为手填时为 null */
    private Integer cityId;

    /** 城市名，当地语言（cities.native ?: name）；城市为手填时为 null */
    private String cityName;
}
