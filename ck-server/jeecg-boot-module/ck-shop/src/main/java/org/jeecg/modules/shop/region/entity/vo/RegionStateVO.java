package org.jeecg.modules.shop.region.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 一级行政区（州/省/府）下拉项（契约 §3.2）。
 * </p>
 *
 * <h3>code 为什么必须有值</h3>
 * 前端要把"选中的州"提交回来作为 {@code provinceCode}，落库列是 {@code varchar(10)}。
 * 上游有 28 个州级记录没有 {@code iso3166_2}（多为小岛屿属地），
 * 若原样返回 null，这些国家的用户就没法保存地址。
 * 因此服务端统一回退成 {@code "S" + id}，并且<b>校验入口也认这种形式</b>
 * （{@code ShopRegionMapper.selectStateByCode} 同时匹配两串），
 * 保证"前端拿到什么就能提交回什么"。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-28
 */
@Data
@Accessors(chain = true)
@Schema(description = "一级行政区下拉项")
public class RegionStateVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键(states.id)")
    private Integer id;

    /** ISO 3166-2；上游缺失时回退 "S"+id，前端可直接原样回传 */
    @Schema(description = "行政区编码(ISO 3166-2, 缺失时为 S+id)")
    private String code;

    @Schema(description = "按访客语言解析后的名称")
    private String name;

    @Schema(description = "当地文字名称")
    private String nameLocal;

    /** province / state / region / district…：不同国家的行政层级叫法不同，前端可用来做提示文案 */
    @Schema(description = "行政区类型")
    private String type;
}
