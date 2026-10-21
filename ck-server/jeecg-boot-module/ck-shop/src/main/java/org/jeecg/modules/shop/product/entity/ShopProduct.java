package org.jeecg.modules.shop.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * <p>
 * 商品 SPU 主表
 * </p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-11
 */

@Data
@TableName("ck_shop_product")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "商品")
public class ShopProduct implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;
    /**
     * 商品SPU编码
     */
    @Excel(name = "商品SPU编码", width = 25)
    @Schema(description = "商品SPU编码")
    private String spuCode;
    /**
     * 基准价格
     */
    @Excel(name = "基准价格", width = 15)
    @Schema(description = "基准价格")
    private BigDecimal price;
    /**
     * 成本价（毛利统计口径）。
     *
     * <p>下单时会把成本价**快照**进订单明细：采购价会变，
     * 如果不在下单时锁定，事后算出来的"历史毛利"会随成本价改而变，
     * 月度报表永远对不上。
     *
     * <p>为 0 表示未录入，此时订单毛利偏高——这是显式已知的口径，
     * 比"编一个成本"诚实。SKU 成本价优先于商品成本价。
     */
    @Excel(name = "成本价", width = 15)
    @Schema(description = "成本价(毛利统计; 0=未录入)")
    private BigDecimal costPrice;
    /**
     * 划线原价/市场价，NULL 表示前端不展示划线价
     */
    @Excel(name = "划线原价", width = 15)
    @Schema(description = "划线原价/市场价，NULL=不展示划线")
    private BigDecimal originalPrice;
    /**
     * 最低售价(SKU汇总)
     */
    @Excel(name = "最低售价", width = 15)
    @Schema(description = "最低售价(SKU汇总)")
    private BigDecimal minPrice;
    /**
     * 最高售价(SKU汇总)
     */
    @Excel(name = "最高售价", width = 15)
    @Schema(description = "最高售价(SKU汇总)")
    private BigDecimal maxPrice;
    /**
     * 总库存
     */
    @Excel(name = "总库存", width = 15)
    @Schema(description = "总库存")
    private Integer stock;
    /**
     * 预占库存（下单占用，支付成功后转实扣）。
     *
     * <p>真正可售量 = {@code stock - frozenStock}。
     * 所有预占都在带条件的原子 UPDATE 里完成（见 {@code ShopStockMapper}），
     * 不存在"查出来再判断"的超卖窗口。
     */
    @Schema(description = "预占库存(下单占用)")
    private Integer frozenStock;
    /**
     * 累计销量
     */
    @Excel(name = "累计销量", width = 15)
    @Schema(description = "累计销量")
    private Integer salesCount;
    /**
     * 近 30 天销量
     */
    @Excel(name = "近30天销量", width = 15)
    @Schema(description = "近 30 天销量")
    private Integer monthSalesCount;
    /**
     * 评价总数
     */
    @Excel(name = "评价总数", width = 15)
    @Schema(description = "评价总数")
    private Integer reviewCount;
    /**
     * 平均评分 0.0-5.0
     */
    @Excel(name = "平均评分", width = 15)
    @Schema(description = "平均评分 0.0-5.0")
    private BigDecimal ratingAvg;
    /**
     * 收藏数
     */
    @Excel(name = "收藏数", width = 15)
    @Schema(description = "收藏数")
    private Integer favoriteCount;
    /**
     * 浏览量
     */
    @Excel(name = "浏览量", width = 15)
    @Schema(description = "浏览量")
    private Integer viewCount;
    /**
     * 是否包邮。
     *
     * <p>库里是 {@code tinyint(1)}，这里用 {@code Boolean} 而不是 {@code Integer}：
     * 店铺端列表接口（StoreProductCardVO）与详情接口（StoreProductDetailVO）
     * 必须返回**同一类型**。否则同一个字段一个返回 {@code true} 另一个返回 {@code 1}，
     * 前端就得为同一个语义写两套判断，迟早出错。
     */
    @Excel(name = "是否包邮", width = 15, replace = {"是_true", "否_false"})
    @Schema(description = "是否包邮")
    private Boolean freeShipping;
    /**
     * 单位
     */
    @Excel(name = "单位", width = 15)
    @Schema(description = "单位")
    private String unit;
    /**
     * 状态: N-下架, Y-上架
     */
    @Excel(name = "状态", width = 15, replace = {"上架_Y", "下架_N"})
    @Schema(description = "状态: N-下架, Y-上架")
    private String status;
    /**
     * 排序
     */
    @Excel(name = "排序", width = 15)
    @Schema(description = "排序")
    private Integer sortOrder;
    /**
     * 是否推荐: N-否, Y-是
     */
    @Excel(name = "是否推荐", width = 15, replace = {"是_Y", "否_N"})
    @Schema(description = "是否推荐: N-否, Y-是")
    private String isRecommend;
    /**
     * 分类ID
     */
    @Excel(name = "分类ID", width = 25)
    @Schema(description = "分类ID")
    private String categoryId;
    /**
     * 归属部门编码（JeecgBoot 数据隔离维度，对应 ck_shop_store.depart_id → sys_depart.org_code）。
     *
     * <p>新增时由 {@code MybatisInterceptor} 自动填充为当前登录人的部门编码；
     * 编辑时**不允许运营修改归属**（后端硬校验），只有 {@code shop-store:manage-all}
     * 权限的账号可以跨部门调整。前缀式编码使其天然支持"本部门及下级部门"的可见域。
     */
    @Schema(description = "归属部门编码(sys_org_code)")
    private String sysOrgCode;
    /**
     * 上游来源：取自系统字典 shop_upstream_source（暂定：淘宝/京东/1688/闲鱼）
     */
    @Schema(description = "上游来源(字典 shop_upstream_source)")
    @Dict(dicCode = "shop_upstream_source")
    private String upstreamSource;
    /**
     * 上游链接：手动录入的商品来源地址，详情页可直接点击跳转
     */
    @Schema(description = "上游链接")
    private String upstreamUrl;
    /**
     * 主图URL
     */
    @Excel(name = "主图URL", width = 30)
    @Schema(description = "主图URL")
    private String mainImage;
    /**
     * 上架时间（NULL 表示从未上架）
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "上架时间")
    private Date listedAt;
    /**
     * 创建人
     */
    @Schema(description = "创建人")
    private String createBy;
    /**
     * 创建日期
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建日期")
    private Date createTime;
    /**
     * 更新人
     */
    @Schema(description = "更新人")
    private String updateBy;
    /**
     * 更新日期
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新日期")
    private Date updateTime;
    /**
     * 删除标记: 0-正常, 1-已删除（软删除）
     */
    @TableLogic(value = "0", delval = "1")
    @Schema(description = "删除标记: 0-正常, 1-已删除")
    private Integer delFlag;
}
