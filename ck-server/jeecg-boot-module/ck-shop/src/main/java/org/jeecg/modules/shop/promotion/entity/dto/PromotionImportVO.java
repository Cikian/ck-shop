package org.jeecg.modules.shop.promotion.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.jeecgframework.poi.excel.annotation.Excel;

import java.io.Serializable;

/**
 * <p>
 * 促销活动 Excel 导入行
 * </p>
 *
 * <b>导入范围（必须在页面上告知用户）：</b>只导入活动**主表字段**
 * （店铺编码、活动编码、类型、名称、时间窗、力度、状态）。
 * <b>多语言明细与关联商品不参与导入</b>——Excel 单行无法表达多行明细，
 * 硬塞只会让用户以为"导入了商品"而实际上什么都没有。
 * 导入后请到编辑页补多语言与关联商品。
 *
 * <p>为什么店铺用**编码**而不是 id：运营手上只有 {@code default} 这种短码，
 * 让他们去抄 36 位 uuid 不现实；服务层按编码定位店铺并校验可见域。
 *
 * <p>时间字段刻意用 {@code String}：Excel 里的时间单元格格式千奇百怪
 * （文本 / 日期 / 带时区），统一收成字符串后在服务层做多格式解析，
 * 比让 AutoPoi 直接往 {@code Date} 里塞更可控、报错也更可读。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-14
 */

@Data
@Schema(description = "促销活动Excel导入行")
public class PromotionImportVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Excel(name = "店铺编码", width = 20)
    @Schema(description = "店铺编码(ck_shop_store.code)")
    private String storeCode;

    @Excel(name = "活动编码", width = 20)
    @Schema(description = "活动编码，同店铺内唯一")
    private String code;

    @Excel(name = "活动类型", width = 15,
            replace = {"直降_DIRECT", "折扣_PERCENT", "满减_FULL_CUT", "秒杀_SECKILL"})
    @Schema(description = "活动类型: DIRECT/PERCENT/FULL_CUT/SECKILL")
    private String type;

    @Excel(name = "活动名称", width = 25)
    @Schema(description = "活动名称(写入 zh 翻译与主表兜底)")
    private String title;

    @Excel(name = "副标题", width = 30)
    @Schema(description = "副标题(写入 zh 翻译与主表兜底)")
    private String subTitle;

    @Excel(name = "开始时间", width = 22)
    @Schema(description = "开始时间, yyyy-MM-dd HH:mm:ss")
    private String startTime;

    @Excel(name = "结束时间", width = 22)
    @Schema(description = "结束时间, yyyy-MM-dd HH:mm:ss")
    private String endTime;

    @Excel(name = "优惠力度", width = 15)
    @Schema(description = "DIRECT=直降金额 / PERCENT=折扣率")
    private String discountValue;

    @Excel(name = "满减门槛", width = 15)
    @Schema(description = "FULL_CUT 门槛")
    private String thresholdAmount;

    @Excel(name = "秒杀限量", width = 15)
    @Schema(description = "SECKILL 限量")
    private String seckillStock;

    @Excel(name = "每人限购", width = 15)
    @Schema(description = "SECKILL 每人限购")
    private String limitPerUser;

    @Excel(name = "状态", width = 10, replace = {"启用_Y", "停用_N"})
    @Schema(description = "状态: Y/N，为空按停用处理")
    private String status;
}
