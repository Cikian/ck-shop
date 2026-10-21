package org.jeecg.modules.shop.store.entity.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 店铺选项（对外安全投影）
 * </p>
 *
 * <h3>为什么不能直接返回 {@code ShopStore} 实体</h3>
 * {@code /shop/store/all} 是"任意已登录账号（含店铺端买家 token）都能调"的接口。
 * 直接返回实体等于把 {@code departId / departOrgCode / domain / themeConfig / createBy}
 * 这些**内部组织与配置信息**一起交出去：拿到组织编码就能拼出别家店铺的数据范围，
 * 这正是多店铺隔离最怕的泄露。
 *
 * <p>本 VO 只包含"选择店铺"这件事真正需要的字段。
 * 只有持有 {@code shop-store:manage-all} 的账号（商品表单里要改商品归属）才会额外拿到
 * {@link #departOrgCode}，其他调用方该字段为 {@code null} 且不参与序列化。
 *
 * @author Cikian
 * @since 2026-10-22
 */
@Data
@Accessors(chain = true)
@Schema(description = "店铺选项(对外安全字段)")
public class StoreOptionVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "店铺ID")
    private String id;

    @Schema(description = "店铺编码")
    private String code;

    @Schema(description = "店铺名称")
    private String name;

    @Schema(description = "店铺Logo")
    private String logoUrl;

    @Schema(description = "排序")
    private Integer sortOrder;

    /**
     * 绑定部门的 org_code（商品归属需要）。
     *
     * <p>仅 {@code shop-store:manage-all} 持有者可见；为 null 时整个字段不输出，
     * 避免前端看到 {@code "departOrgCode": null} 而误以为"店铺没绑部门"。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "绑定部门编码(仅管理全部店铺的账号返回)")
    private String departOrgCode;
}
