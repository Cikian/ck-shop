package org.jeecg.modules.shop.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 商品详情模块（结构化详情，按 商品 + 语言 分行）
 * </p>
 *
 * <p>契约见 docs/商品详情-设计契约.md 第 1.1 / 1.3 节。</p>
 *
 * <p><b>为什么没有 del_flag：</b>模块是内容型数据，不被订单/售后引用，也没有唯一索引；
 * 做逻辑删除只会让长文本行无限堆积，因此删除策略是<b>物理删除</b>（契约 1.1）。</p>
 *
 * <p><b>content 的类型差异：</b>数据库里是 JSON 字符串（longtext），实体里保持 String；
 * 只有 DTO/VO 层才转成 {@code Map<String,Object>}，转换与容错都收口在
 * {@link org.jeecg.modules.shop.product.util.ShopDetailBlockSupport} 里。</p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-20
 */
@Data
@TableName("ck_shop_product_detail_block")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "商品详情模块")
public class ShopProductDetailBlock implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    /**
     * 商品ID（ck_shop_product.id）
     */
    @Schema(description = "商品ID")
    private String productId;

    /**
     * 语言代码: zh/en/fr...
     */
    @Schema(description = "语言代码")
    private String languageCode;

    /**
     * 模块类型: IMAGE/IMAGE_GRID/IMAGE_TEXT/TEXT/RICH_TEXT/VIDEO/TITLE/DIVIDER
     */
    @Schema(description = "模块类型")
    private String blockType;

    /**
     * 模块内容：JSON 字符串（结构见契约 1.3），接口层再转 Map
     */
    @Schema(description = "模块内容(JSON 字符串)")
    private String content;

    /**
     * 同语言内的展示顺序（升序）
     */
    @Schema(description = "同语言内展示顺序")
    private Integer sortOrder;

    /*
     * 模块四方向间距（契约 1.4）：为「长图切割后无缝拼接」而加。
     * 默认全部 0 —— 模块之间、模块与容器之间不再有默认留白；由运营逐模块按需调整。
     * 存表列而不是 content 字段：布局属于模块级属性，与模块内容无关，便于统一渲染与排查。
     */
    @Schema(description = "上间距(px)")
    private Integer marginTop;

    @Schema(description = "右间距(px)")
    private Integer marginRight;

    @Schema(description = "下间距(px)")
    private Integer marginBottom;

    @Schema(description = "左间距(px)")
    private Integer marginLeft;

    @Schema(description = "创建人")
    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建日期")
    private Date createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新日期")
    private Date updateTime;
}
