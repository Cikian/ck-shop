package org.jeecg.modules.shop.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.poi.excel.annotation.Excel;

import java.io.Serializable;
import java.util.Date;
/**
 * <p>
 * 店铺（= 一个前台站点 + 一个数据隔离部门）
 * </p>
 * 店铺与 JeecgBoot 部门 **1:1**：{@code depart_id} 指向 {@code sys_depart.id}，
 * 商品/分类的归属列统一是部门编码 {@code sys_org_code}，因此
 * "这个商品属于哪个店" = "这个商品属于哪个部门"。
 * 店铺身份由前端站点服务端配置（X-Store-Code）或域名（Host）解析得到。
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
    @Excel(name = "店铺编码", width = 20)
    @Schema(description = "店铺编码")
    private String code;

    @Excel(name = "店铺名称", width = 25)
    @Schema(description = "店铺名称")
    private String name;

    /**
     * 前台域名：X-Store-Code 缺失时按 Host 兜底解析
     */
    @Excel(name = "前台域名", width = 30)
    @Schema(description = "前台域名")
    private String domain;

    /**
     * 绑定部门（sys_depart.id）。店铺 ↔ 部门 1:1：
     * 商品、分类的归属都按该部门的 org_code 隔离，运营只能管本部门的数据。
     *
     * 带上 @Excel 是为了"导出→改→导入"的往返：导入会逐行走 saveStore 的校验
     * （部门必须存在、必须是「部门」节点、不能被两个店铺绑定）。
     */
    @Excel(name = "绑定部门ID", width = 25)
    @Schema(description = "绑定部门ID(sys_depart.id)")
    private String departId;

    /**
     * 绑定部门的 org_code —— **只读派生字段，不落库**。
     * 由列表查询 join sys_depart 带出，供前端把"所属店铺"下拉映射成商品的 sys_org_code，
     * 避免把部门树这种东西再暴露一层给商品表单。
     */
    @TableField(exist = false)
    @Schema(description = "绑定部门编码(只读派生, 不落库)")
    private String departOrgCode;

    @Schema(description = "店铺Logo")
    private String logoUrl;

    @Schema(description = "主题配置JSON")
    private String themeConfig;

    @Excel(name = "简介", width = 30)
    @Schema(description = "简介")
    private String description;

    @Excel(name = "排序", width = 10)
    @Schema(description = "排序")
    private Integer sortOrder;

    /**
     * 状态: N-停用, Y-启用。停用的店铺不参与接口解析，前台会直接报错而不是回退到别的店。
     */
    @Excel(name = "状态", width = 10, replace = {"启用_Y", "停用_N"})
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
