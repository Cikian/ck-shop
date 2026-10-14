package org.jeecg.modules.shop.order.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 后台发货入参。
 * </p>
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@Schema(description = "发货入参")
public class OrderShipDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "订单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderNo;

    /**
     * 物流公司 / 单号在这里是**选填**还是必填？
     *
     * <p>本系统当前没有对接物流平台，虚拟发货（如虚拟商品、门店自提）是真实存在的场景，
     * 因此不强制。但两者**要么都填、要么都不填**：只填公司不填单号
     * 会让买家拿着一个空单号去查物流，客服必然收到投诉。
     */
    @Schema(description = "物流公司(与物流单号成对填写)")
    private String logisticsCompany;

    @Schema(description = "物流单号(与物流公司成对填写)")
    private String logisticsNo;

    @Schema(description = "发货备注")
    private String shipRemark;
}
