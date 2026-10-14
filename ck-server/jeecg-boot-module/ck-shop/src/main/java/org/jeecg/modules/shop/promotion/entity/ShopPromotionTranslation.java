package org.jeecg.modules.shop.promotion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 促销活动多语言表（ck_shop_promotion_translation）
 * </p>
 *
 * 唯一键是 {@code (promotion_id, language_code)}：一个活动一种语言只能有一行。
 * 语言清单的唯一来源是 {@code nuxt-shop/types/store-locale.ts} 的 STORE_LOCALES（zh/en/fr），
 * 该表**没有 del_flag 列**（与 {@code ck_shop_category_translation} 一致），
 * 被移除的语言行直接物理删除，不要加 {@code @TableLogic}。
 *
 * <p>为什么主表还留着 {@code title / sub_title}：
 * 主表那两列是**兜底文案**，多语言全部缺失（历史数据、导入数据）时前台仍要能渲染，
 * 排序是 {@code translation[lang] → translation['zh'] → 主表}。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@TableName("ck_shop_promotion_translation")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "促销活动多语言表")
public class ShopPromotionTranslation implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Excel(name = "关联活动ID", width = 25)
    @Schema(description = "关联活动ID")
    private String promotionId;

    /** zh / en / fr，与 ck_shop_category_translation 的取值保持一致 */
    @Excel(name = "语言代码", width = 10)
    @Schema(description = "语言代码: zh/en/fr")
    private String languageCode;

    @Excel(name = "活动名称", width = 25)
    @Schema(description = "该语言下的活动名称")
    private String title;

    @Excel(name = "副标题", width = 30)
    @Schema(description = "该语言下的副标题")
    private String subTitle;

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
