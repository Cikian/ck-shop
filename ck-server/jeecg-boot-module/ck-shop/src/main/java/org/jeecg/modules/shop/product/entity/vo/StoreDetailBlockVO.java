package org.jeecg.modules.shop.product.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * <p>
 * 店铺端商品详情模块 VO
 * </p>
 *
 * <p>契约 2.2：**不返回 id / languageCode** —— 店铺端无权也不需要知道内部主键，
 * 语言回退已在后端完成，前端直接按数组顺序渲染。</p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-20
 */
@Data
public class StoreDetailBlockVO {

    @Schema(description = "模块类型")
    private String blockType;

    @Schema(description = "模块内容（JSON 对象，结构见契约 1.3）")
    private Map<String, Object> content;

    @Schema(description = "展示顺序（已按 sortOrder 升序）")
    private Integer sortOrder;

    /*
     * 模块四方向间距（契约 1.4 / 2.2）：店铺端按内联 margin 渲染；null 归一为 0，越界按范围夹取。
     */
    @Schema(description = "上间距(px)")
    private Integer marginTop;

    @Schema(description = "右间距(px)")
    private Integer marginRight;

    @Schema(description = "下间距(px)")
    private Integer marginBottom;

    @Schema(description = "左间距(px)")
    private Integer marginLeft;
}
