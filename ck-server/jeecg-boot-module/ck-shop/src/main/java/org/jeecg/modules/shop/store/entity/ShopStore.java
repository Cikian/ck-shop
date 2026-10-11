package org.jeecg.modules.shop.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 店铺（= 一个前台站点）
 * </p>
 * 一个管理端、多个前台：商品主体全局共享，"属于哪个店"是渠道属性，
 * 落在 ck_shop_product_store 上；店铺身份由前端站点服务端配置（X-Store-Code）
 * 或域名（Host）解析得到。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-12
 */

@Data
@TableName("ck_shop_store")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "店铺(前台站点)")
public class ShopStore implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    /**
     * 店铺编码：前端站点服务端配置里用的那个值，接口通过 X-Store-Code 传递
     */
    @Schema(description = "店铺编码")
    private String code;

    @Schema(description = "店铺名称")
    private String name;

    /**
     * 前台域名：X-Store-Code 缺失时按 Host 兜底解析
     */
    @Schema(description = "前台域名")
    private String domain;

    @Schema(description = "店铺Logo")
    private String logoUrl;

    @Schema(description = "主题配置JSON")
    private String themeConfig;

    @Schema(description = "简介")
    private String description;

    @Schema(description = "排序")
    private Integer sortOrder;

    /**
     * 状态: N-停用, Y-启用。停用的店铺不参与接口解析，前台会直接报错而不是回退到别的店。
     */
    @Schema(description = "状态: N-停用, Y-启用")
    private String status;

    @TableLogic
    @Schema(description = "删除标记: 0-正常, 1-已删除")
    private Integer delFlag;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建日期")
    private Date createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新日期")
    private Date updateTime;
}
