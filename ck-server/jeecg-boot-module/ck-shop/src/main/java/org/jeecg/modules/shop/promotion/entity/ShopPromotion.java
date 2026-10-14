package org.jeecg.modules.shop.promotion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * <p>
 * 促销活动主表（ck_shop_promotion）
 * </p>
 *
 * 店铺归属列是 {@code store_id}（= ck_shop_store.id），与商品/分类用
 * {@code sys_org_code} 隔离**不同**：促销活动在建模上直接挂在店铺上。
 * 因此店铺端查询用店铺 id 过滤，而不是部门编码。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-13
 */

@Data
@TableName("ck_shop_promotion")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "促销活动")
public class ShopPromotion implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    /** 所属店铺(ck_shop_store.id) */
    @Schema(description = "所属店铺ID")
    private String storeId;

    @Excel(name = "活动编码", width = 20)
    @Schema(description = "活动编码")
    private String code;

    /**
     * 活动类型: DIRECT-直降 / PERCENT-折扣 / FULL_CUT-满减 / SECKILL-秒杀
     */
    /*
     * 类型/状态用 replace 输出中文，而不是导出成 DIRECT / Y：
     * 导出的表要么给运营看，要么被改完再导回来；英文枚举值在 Excel 里既看不懂也容易改错
     * （写成"直降"而不是"DIRECT"就静默失效）。
     */
    @Excel(name = "活动类型", width = 15, replace = {"直降_DIRECT", "折扣_PERCENT", "满减_FULL_CUT", "秒杀_SECKILL"})
    @Schema(description = "活动类型: DIRECT-直降 / PERCENT-折扣 / FULL_CUT-满减 / SECKILL-秒杀")
    private String type;

    @Excel(name = "活动名称", width = 25)
    @Schema(description = "活动名称")
    private String title;

    @Excel(name = "副标题", width = 30)
    @Schema(description = "副标题/宣传语")
    private String subTitle;

    /**
     * 活动落地页路径（不含语言前缀，由前端按当前语言补 /zh、/en）。
     * 为空表示该活动没有落地页，前端不应渲染入口——避免出现点了没反应的死链。
     */
    @Schema(description = "活动落地页路径(不含语言前缀; 为空则前端不渲染入口)")
    private String linkUrl;

    /**
     * 秒杀条背景图 URL。
     * 为空时前台用品牌色渐变兜底——**不要在这里塞默认图**：
     * 不同店铺品牌色不同，后端硬编码一张图会让所有店铺长得一样。
     */
    @Excel(name = "背景图", width = 40)
    @Schema(description = "秒杀条背景图URL; 为空时前台用品牌色渐变兜底")
    private String bgImage;

    @Excel(name = "排序", width = 10)
    @Schema(description = "优先级(同商品多活动时取值顺序)")
    private Integer sortOrder;

    @Excel(name = "开始时间", width = 22)
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间")
    private Date startTime;

    /** 结束时间 —— 店铺端倒计时的唯一依据 */
    @Excel(name = "结束时间", width = 22)
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间(店铺端倒计时依据)")
    private Date endTime;

    /** 直降金额 或 折扣率(如 8.50 = 8.5 折) */
    @Excel(name = "优惠力度", width = 15)
    @Schema(description = "直降金额 或 折扣率(如8.50=8.5折)")
    private BigDecimal discountValue;

    /** 满减门槛(FULL_CUT 用) */
    @Excel(name = "满减门槛", width = 15)
    @Schema(description = "满减门槛(FULL_CUT用)")
    private BigDecimal thresholdAmount;

    /** 秒杀限量(SECKILL 用) */
    @Excel(name = "秒杀限量", width = 15)
    @Schema(description = "秒杀限量(SECKILL用)")
    private Integer seckillStock;

    /** 每人限购(SECKILL 用) */
    @Excel(name = "每人限购", width = 15)
    @Schema(description = "每人限购(SECKILL用)")
    private Integer limitPerUser;

    /**
     * 状态: Y-启用, N-停用
     */
    @Excel(name = "状态", width = 10, replace = {"启用_Y", "停用_N"})
    @Schema(description = "状态: Y-启用, N-停用")
    private String status;

    /** 删除标记: 0-正常, 1-已删除 */
    @TableLogic(value = "0", delval = "1")
    @Schema(description = "删除标记: 0-正常, 1-已删除")
    private Integer delFlag;

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

    /**
     * 店铺名称 —— **只读派生字段，不落库**，由列表查询按 store_id 批量带出。
     */
    @TableField(exist = false)
    @Schema(description = "店铺名称(只读派生,不落库)")
    private String storeName;

    /**
     * 店铺编码 —— **只读派生字段，不落库**。
     *
     * <p>导入 Excel 时运营手上只有"店铺编码"（他们填的是 default 这种码），
     * 不可能去抄 36 位 uuid，因此导入用编码定位店铺，再转成 storeId 落库。
     */
    @TableField(exist = false)
    @Schema(description = "店铺编码(只读派生,不落库;导入按编码定位店铺)")
    private String storeCode;
}
