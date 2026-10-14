package org.jeecg.modules.shop.order.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.modules.shop.order.entity.ShopOrderEvent;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 订单操作流水出参。
 * </p>
 *
 * 继承实体，直接把台账暴露出来——**账单类的数据不做裁剪**：
 * 出问题时"少了一个字段"就等于少了一条线索。
 * 里面的手机号/IP 等敏感信息由写入端在落库前就脱敏好了（见 {@code ShopMaskUtils}）。
 *
 * @author Cikian
 * @since 2026-10-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "订单操作流水")
public class OrderEventVO extends ShopOrderEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "事件类型中文")
    private String eventTypeLabel;

    @Schema(description = "操作者类型中文")
    private String operatorTypeLabel;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "发生时间(便于前端表格直接展示)")
    private Date occurredTime;
}
