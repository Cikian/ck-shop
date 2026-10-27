package org.jeecg.modules.shop.account.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 店铺端-选择头像（预设头像 / 恢复默认）
 * </p>
 *
 * <p><b>为什么只能传"站内预设路径"</b>：如果这里接受任意 URL，用户就能把自己的头像
 * 指到一个外部地址 —— 既是存储型 XSS/追踪像素的入口（别人看它的主页时就会请求那个域名），
 * 也让"头像一定是我们自己的图"这个前提失效。</p>
 *
 * <p>用户上传的图片走 {@code /shop/account/profile/avatar/upload}（服务端上传 OSS 并写库），
 * 那个接口的 URL 由服务端自己生成，同样不经过这里。</p>
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-27
 */
@Data
@Schema(description = "店铺端-选择头像")
public class UpdateAvatarDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 预设头像站内路径（如 /avatars/preset-1.webp）；空串表示恢复默认（无头像） */
    @Schema(description = "预设头像路径或空串(恢复默认)")
    private String avatar;
}
