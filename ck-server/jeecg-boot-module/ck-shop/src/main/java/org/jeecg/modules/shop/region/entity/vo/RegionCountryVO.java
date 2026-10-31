package org.jeecg.modules.shop.region.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 国家/地区下拉项（契约 §3.1）。
 * </p>
 *
 * <h3>name 与 nameLocal 是两个不同用途的字段，不要合并</h3>
 * <ul>
 *   <li>{@code name}：按<b>当前访客语言</b>解析后的名字，只用于界面显示（不落库）；</li>
 *   <li>{@code nameLocal}：{@code countries.native}，即当地文字。
 *       中文用户搜不到"กรุงเทพฯ"，但东南亚用户搜得到——所以前端搜索必须
 *       同时匹配这两个字段，否则一半用户搜不到自己的国家。</li>
 * </ul>
 *
 * <p>{@code postalCodeFormat / postalCodeRegex} 直接下发，是为了让前端做<b>预校验</b>：
 * 每个国家的邮编规则都不一样（泰国 5 位纯数字、哈萨克斯坦带字母），
 * 硬编码在代码里就等于永远跟不上数据更新。后端仍是权威校验方。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-28
 */
@Data
@Accessors(chain = true)
@Schema(description = "国家/地区下拉项")
public class RegionCountryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** ISO 3166-1 alpha-2（权威码，落库用的就是它） */
    @Schema(description = "国家码(ISO alpha-2)")
    private String code;

    /** 当前访客语言的名字；译文缺失时按 译文 → name → native 回退，绝不留空 */
    @Schema(description = "按访客语言解析后的国名")
    private String name;

    /** 当地文字国名（countries.native），用于搜索与"落库快照"对照 */
    @Schema(description = "当地文字国名")
    private String nameLocal;

    @Schema(description = "国际电话区号")
    private String phoneCode;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "邮编格式示例")
    private String postalCodeFormat;

    @Schema(description = "邮编校验正则(前端预校验用, 后端仍是权威)")
    private String postalCodeRegex;
}
