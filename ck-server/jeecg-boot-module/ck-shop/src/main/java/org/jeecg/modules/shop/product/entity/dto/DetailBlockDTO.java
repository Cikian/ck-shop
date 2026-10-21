package org.jeecg.modules.shop.product.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * <p>
 * 商品详情模块 DTO（管理端提交/回填）
 * </p>
 *
 * <p>契约 2.1：{@code content} 在接口上**始终是 JSON 对象**（Map），
 * 数据库里才是字符串；整体替换时不依赖 {@code id}（前端回填时可以带上，后端忽略）。</p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-20
 */
@Data
public class DetailBlockDTO {

    @Schema(description = "主键（可选，回填时带上；后端整体替换时不依赖它）")
    private String id;

    @Schema(description = "语言代码: zh/en/fr...")
    private String languageCode;

    @Schema(description = "模块类型: IMAGE/IMAGE_GRID/IMAGE_TEXT/TEXT/RICH_TEXT/VIDEO/TITLE/DIVIDER")
    private String blockType;

    @Schema(description = "模块内容（JSON 对象，结构见契约 1.3）")
    private Map<String, Object> content;

    @Schema(description = "展示顺序（后端按数组下标在同语言内重排，提交值仅作参考）")
    private Integer sortOrder;

    /*
     * 模块四方向间距（契约 1.4）：与 content 无关的模块级布局属性。
     * 允许缺省/null（后端按 0 处理）；取值 0–500 整数，单位 px。
     */
    @Schema(description = "上间距(px)，0–500，缺省 0")
    private Integer marginTop;

    @Schema(description = "右间距(px)，0–500，缺省 0")
    private Integer marginRight;

    @Schema(description = "下间距(px)，0–500，缺省 0")
    private Integer marginBottom;

    @Schema(description = "左间距(px)，0–500，缺省 0")
    private Integer marginLeft;
}
