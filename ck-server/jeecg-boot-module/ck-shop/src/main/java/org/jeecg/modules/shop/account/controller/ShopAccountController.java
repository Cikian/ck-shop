package org.jeecg.modules.shop.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.shop.account.entity.dto.ChangePasswordDTO;
import org.jeecg.modules.shop.account.entity.dto.GoogleCallbackDTO;
import org.jeecg.modules.shop.account.entity.dto.LoginEmailCodeDTO;
import org.jeecg.modules.shop.account.entity.dto.LoginPasswordDTO;
import org.jeecg.modules.shop.account.entity.dto.RegisterDTO;
import org.jeecg.modules.shop.account.entity.dto.ResetPasswordDTO;
import org.jeecg.modules.shop.account.entity.dto.SendEmailCodeDTO;
import org.jeecg.modules.shop.account.entity.vo.GoogleAuthUrlVO;
import org.jeecg.modules.shop.account.entity.vo.LoginResultVO;
import org.jeecg.modules.shop.account.entity.vo.PasswordOpVO;
import org.jeecg.modules.shop.account.entity.vo.SendEmailCodeVO;
import org.jeecg.modules.shop.account.entity.vo.ShopProfileVO;
import org.jeecg.modules.shop.account.service.IShopAccountService;
import org.jeecg.modules.shop.store.ShopOrgScopeResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 店铺端账号体系（C 端买家）
 * </p>
 *
 * 统一前缀 {@code /shop/account}。
 *
 * <h3>Shiro 白名单的粒度（踩过坑，勿改回整段放行）</h3>
 * 只有**匿名可达**的接口进白名单（见 {@code jeecg.shiro.excludeUrls}）：
 * <pre>
 *   /shop/account/email/send-code   发码（用户还没登录）
 *   /shop/account/register          注册
 *   /shop/account/login/**          两条登录路径
 *   /shop/account/google/**         Google 授权地址与回调
 *   /shop/account/password/reset    忘记密码
 * </pre>
 * 而 {@code /profile}、{@code /password/change} **不在白名单内**，由 Shiro 正常鉴权。
 *
 * <p>⚠️ 早期版本把整个 {@code /shop/account/**} 放进白名单，导致 Shiro 跳过认证、
 * Subject 里没有 principal —— 这两个接口拿着合法 token 也永远返回"请先登录"。
 * 白名单必须精确到接口。
 *
 * <h3>与管理端的关系</h3>
 * 本类**不碰** {@code /sys/login}，也不改 admin-client 的任何行为。
 * sys_user 是两边共用的表，但登录入口、返回结构、角色校验完全独立：
 * 店铺端只认 {@code shop_customer} 角色（见 Service 层 requireShopCustomer）。
 *
 * <h3>异常处理</h3>
 * Service 抛出的 {@code JeecgBootException} 由 JeecgBoot 全局异常处理器
 * 翻译成 {@code Result.error(msg)}（HTTP 200 + success=false），
 * 前端统一按 success 字段判断，不需要为每个接口写 try/catch。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Tag(name = "店铺端-账号体系")
@RestController
@RequestMapping("/shop/account")
@Slf4j
public class ShopAccountController {

    @Autowired
    private IShopAccountService shopAccountService;

    @Autowired
    private ShopOrgScopeResolver orgScopeResolver;

    // ==================================================================
    // 2.1 发送邮箱验证码
    // ==================================================================

    @Operation(summary = "店铺端-发送邮箱验证码")
    @PostMapping(value = "/email/send-code")
    public Result<SendEmailCodeVO> sendEmailCode(@RequestBody SendEmailCodeDTO dto, HttpServletRequest request) {
        return Result.OK(shopAccountService.sendEmailCode(dto, request));
    }

    // ==================================================================
    // 2.2 注册
    // ==================================================================

    @Operation(summary = "店铺端-邮箱注册")
    @PostMapping(value = "/register")
    public Result<LoginResultVO> register(@RequestBody RegisterDTO dto, HttpServletRequest request) {
        return Result.OK(shopAccountService.register(dto, request));
    }

    // ==================================================================
    // 2.3 邮箱 + 密码登录
    // ==================================================================

    @Operation(summary = "店铺端-邮箱密码登录")
    @PostMapping(value = "/login/password")
    public Result<LoginResultVO> loginByPassword(@RequestBody LoginPasswordDTO dto, HttpServletRequest request) {
        return Result.OK(shopAccountService.loginByPassword(dto, request));
    }

    // ==================================================================
    // 2.4 邮箱 + 邮箱验证码登录
    // ==================================================================

    @Operation(summary = "店铺端-邮箱验证码登录(未注册自动建号)")
    @PostMapping(value = "/login/email-code")
    public Result<LoginResultVO> loginByEmailCode(@RequestBody LoginEmailCodeDTO dto, HttpServletRequest request) {
        return Result.OK(shopAccountService.loginByEmailCode(dto, request));
    }

    // ==================================================================
    // 2.5 Google 登录
    // ==================================================================

    @Operation(summary = "店铺端-获取 Google 授权地址")
    @GetMapping(value = "/google/auth-url")
    public Result<GoogleAuthUrlVO> googleAuthUrl(
            @RequestParam(name = "redirectUri", required = false) String redirectUri) {
        // 未配置 google.client-id/secret 时，Service 抛业务异常 →
        // 返回明确文案，前端据此禁用 Google 按钮（契约 2.5 要求）
        return Result.OK(shopAccountService.buildGoogleAuthUrl(redirectUri));
    }

    @Operation(summary = "店铺端-Google 登录回调")
    @PostMapping(value = "/google/callback")
    public Result<LoginResultVO> googleCallback(@RequestBody GoogleCallbackDTO dto, HttpServletRequest request) {
        return Result.OK(shopAccountService.loginByGoogle(dto, request));
    }

    // ==================================================================
    // 2.6 忘记密码
    // ==================================================================

    @Operation(summary = "店铺端-重置密码(忘记密码)")
    @PostMapping(value = "/password/reset")
    public Result<PasswordOpVO> resetPassword(@RequestBody ResetPasswordDTO dto, HttpServletRequest request) {
        return Result.OK(shopAccountService.resetPassword(dto, request));
    }

    // ==================================================================
    // 2.7 个人中心：设置 / 修改密码（需登录）
    // ==================================================================

    @Operation(summary = "店铺端-设置/修改密码")
    @PostMapping(value = "/password/change")
    public Result<PasswordOpVO> changePassword(@RequestBody ChangePasswordDTO dto, HttpServletRequest request) {
        // 本路径在 Shiro 白名单内，因此**必须**自己校验登录态。
        // 用户ID 只从登录态取，绝不接受请求体传入 ——
        // 否则任何人都能改别人的密码（水平越权）。
        LoginUser user = requireLoginUser();
        return Result.OK(shopAccountService.changePassword(dto, user.getId(), request));
    }

    // ==================================================================
    // 2.8 当前登录用户信息（需登录）
    // ==================================================================

    @Operation(summary = "店铺端-当前登录用户信息")
    @GetMapping(value = "/profile")
    public Result<ShopProfileVO> profile() {
        LoginUser user = requireLoginUser();
        return Result.OK(shopAccountService.profile(user.getId()));
    }

    /**
     * 滑动续期：用当前登录态换一张新 token（清单 S25）。
     *
     * <p>只返回 token，不返回用户信息 —— 调用方（BFF）只需要替换 cookie。
     * 未登录时 Shiro 会先拦掉，不会走到这里。
     */
    @Operation(summary = "店铺端-续期登录态")
    @PostMapping(value = "/refresh-token")
    public Result<String> refreshToken() {
        LoginUser user = requireLoginUser();
        return Result.OK("续期成功", shopAccountService.refreshToken(user.getId()));
    }

    // ==================================================================
    // 登录态校验
    // ==================================================================

    /**
     * 取当前登录人，未登录直接报错。
     *
     * <p><b>为什么这里能拿到登录人</b>：{@code /shop/account/**} 里**只有匿名接口**
     * 被放进 Shiro 白名单（见 {@code jeecg.shiro.excludeUrls}），
     * {@code /profile} 与 {@code /password/change} **不在**白名单里，
     * 因此 Shiro 会照常解析 {@code X-Access-Token} 并设置 Subject principal，
     * {@link ShopOrgScopeResolver#currentLoginUser()} 才取得到人。
     *
     * <p>⚠️ 曾经把整个 {@code /shop/account/**} 放进白名单，结果是 Shiro 完全跳过认证、
     * Subject 里没有 principal，这两个接口**拿着合法 token 也永远返回"请先登录"**。
     * 白名单必须精确到匿名接口，不能整段放行。
     */
    private LoginUser requireLoginUser() {
        LoginUser user = orgScopeResolver.currentLoginUser();
        if (user == null || user.getId() == null) {
            throw new org.jeecg.common.exception.JeecgBootException("请先登录");
        }
        return user;
    }
}
