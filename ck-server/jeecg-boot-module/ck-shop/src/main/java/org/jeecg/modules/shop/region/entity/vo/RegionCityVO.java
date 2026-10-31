package org.jeecg.modules.shop.region.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 城市下拉项（契约 §3.3）。
 * </p>
 *
 * <h3>字段为什么这么少</h3>
 * 城市下拉要一次铺满 200~500 条，字段越多传输越慢（菲律宾 4,374 个城市，
 * 搜索要多次往返）。{@code provinceCode} 之类调用方本来就知道的信息不再回传，
 * 靠 {@code id} 反查即可。
 *
 * <p>{@code population} 参与排序（人口多的城市在前，符合"用户多半选大城"的直觉），
 * 也一并下发给前端做二次排序/展示。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-28
 */
@Data
@Accessors(chain = true)
@Schema(description = "城市下拉项")
public class RegionCityVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键(cities.id, 落库到 city_id)")
    private Integer id;

    @Schema(description = "按访客语言解析后的名称")
    private String name;

    @Schema(description = "当地文字名称")
    private String nameLocal;

    @Schema(description = "人口(排序依据, 可能为空)")
    private Long population;
}
