package org.jeecg.modules.shop.account.service;

import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.modules.shop.account.entity.dto.ChangePasswordDTO;
import org.jeecg.modules.shop.account.entity.dto.GoogleCallbackDTO;
import org.jeecg.modules.shop.account.entity.dto.LoginEmailCodeDTO;
import org.jeecg.modules.shop.account.entity.dto.LoginPasswordDTO;
import org.jeecg.modules.shop.account.entity.dto.RegisterDTO;
import org.jeecg.modules.shop.account.entity.dto.ResetPasswordDTO;
import org.jeecg.modules.shop.account.entity.dto.SendEmailCodeDTO;
import org.jeecg.modules.shop.account.entity.dto.UpdateAvatarDTO;
import org.jeecg.modules.shop.account.entity.dto.UpdateProfileDTO;
import org.jeecg.modules.shop.account.entity.vo.GoogleAuthUrlVO;
import org.jeecg.modules.shop.account.entity.vo.LoginResultVO;
import org.jeecg.modules.shop.account.entity.vo.PasswordOpVO;
import org.jeecg.modules.shop.account.entity.vo.SendEmailCodeVO;
import org.jeecg.modules.shop.account.entity.vo.ShopProfileVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * 店铺端账号体系 Service
 * </p>
 *
 * <b>关于异常</b>：本接口统一以 {@code JeecgBootException} 表达业务失败，
 * 由全局异常处理器翻译成 {@code Result.error(msg)}。
 * 只有"需要区分 HTTP 语义"的场合才由 Controller 直接返回 Result。
 * 这样 Service 不需要依赖 {@code Result} 与 servlet 类型，可测性更好。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

public interface IShopAccountService {

    // ==================================================================
    // 2.1 发送邮箱验证码
    // ==================================================================

    /**
     * 发送邮箱验证码（含图形验证码校验 + 限流 + 场景下邮箱存在性检查）。
     */
    SendEmailCodeVO sendEmailCode(SendEmailCodeDTO dto, HttpServletRequest request);

    // ==================================================================
    // 2.2 注册
    // ==================================================================

    /**
     * 邮箱注册并直接登录（免二次登录）。
     */
    LoginResultVO register(RegisterDTO dto, HttpServletRequest request);

    // ==================================================================
    // 2.3 邮箱 + 密码登录
    // ==================================================================

    LoginResultVO loginByPassword(LoginPasswordDTO dto, HttpServletRequest request);

    // ==================================================================
    // 2.4 邮箱 + 邮箱验证码登录（未注册自动建号）
    // ==================================================================

    LoginResultVO loginByEmailCode(LoginEmailCodeDTO dto, HttpServletRequest request);

    // ==================================================================
    // 2.5 Google 登录
    // ==================================================================

    /**
     * 生成 Google 授权地址（含写入 Redis 的 state）。
     */
    GoogleAuthUrlVO buildGoogleAuthUrl(String redirectUri);

    /**
     * Google 回调：消费 state、换 token、取 userinfo、登录或自动注册。
     */
    LoginResultVO loginByGoogle(GoogleCallbackDTO dto, HttpServletRequest request);

    // ==================================================================
    // 2.6 忘记密码
    // ==================================================================

    PasswordOpVO resetPassword(ResetPasswordDTO dto, HttpServletRequest request);

    // ==================================================================
    // 2.7 个人中心：设置 / 修改密码
    // ==================================================================

    /**
     * @param userId 当前登录用户ID（由 Controller 从登录态取出，**不接受请求体传入**）
     */
    PasswordOpVO changePassword(ChangePasswordDTO dto, String userId, HttpServletRequest request);

    // ==================================================================
    // 2.8 当前登录用户信息
    // ==================================================================

    ShopProfileVO profile(String userId);

    /**
     * 用当前登录人重新签发 token（滑动续期，清单 S25）。
     *
     * <p>调用方必须已通过 Shiro 认证；这里不再校验密码、不记登录日志，
     * 只做"账号仍有效"的兜底（不存在 / 已注销 / 已冻结）。
     *
     * @param userId 当前登录用户 ID（由 Controller 从登录态取出，不接受请求体传入）
     * @return 新的 token（有效期与登录时一致）
     */
    String refreshToken(String userId);

    // ==================================================================
    // 2.9 个人中心：保存资料 / 换头像（均需登录）
    // ==================================================================

    /**
     * 保存个人资料（昵称 + 手机号）。
     *
     * <p>{@code userId} 由 Controller 从登录态取出，**绝不接受请求体传入**（水平越权防护）。
     *
     * @return 保存后的最新资料（前端直接用它刷新共享的 userInfo，不必再拉一次 /profile）
     */
    ShopProfileVO updateProfile(UpdateProfileDTO dto, String userId);

    /**
     * 选择预设头像 / 恢复默认头像。
     *
     * <p>只接受服务端白名单里的站内路径或空串，不接受任意 URL。
     */
    ShopProfileVO updateAvatar(UpdateAvatarDTO dto, String userId);

    /**
     * 上传自定义头像：校验图片类型与体积 → 存入 OSS → 写 sys_user.avatar。
     *
     * <p>URL 由**服务端**生成后入库，前端拿不到"自己指定 URL"的能力。
     */
    ShopProfileVO uploadAvatar(MultipartFile file, String userId);
}
