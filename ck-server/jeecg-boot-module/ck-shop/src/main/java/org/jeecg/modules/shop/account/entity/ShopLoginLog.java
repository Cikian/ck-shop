package org.jeecg.modules.shop.account.entity;

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
 * 店铺端登录来源流水
 * </p>
 *
 * 每次登录**尝试**写一行（成功与失败都写），用途有两个：
 * <ol>
 *   <li>排查："这个账号为什么登不上" —— 失败原因落在 fail_reason 上，
 *       不写失败记录的话用户只会看到一句"密码错误"，运维无从下手；</li>
 *   <li>归因：store_code / store_id 记录这次登录是从哪个前台站点发起的，
 *       用来算"某店铺的登录量/留存"，与注册来源（sys_user.register_store_*）分开看。</li>
 * </ol>
 *
 * 这张表**只增不改**，没有 del_flag / update_time：流水表做软删除毫无意义，
 * 反而会让"某天的登录量"随删除而缩水，对不上账。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Data
@TableName("ck_shop_login_log")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "店铺端登录来源流水")
public class ShopLoginLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Schema(description = "用户ID")
    private String userId;

    /** 冗余一份账号与邮箱：用户后续改了邮箱，历史流水仍应反映"当时用哪个邮箱登的" */
    @Schema(description = "用户账号(冗余)")
    private String username;

    @Schema(description = "登录邮箱(冗余)")
    private String email;

    @Schema(description = "登录方式: PASSWORD/EMAIL_CODE/GOOGLE")
    private String loginType;

    @Schema(description = "登录来源店铺编码")
    private String storeCode;

    @Schema(description = "登录来源店铺ID")
    private String storeId;

    @Schema(description = "客户端IP")
    private String clientIp;

    @Schema(description = "User-Agent")
    private String userAgent;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "登录时间")
    private Date loginTime;

    @Schema(description = "是否成功: 1-成功, 0-失败")
    private Integer success;

    @Schema(description = "失败原因")
    private String failReason;
}
