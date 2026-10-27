package org.jeecg.modules.shop.account.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import cn.cikian.captcha.client.ticket.CaptchaTicketService;
import cn.cikian.oss.service.OssServiceContext;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.util.JwtUtil;
import org.jeecg.common.util.PasswordUtil;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.UUIDGenerator;
import org.jeecg.common.util.encryption.AesEncryptUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.shop.account.constant.ShopAccountConstant;
import org.jeecg.modules.shop.account.entity.ShopAccountUser;
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
import org.jeecg.modules.shop.account.entity.vo.LoginUserVO;
import org.jeecg.modules.shop.account.entity.vo.PasswordOpVO;
import org.jeecg.modules.shop.account.entity.vo.SendEmailCodeVO;
import org.jeecg.modules.shop.account.entity.vo.ShopProfileVO;
import org.jeecg.modules.shop.account.mapper.ShopAccountMapper;
import org.jeecg.modules.shop.account.service.IShopAccountService;
import org.jeecg.modules.shop.account.service.ShopEmailCodeService;
import org.jeecg.modules.shop.account.service.ShopGoogleOAuthService;
import org.jeecg.modules.shop.account.service.ShopLoginLogRecorder;
import org.jeecg.modules.shop.common.ShopRateLimiter;
import org.jeecg.modules.shop.store.StoreResolver;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <p>
 * 店铺端账号体系 Service 实现
 * </p>
 *
 * <h3>这个类最容易出错的三处，以及各自的落点</h3>
 *
 * <ol>
 *   <li><b>登录失败次数限制的 key 用 email，不用 username</b>。
 *       店铺端没有用户名登录入口，用户根本不知道自己那个
 *       {@code u_xxxxxxxx} 是什么；如果按 username 计数，
 *       攻击者只要换邮箱前缀就能绕过锁定。用 email 才是"同一个人"的稳定标识。
 *       （复用 {@code CommonConstant.LOGIN_FAIL} 前缀，与 LoginController 同一套机制与阈值。）</li>
 *   <li><b>登录必须校验 {@code shop_customer} 角色</b>。
 *       店铺端与管理端共用 sys_user，若不加这道校验，管理端账号（admin）
 *       可以直接调店铺端接口拿到一个 token —— 该 token 在管理端是**有效**的，
 *       等于给了一条"从店前端接口获得管理端会话"的路径。</li>
 *   <li><b>自动注册的用户也要有密码哈希</b>。
 *       {@code JwtUtil.sign} 把密码哈希纳入签名，密码为空会直接签不出 token。
 *       所以自动注册写一个随机 32 位串的哈希（无人知道明文），
 *       并用 {@code pwd_set = 0} 标记"用户其实没有密码"。</li>
 * </ol>
 *
 * <h3>为什么直接用 ShopAccountMapper 而不是 ISysUserService</h3>
 * ck-shop 模块编译期看不到 {@code jeecg-system-biz}
 * （依赖只有 base-core + ck-system），`ISysUserService` / `SysUser` 均不可引用。
 * 详见 {@link ShopAccountMapper} 的类注释（含三条可选路线的取舍）。
 *
 * <h3>因此这里也必须自己实现 {@code checkUserIsEffective}</h3>
 * 那个方法在 {@code SysUserServiceImpl}（jeecg-system-biz）里，
 * 本模块够不到。下面 {@link #checkUserEffective} 按它的**等价语义**实现：
 * 用户不存在 / 已注销（del_flag=1）/ 已冻结（status=2）三种情况逐一拒绝。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

@Slf4j
@Service
public class ShopAccountServiceImpl implements IShopAccountService {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final ShopAccountMapper shopAccountMapper;

    private final ShopEmailCodeService emailCodeService;

    private final ShopGoogleOAuthService googleOAuthService;

    private final ShopLoginLogRecorder loginLogRecorder;

    private final StoreResolver storeResolver;

    private final RedisUtil redisUtil;

    /**
     * CikVerify 验证码 ticket 服务（SDK 自动装配，依赖 {@code ck.captcha.*} 配置）。
     *
     * <p>负责消费前端经 BFF 调用 {@code /api/captcha/verify} 后拿到的 ticket：
     * 一次性、默认 120 秒有效，并绑定客户端 IP 与 User-Agent。</p>
     */
    private final CaptchaTicketService captchaTicketService;

    private final ShopAccountSupport accountSupport;

    /** Redis 固定窗口限流：按 IP 限制发信/登录/注册/验证码尝试，防脚本刷 */
    private final ShopRateLimiter rateLimiter;

    /**
     * 站内对象存储（cik-oss，由 ck.oss.* 装配）。
     *
     * <p>头像必须落在**我们自己的**存储里：让用户直接填一个外部图片 URL 的话，
     * 那张图随时可能被第三方替换（换成违规内容）或下线（前台裂图），
     * 而且每次渲染都会向外部域名发请求（追踪像素）。</p>
     */
    private final OssServiceContext ossServiceContext;

    /** OSS 对象目录前缀（与 {@code ck.oss.object-dir-prefix} 一致） */
    @Value("${ck.oss.object-dir-prefix:}")
    private String ossDirPrefix;

    public ShopAccountServiceImpl(ShopAccountMapper shopAccountMapper,
                                  ShopEmailCodeService emailCodeService,
                                  ShopGoogleOAuthService googleOAuthService,
                                  ShopLoginLogRecorder loginLogRecorder,
                                  StoreResolver storeResolver,
                                  RedisUtil redisUtil,
                                  CaptchaTicketService captchaTicketService,
                                  ShopAccountSupport accountSupport,
                                  ShopRateLimiter rateLimiter,
                                  OssServiceContext ossServiceContext) {
        this.shopAccountMapper = shopAccountMapper;
        this.emailCodeService = emailCodeService;
        this.googleOAuthService = googleOAuthService;
        this.loginLogRecorder = loginLogRecorder;
        this.storeResolver = storeResolver;
        this.redisUtil = redisUtil;
        this.captchaTicketService = captchaTicketService;
        this.accountSupport = accountSupport;
        this.rateLimiter = rateLimiter;
        this.ossServiceContext = ossServiceContext;
    }

    // ==================================================================
    // 2.1 发送邮箱验证码
    // ==================================================================

    @Override
    public SendEmailCodeVO sendEmailCode(SendEmailCodeDTO dto, HttpServletRequest request) {
        if (dto == null) {
            throw new JeecgBootException("请求参数不能为空");
        }
        String email = ShopEmailCodeService.normalizeEmail(dto.getEmail());
        if (StringUtils.isBlank(email)) {
            throw new JeecgBootException("请填写邮箱");
        }
        if (!accountSupport.isValidEmail(email)) {
            throw new JeecgBootException("邮箱格式不正确");
        }
        String scene = StringUtils.trimToNull(dto.getScene());
        if (scene == null) {
            throw new JeecgBootException("缺少场景参数 scene");
        }
        if (!ShopAccountConstant.SCENE_REGISTER.equals(scene)
                && !ShopAccountConstant.SCENE_LOGIN.equals(scene)
                && !ShopAccountConstant.SCENE_RESET.equals(scene)) {
            throw new JeecgBootException("场景参数 scene 非法，只支持 register / login / reset");
        }

        String clientIp = accountSupport.resolveClientIp(request);

        // 第一道闸：IP 维度短窗口限流。
        // 放在验证码之前：拿着一张真验证码疯狂请求同样会打爆 SMTP / 后端线程，
        // 这一步只花一次 Redis incr，比解析参数、查库、验图都便宜。
        rateLimiter.check("send-code:ip:min:" + clientIp,
                ShopAccountConstant.RL_SEND_CODE_PER_MINUTE, 60L,
                "验证码发送过于频繁，请稍后再试");
        rateLimiter.check("send-code:ip:hour:" + clientIp,
                ShopAccountConstant.RL_SEND_CODE_PER_HOUR, 3600L,
                "当前网络发送验证码次数过多，请稍后再试");

        // 第二道闸：CikVerify 验证码 ticket 必须**先**校验通过。它是挡在发信能力前面的人工闸门。
        // ticket 由前端经 BFF 调 /api/captcha/challenge + /api/captcha/verify 获得，
        // 服务端用 SDK 的 CaptchaTicketService#consume 消费：
        //   - 一次性（Redis GETSET 原子认领，并发下只有一方成功）；
        //   - 默认 120 秒有效；
        //   - 绑定客户端 IP 与 User-Agent（BFF 已把真实 IP 覆盖进 X-Forwarded-For）。
        // ticket 为空 / 格式不对 / 已消费 / 过期 / IP/UA 不匹配，SDK 一律返回 false（fail-closed），
        // 这里统一抛"请先完成安全验证"，不区分失败原因（避免给攻击者提供探测信号）。
        // 注意：这一步**不再受 jeecg.firewall.enableLoginCaptcha 开关影响**，
        // 店铺端发信接口必须在任何环境下都校验，否则一个配置漏改就会变成公开的"免费发信机"。
        if (!captchaTicketService.consume(dto.getTicket(), request)) {
            throw new JeecgBootException("请先完成安全验证");
        }

        // 场景下的邮箱存在性检查（契约明确要求保留清晰文案，枚举风险由限流承担）
        ShopAccountUser existing = findByEmail(email);
        if (ShopAccountConstant.SCENE_REGISTER.equals(scene)) {
            if (existing != null) {
                throw new JeecgBootException("该邮箱已注册，请直接登录");
            }
        } else if (existing == null) {
            // login / reset：未注册就发码毫无意义，用户拿着码也进不来
            throw new JeecgBootException("该邮箱尚未注册");
        }

        return emailCodeService.sendCode(email, scene, clientIp);
    }

    // ==================================================================
    // 2.2 注册
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResultVO register(RegisterDTO dto, HttpServletRequest request) {
        if (dto == null) {
            throw new JeecgBootException("请求参数不能为空");
        }
        String email = ShopEmailCodeService.normalizeEmail(dto.getEmail());
        if (StringUtils.isBlank(email)) {
            throw new JeecgBootException("请填写邮箱");
        }
        if (!accountSupport.isValidEmail(email)) {
            throw new JeecgBootException("邮箱格式不正确");
        }
        String password = resolvePassword(dto.getPassword());
        validatePasswordStrength(password);
        if (!password.equals(resolvePassword(dto.getConfirmPassword()))) {
            throw new JeecgBootException("两次输入的密码不一致");
        }

        String clientIp = accountSupport.resolveClientIp(request);

        // IP 维度限流：注册会写库 + 发 token，属于低成本可被批量利用的接口
        rateLimiter.check("register:ip:min:" + clientIp,
                ShopAccountConstant.RL_REGISTER_PER_MINUTE, 60L,
                "操作过于频繁，请稍后再试");
        rateLimiter.check("register:ip:hour:" + clientIp,
                ShopAccountConstant.RL_REGISTER_PER_HOUR, 3600L,
                "当前网络注册次数过多，请稍后再试");

        // 先校验并消费验证码：验证码不过就不该消耗用户名配额，也不该留下任何数据
        emailCodeService.consumeCode(email, ShopAccountConstant.SCENE_REGISTER, dto.getEmailCode());

        // 邮箱唯一性前置检查。唯一索引兜底，但并发撞索引会变成 500，
        // 前置检查让绝大多数情况返回友好文案。
        if (shopAccountMapper.selectIdByEmailAny(email) != null) {
            throw new JeecgBootException("该邮箱已注册，请直接登录");
        }

        ShopStore store = resolveStoreQuietly(request);
        ShopAccountUser user = createCustomer(email, password, null, null, store, true);

        loginLogRecorder.record(user.getId(), user.getUsername(), email,
                ShopAccountConstant.LOGIN_TYPE_PASSWORD, store,
                clientIp, accountSupport.resolveUserAgent(request), true, null);

        String token = issueToken(user);
        return new LoginResultVO()
                .setToken(token)
                .setUserInfo(toLoginUserVO(user, true))
                .setStoreCode(store == null ? null : store.getCode());
    }

    // ==================================================================
    // 2.3 邮箱 + 密码登录
    // ==================================================================

    @Override
    public LoginResultVO loginByPassword(LoginPasswordDTO dto, HttpServletRequest request) {
        if (dto == null) {
            throw new JeecgBootException("请求参数不能为空");
        }
        String email = ShopEmailCodeService.normalizeEmail(dto.getEmail());
        if (StringUtils.isBlank(email)) {
            throw new JeecgBootException("请填写邮箱");
        }
        // 判空在这里做，不指望 resolvePassword 兜底（它对空输入原样返回空）
        String password = resolvePassword(dto.getPassword());
        if (StringUtils.isBlank(password)) {
            throw new JeecgBootException("请填写密码");
        }

        ShopStore store = resolveStoreQuietly(request);
        String clientIp = accountSupport.resolveClientIp(request);
        String userAgent = accountSupport.resolveUserAgent(request);

        // IP 维度限流：账号级失败锁定（下面 step.1）挡不住"换邮箱撞库"，
        // 同一个 IP 对大量邮箱各试 1 次时只有 IP 限流能拦住。
        rateLimiter.check("login-pwd:ip:min:" + clientIp,
                ShopAccountConstant.RL_PASSWORD_LOGIN_PER_MINUTE, 60L,
                "登录尝试过于频繁，请稍后再试");
        rateLimiter.check("login-pwd:ip:hour:" + clientIp,
                ShopAccountConstant.RL_PASSWORD_LOGIN_PER_HOUR, 3600L,
                "当前网络登录尝试次数过多，请稍后再试");

        // step.1 失败次数（key 用 email，与 LoginController 同一套机制与阈值）
        if (isLoginFailOvertimes(email)) {
            loginLogRecorder.record(null, null, email, ShopAccountConstant.LOGIN_TYPE_PASSWORD,
                    store, clientIp, userAgent, false, "登录失败次数过多，账号已临时锁定");
            throw new JeecgBootException("该用户登录失败次数过多，请于10分钟后再次登录！");
        }

        // step.2 用户存在且有效
        ShopAccountUser sysUser = findByEmail(email);
        if (sysUser == null) {
            addLoginFailOvertimes(email);
            loginLogRecorder.record(null, null, email, ShopAccountConstant.LOGIN_TYPE_PASSWORD,
                    store, clientIp, userAgent, false, "邮箱未注册");
            // 与"密码错误"文案区分：契约 2.1 已明确"可用性优先，保留明确文案"，
            // 枚举风险由 60s 冷却 + 每日上限 + 失败锁定共同承担
            throw new JeecgBootException("该邮箱尚未注册，请先注册");
        }
        checkUserEffective(sysUser, email, store, clientIp, userAgent);

        // step.3 店铺端专属：必须是 shop_customer
        requireShopCustomer(sysUser, email, store, clientIp, userAgent);

        // step.4 密码校验
        // pwd_set=0 表示密码是系统随机占位、无人知晓明文 —— 这种账号只能走
        // 验证码/Google 登录，密码登录**必然**失败。提前给出可操作提示，
        // 而不是让用户对着一个永远不对的密码反复试（试满 5 次还会被锁 10 分钟）。
        if (!hasRealPassword(sysUser)) {
            loginLogRecorder.record(sysUser.getId(), sysUser.getUsername(), email,
                    ShopAccountConstant.LOGIN_TYPE_PASSWORD, store, clientIp, userAgent,
                    false, "账号未设置密码");
            throw new JeecgBootException("该账号还没有设置密码，请使用邮箱验证码或 Google 登录");
        }

        String encryptedInput = PasswordUtil.encrypt(sysUser.getUsername(), password, sysUser.getSalt());
        if (!StringUtils.equals(encryptedInput, sysUser.getPassword())) {
            addLoginFailOvertimes(email);
            loginLogRecorder.record(sysUser.getId(), sysUser.getUsername(), email,
                    ShopAccountConstant.LOGIN_TYPE_PASSWORD, store, clientIp, userAgent,
                    false, "密码错误");
            throw new JeecgBootException("邮箱或密码错误");
        }

        // step.5 成功：清失败计数
        redisUtil.del(CommonConstant.LOGIN_FAIL + email);

        String token = issueToken(sysUser);
        loginLogRecorder.record(sysUser.getId(), sysUser.getUsername(), email,
                ShopAccountConstant.LOGIN_TYPE_PASSWORD, store, clientIp, userAgent, true, null);

        return new LoginResultVO()
                .setToken(token)
                .setUserInfo(toLoginUserVO(sysUser, true));
    }

    // ==================================================================
    // 2.4 邮箱 + 邮箱验证码登录（未注册自动建号）
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResultVO loginByEmailCode(LoginEmailCodeDTO dto, HttpServletRequest request) {
        if (dto == null) {
            throw new JeecgBootException("请求参数不能为空");
        }
        String email = ShopEmailCodeService.normalizeEmail(dto.getEmail());
        if (StringUtils.isBlank(email)) {
            throw new JeecgBootException("请填写邮箱");
        }
        if (!accountSupport.isValidEmail(email)) {
            throw new JeecgBootException("邮箱格式不正确");
        }

        ShopStore store = resolveStoreQuietly(request);
        String clientIp = accountSupport.resolveClientIp(request);
        String userAgent = accountSupport.resolveUserAgent(request);

        // IP 维度限流：邮箱验证码登录 + 自动注册，防止脚本批量建号
        rateLimiter.check("login-code:ip:min:" + clientIp,
                ShopAccountConstant.RL_LOGIN_CODE_PER_MINUTE, 60L,
                "操作过于频繁，请稍后再试");

        // 先消费验证码：验证码不对就不该建号（否则一个打错的验证码会留下垃圾账号）
        emailCodeService.consumeCode(email, ShopAccountConstant.SCENE_LOGIN, dto.getEmailCode());

        ShopAccountUser sysUser = findByEmail(email);
        if (sysUser == null) {
            // 新用户自动注册并登录（契约 2.4）。密码为系统随机占位，pwd_set=0。
            sysUser = createCustomer(email, null, null, null, store, false);
            log.info("邮箱验证码登录自动注册新用户，email={}，username={}", email, sysUser.getUsername());
        } else {
            checkUserEffective(sysUser, email, store, clientIp, userAgent);
            requireShopCustomer(sysUser, email, store, clientIp, userAgent);
        }

        String token = issueToken(sysUser);
        loginLogRecorder.record(sysUser.getId(), sysUser.getUsername(), email,
                ShopAccountConstant.LOGIN_TYPE_EMAIL_CODE, store, clientIp, userAgent, true, null);

        return new LoginResultVO()
                .setToken(token)
                .setUserInfo(toLoginUserVO(sysUser, hasRealPassword(sysUser)));
    }

    // ==================================================================
    // 2.5 Google 登录
    // ==================================================================

    @Override
    public GoogleAuthUrlVO buildGoogleAuthUrl(String redirectUri) {
        ShopGoogleOAuthService.GoogleAuthResult result = googleOAuthService.buildAuthUrl(redirectUri);
        return new GoogleAuthUrlVO()
                .setAuthUrl(result.getAuthUrl())
                .setState(result.getState());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResultVO loginByGoogle(GoogleCallbackDTO dto, HttpServletRequest request) {
        if (dto == null) {
            throw new JeecgBootException("请求参数不能为空");
        }
        ShopStore store = resolveStoreQuietly(request);
        String clientIp = accountSupport.resolveClientIp(request);
        String userAgent = accountSupport.resolveUserAgent(request);

        String email = null;
        boolean isNewUser = false;
        try {
            // state 一次性消费，返回的是申请 auth-url 时登记的 redirectUri
            String registeredRedirectUri = googleOAuthService.consumeState(dto.getState());
            // redirectUri 优先用请求带的值（BFF 按站点 origin 拼的），
            // 缺失时回退到 state 里登记的那个 —— 两者不一致时 Google 会拒绝，
            // 这是 Google 自己的安全设计，不需要我们额外判断
            String redirectUri = StringUtils.defaultIfBlank(dto.getRedirectUri(), registeredRedirectUri);

            // ① 一次请求拿到 token + id_token：id_token 里已经带了 sub/email/name/picture，
            //    直接解析可以省掉 userinfo 这第二次跨境请求（墙内每多一次请求就多一次超时风险）。
            //    只有 id_token 不可用时才回退 userinfo，保证兼容性与兜底能力。
            ShopGoogleOAuthService.TokenResult tokenResult =
                    googleOAuthService.exchangeCode(dto.getCode(), redirectUri);
            ShopGoogleOAuthService.GoogleUserInfo googleUser =
                    googleOAuthService.parseIdToken(tokenResult.getIdToken());
            if (googleUser == null) {
                log.info("Google id_token 不可用，回退调用 userinfo 获取用户信息");
                googleUser = googleOAuthService.fetchUserInfo(tokenResult.getAccessToken());
            }

            String googleSub = googleUser.getSub();
            email = googleUser.getEmail();

            ShopAccountUser sysUser;
            String userIdBySub = shopAccountMapper.selectIdByGoogleSub(googleSub);
            if (StringUtils.isNotBlank(userIdBySub)) {
                // ① 按 google_sub 命中 → 直接登录
                sysUser = shopAccountMapper.selectByIdActive(userIdBySub);
                if (sysUser == null) {
                    // google_sub 指向一个已不存在的用户：数据不一致，报错而不是静默重建
                    throw new JeecgBootException("Google 账号绑定的用户不存在，请联系管理员");
                }
                checkUserEffective(sysUser, email, store, clientIp, userAgent);
                requireShopCustomer(sysUser, email, store, clientIp, userAgent);
            } else if (StringUtils.isNotBlank(email) && (sysUser = findByEmail(email)) != null) {
                // ② 未命中 sub，但邮箱已存在 → 绑定 google_sub，不重复建号
                checkUserEffective(sysUser, email, store, clientIp, userAgent);
                requireShopCustomer(sysUser, email, store, clientIp, userAgent);
                shopAccountMapper.updateGoogleSub(sysUser.getId(), googleSub);
                // 同步内存态，避免后续逻辑用到旧值
                sysUser.setGoogleSub(googleSub);
                log.info("Google 账号绑定到既有用户，userId={}，email={}", sysUser.getId(), email);
            } else {
                // ③ 全新用户 → 自动注册（pwd_set=0，密码为随机占位）
                sysUser = createCustomer(email, null, googleSub, googleUser.getPicture(), store, false);
                isNewUser = true;
                log.info("Google 登录自动注册新用户，email={}，username={}", email, sysUser.getUsername());
            }

            String token = issueToken(sysUser);
            loginLogRecorder.record(sysUser.getId(), sysUser.getUsername(), email,
                    ShopAccountConstant.LOGIN_TYPE_GOOGLE, store, clientIp, userAgent, true, null);

            return new LoginResultVO()
                    .setToken(token)
                    .setUserInfo(toLoginUserVO(sysUser, hasRealPassword(sysUser)))
                    .setIsNewUser(isNewUser);

        } catch (JeecgBootException e) {
            // 失败也记流水：Google 回调失败是最难排查的一类问题
            // （state 过期？code 已被用过？网络不通？），没有流水只能靠用户口述
            loginLogRecorder.record(null, null, email, ShopAccountConstant.LOGIN_TYPE_GOOGLE,
                    store, clientIp, userAgent, false, e.getMessage());
            throw e;
        }
    }

    // ==================================================================
    // 2.6 忘记密码
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PasswordOpVO resetPassword(ResetPasswordDTO dto, HttpServletRequest request) {
        if (dto == null) {
            throw new JeecgBootException("请求参数不能为空");
        }
        String email = ShopEmailCodeService.normalizeEmail(dto.getEmail());
        if (StringUtils.isBlank(email)) {
            throw new JeecgBootException("请填写邮箱");
        }
        String newPassword = resolvePassword(dto.getNewPassword());
        validatePasswordStrength(newPassword);
        if (!newPassword.equals(resolvePassword(dto.getConfirmPassword()))) {
            throw new JeecgBootException("两次输入的密码不一致");
        }

        // IP 维度限流：重置密码成功后会让旧 token 失效，属于高价值攻击目标
        String clientIp = accountSupport.resolveClientIp(request);
        rateLimiter.check("reset:ip:min:" + clientIp,
                ShopAccountConstant.RL_RESET_PER_MINUTE, 60L,
                "操作过于频繁，请稍后再试");

        emailCodeService.consumeCode(email, ShopAccountConstant.SCENE_RESET, dto.getEmailCode());
        ShopAccountUser sysUser = findByEmail(email);
        if (sysUser == null) {
            // 走到这里说明验证码是对的（否则上面就抛了），但用户被删了 —— 属于并发删除
            throw new JeecgBootException("该邮箱尚未注册");
        }
        requireShopCustomerQuietly(sysUser);

        updateCredential(sysUser, newPassword);
        clearUserLoginState(sysUser);

        return new PasswordOpVO().setSuccess(true);
    }

    // ==================================================================
    // 2.7 个人中心：设置 / 修改密码
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PasswordOpVO changePassword(ChangePasswordDTO dto, String userId, HttpServletRequest request) {
        if (dto == null) {
            throw new JeecgBootException("请求参数不能为空");
        }
        if (StringUtils.isBlank(userId)) {
            throw new JeecgBootException("请先登录");
        }
        ShopAccountUser sysUser = shopAccountMapper.selectByIdActive(userId);
        if (sysUser == null) {
            throw new JeecgBootException("当前登录用户不存在，请重新登录");
        }

        String newPassword = resolvePassword(dto.getNewPassword());
        validatePasswordStrength(newPassword);
        if (!newPassword.equals(resolvePassword(dto.getConfirmPassword()))) {
            throw new JeecgBootException("两次输入的密码不一致");
        }

        if (hasRealPassword(sysUser)) {
            // 已设置过密码：必须校验旧密码。
            // 跳过这一步等于"拿到 token 就能改密码"，token 一旦泄漏（XSS、日志、共享电脑）
            // 攻击者就能永久接管账号 —— 受害者连"改回去"都做不到。
            String oldPassword = resolvePassword(dto.getOldPassword());
            if (StringUtils.isBlank(oldPassword)) {
                throw new JeecgBootException("请输入当前密码");
            }
            String encryptedOld = PasswordUtil.encrypt(sysUser.getUsername(), oldPassword, sysUser.getSalt());
            if (!StringUtils.equals(encryptedOld, sysUser.getPassword())) {
                throw new JeecgBootException("当前密码不正确");
            }
        } else if (StringUtils.isNotBlank(dto.getOldPassword())) {
            // 没设过密码却传了旧密码：不报错也不校验（前端可能统一渲染了输入框）。
            // 只记一条 debug，便于发现"前端把不该显示的表单显示了"。
            log.debug("用户[{}]尚未设置过密码，忽略请求中的 oldPassword", sysUser.getUsername());
        }

        updateCredential(sysUser, newPassword);
        clearUserLoginState(sysUser);

        return new PasswordOpVO().setSuccess(true);
    }

    // ==================================================================
    // 2.8 当前登录用户信息
    // ==================================================================

    @Override
    public ShopProfileVO profile(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new JeecgBootException("请先登录");
        }
        ShopAccountUser sysUser = shopAccountMapper.selectByIdActive(userId);
        if (sysUser == null) {
            throw new JeecgBootException("当前登录用户不存在，请重新登录");
        }
        return new ShopProfileVO()
                .setId(sysUser.getId())
                .setUsername(sysUser.getUsername())
                .setRealname(sysUser.getRealname())
                .setEmail(sysUser.getEmail())
                .setAvatar(sysUser.getAvatar())
                .setPhone(sysUser.getPhone())
                .setHasPassword(hasRealPassword(sysUser))
                .setRegisterStoreCode(sysUser.getRegisterStoreCode())
                .setCreateTime(formatDate(sysUser.getCreateTime()));
    }

    // ==================================================================
    // 2.9 个人中心：保存资料 / 换头像
    // ==================================================================

    /**
     * 保存个人资料（昵称 + 手机号）。
     *
     * <p><b>为什么这个接口是"真写库"而不是只改前端状态</b>：旧版个人中心的"保存修改"
     * 只把内容塞进 localStorage 里那份用于显示的缓存，服务器完全不知道 ——
     * 换设备/清缓存就变回原值，用户会认为"保存没生效"。既然要做个人中心，
     * 就必须有一个真正落库的入口。</p>
     *
     * <p>手机号在 {@code sys_user} 上是唯一索引：撞索引时转成明确的业务提示，
     * 不能把 {@code DuplicateKeyException} 抛到全局异常处理器变成一句"系统异常"。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopProfileVO updateProfile(UpdateProfileDTO dto, String userId) {
        ShopAccountUser sysUser = requireActiveShopCustomer(userId);
        if (dto == null) {
            throw new JeecgBootException("请求参数不能为空");
        }
        String realname = normalizeRealname(dto.getRealname());
        String phone = normalizePhone(dto.getPhone());
        try {
            int updated = shopAccountMapper.updateProfile(sysUser.getId(), realname, phone);
            if (updated <= 0) {
                throw new JeecgBootException("资料保存失败，请稍后重试");
            }
        } catch (DuplicateKeyException e) {
            // uniq_sys_user_phone
            throw new JeecgBootException("该手机号已被其他账号使用");
        }
        log.info("店铺端资料已更新，userId={}，realname={}，phone已设置={}",
                sysUser.getId(), realname, phone != null);
        return profile(sysUser.getId());
    }

    /**
     * 选择预设头像 / 恢复默认头像。
     *
     * <p>只接受白名单里的站内路径或空串：</p>
     * <ul>
     *   <li>空串 = 恢复默认（库里写 NULL，前端回落到昵称首字母占位）；</li>
     *   <li>命中 {@link ShopAccountConstant#PRESET_AVATARS} 才允许 —— 这条路走不通任意 URL，</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopProfileVO updateAvatar(UpdateAvatarDTO dto, String userId) {
        ShopAccountUser sysUser = requireActiveShopCustomer(userId);
        String avatar = dto == null ? "" : StringUtils.trimToEmpty(dto.getAvatar());
        if (StringUtils.isNotEmpty(avatar) && !ShopAccountConstant.PRESET_AVATARS.contains(avatar)) {
            throw new JeecgBootException("头像来源不合法，请重新选择");
        }
        int updated = shopAccountMapper.updateAvatar(sysUser.getId(),
                StringUtils.isEmpty(avatar) ? null : avatar);
        if (updated <= 0) {
            throw new JeecgBootException("头像保存失败，请稍后重试");
        }
        // 从"自己上传的图"切到预设头像 / 恢复默认时，把那张上传的图一并清掉，
        // 否则 OSS 里会不断留下没人引用的孤儿对象（用户每次换一次就漏一张）。
        deleteOldUploadedAvatar(sysUser.getAvatar());
        log.info("店铺端头像已更新（{}），userId={}",
                StringUtils.isEmpty(avatar) ? "恢复默认" : avatar, sysUser.getId());
        return profile(sysUser.getId());
    }

    /**
     * 上传自定义头像：类型/体积校验 → 落 OSS → 写库。
     *
     * <p>三重校验的理由：前端（BFF）已经用 sharp 裁成 512×512 webp，
     * 但接口是公开可调的，不能假设调用方一定是我们的前端 ——
     * 这里按**文件头魔数**判断真实类型（不信 Content-Type），并硬性限制 2MB。</p>
     *
     * <p>URL 由服务端生成后直接写库，用户拿不到"自己指定头像地址"的能力。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopProfileVO uploadAvatar(MultipartFile file, String userId) {
        ShopAccountUser sysUser = requireActiveShopCustomer(userId);
        if (file == null || file.isEmpty()) {
            throw new JeecgBootException("请选择要上传的头像图片");
        }
        if (file.getSize() > ShopAccountConstant.AVATAR_MAX_BYTES) {
            throw new JeecgBootException("图片体积过大，请控制在 2MB 以内");
        }

        byte[] data;
        try (InputStream in = file.getInputStream()) {
            data = in.readNBytes((int) ShopAccountConstant.AVATAR_MAX_BYTES + 1);
        } catch (IOException e) {
            log.warn("头像文件读取失败，userId={}", sysUser.getId(), e);
            throw new JeecgBootException("图片读取失败，请重试");
        }
        if (data.length > ShopAccountConstant.AVATAR_MAX_BYTES) {
            throw new JeecgBootException("图片体积过大，请控制在 2MB 以内");
        }

        String ext = detectImageExtension(data);
        if (ext == null) {
            throw new JeecgBootException("只支持 JPG / PNG / WEBP 格式的图片");
        }

        String key = buildAvatarObjectKey(sysUser.getId(), ext);
        URL url;
        try {
            url = ossServiceContext.putObject(key, data);
        } catch (Exception e) {
            log.error("头像上传 OSS 失败，userId={}，key={}", sysUser.getId(), key, e);
            throw new JeecgBootException("头像上传失败，请稍后重试");
        }
        if (url == null || StringUtils.isBlank(url.toString())) {
            throw new JeecgBootException("头像上传失败，请稍后重试");
        }
        String avatar = url.toString();
        // sys_user.avatar 是 varchar(255)：超长会被 MySQL 截断/报错，入 before 先拦
        if (avatar.length() > 255) {
            log.error("头像 URL 过长，拒绝写库，userId={}，url={}", sysUser.getId(), avatar);
            throw new JeecgBootException("头像上传失败，请稍后重试");
        }

        int updated = shopAccountMapper.updateAvatar(sysUser.getId(), avatar);
        if (updated <= 0) {
            throw new JeecgBootException("头像保存失败，请稍后重试");
        }

        // 换新头像后清理旧的上传文件（best-effort：失败只记日志，不影响本次保存）
        deleteOldUploadedAvatar(sysUser.getAvatar());
        log.info("店铺端自定义头像已更新，userId={}，url={}", sysUser.getId(), avatar);
        return profile(sysUser.getId());
    }

    /**
     * 写操作统一的登录态校验：账号必须存在、未注销、未冻结，且具备 shop_customer 角色。
     *
     * <p>比只校验登录态更严一格：{@code sys_user} 是管理端共用的表，
     * 店铺端的写接口不该成为"管理端账号也能改自己资料"的旁路。</p>
     */
    private ShopAccountUser requireActiveShopCustomer(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new JeecgBootException("请先登录");
        }
        ShopAccountUser sysUser = shopAccountMapper.selectByIdActive(userId);
        if (sysUser == null) {
            throw new JeecgBootException("当前登录用户不存在，请重新登录");
        }
        if (CommonConstant.USER_FREEZE.equals(sysUser.getStatus())) {
            throw new JeecgBootException("该用户已冻结，请联系客服");
        }
        if (shopAccountMapper.countUserRoleByRoleCode(sysUser.getId(),
                ShopAccountConstant.ROLE_SHOP_CUSTOMER) == 0) {
            log.warn("非店铺端账号尝试修改店铺端资料，userId={}，roles={}",
                    sysUser.getId(), shopAccountMapper.selectRoleCodesByUserId(sysUser.getId()));
            throw new JeecgBootException("该账号不是店铺端账号，无法在此修改资料");
        }
        return sysUser;
    }

    /** 昵称：去空白、去控制字符、限制长度；不允许为空（空昵称会让资料卡显示成"用户"） */
    private String normalizeRealname(String raw) {
        if (raw == null) {
            throw new JeecgBootException("请填写昵称");
        }
        // 去掉控制字符与尖括号：前者会破坏排版，后者是 HTML 注入的常见载荷
        String value = raw.replaceAll("[\\p{Cntrl}]", "")
                .replace("<", "")
                .replace(">", "")
                .trim()
                .replaceAll("\\s{2,}", " ");
        if (value.isEmpty()) {
            throw new JeecgBootException("请填写昵称");
        }
        if (value.codePointCount(0, value.length()) > ShopAccountConstant.REALNAME_MAX_LENGTH) {
            throw new JeecgBootException("昵称最多 " + ShopAccountConstant.REALNAME_MAX_LENGTH + " 个字符");
        }
        return value;
    }

    /**
     * 手机号：空 -> null（清空）；否则只保留数字与开头的 +，长度 6-20。
     *
     * <p>刻意**不做**"必须是中国大陆 11 位手机号"的校验：本项目是多语言站点，
     * 买家可能是海外号码；用 E.164 的宽松形状校验比强行套 +86 规则更合适。</p>
     */
    private String normalizePhone(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        // 允许用户输入带空格/短横线/括号的号码，入库前统一去掉分隔符
        value = value.replaceAll("[\\s\\-()]", "");
        if (value.length() > ShopAccountConstant.PHONE_MAX_LENGTH) {
            throw new JeecgBootException("手机号长度不能超过 " + ShopAccountConstant.PHONE_MAX_LENGTH + " 位");
        }
        if (!value.matches("^\\+?[0-9]{6,20}$")) {
            throw new JeecgBootException("手机号格式不正确");
        }
        return value;
    }

    /**
     * 按文件头魔数识别图片类型（**不看** Content-Type 与扩展名，两者都可伪造）。
     *
     * @return 扩展名（png / jpg / webp）；无法识别返回 null
     */
    private String detectImageExtension(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
            return "png";
        }
        // JPEG: FF D8 FF
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        // WEBP: "RIFF" .... "WEBP"
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "webp";
        }
        return null;
    }

    /**
     * 生成 OSS 对象 key：{@code <dir-prefix>/shop/avatar/<userId>-<时间戳>.<ext>}。
     *
     * <p>文件名带 userId 与时间戳：</p>
     * <ul>
     *   <li>userId 让运维能一眼定位"这张图属于谁"；</li>
     *   <li>时间戳保证同名上传不会互相覆盖（否则两个人都传 avatar.webp 就串了）；</li>
     *   <li>**不含用户可控的原始文件名**，避免路径穿越（{@code ../}）。</li>
     * </ul>
     */
    private String buildAvatarObjectKey(String userId, String ext) {
        String prefix = StringUtils.trimToEmpty(ossDirPrefix);
        StringBuilder key = new StringBuilder();
        if (!prefix.isEmpty()) {
            key.append(prefix).append('/');
        }
        key.append(ShopAccountConstant.AVATAR_OBJECT_DIR).append('/')
                .append(userId).append('-').append(System.currentTimeMillis())
                .append('.').append(ext);
        return key.toString();
    }

    /**
     * 删除被替换掉的上传头像（best-effort）。
     *
     * <p>只在"旧值确实指向本系统头像目录"时才删：
     * Google 头像（{@code lh3.googleusercontent.com}）与预设头像（{@code /avatars/...}）
     * 都不能删，前者是外站资源、后者是仓库里的静态文件。</p>
     */
    private void deleteOldUploadedAvatar(String oldAvatar) {
        if (StringUtils.isBlank(oldAvatar) || !oldAvatar.startsWith("http")) {
            return;
        }
        try {
            String path = new URL(oldAvatar).getPath();
            String prefix = StringUtils.trimToEmpty(ossDirPrefix);
            String expect = (prefix.isEmpty() ? "" : prefix + "/") + ShopAccountConstant.AVATAR_OBJECT_DIR + "/";
            String objectKey = path.startsWith("/") ? path.substring(1) : path;
            if (!objectKey.startsWith(expect)) {
                return;
            }
            ossServiceContext.deleteObject(objectKey);
        } catch (Exception e) {
            log.warn("删除旧头像失败（忽略），url={}", oldAvatar, e);
        }
    }

    /**
     * 滑动续期：用当前登录人重新签发 token（清单 S25）。
     *
     * <p>为什么需要：JWT 固定 7 天过期，浏览器 cookie 也是 7 天，
     * 用户在第 7 天会被"突然"登出。BFF 在 token 剩余不足 1 天时调这里换一张新的，
     * 只要用户在活跃期内访问过，登录态就会一直续下去。
     *
     * <p>安全边界：只认 Shiro 已认证的 userId（Controller 传入），不接受请求体；
     * 账号被注销/冻结时拒绝，避免"冻结后靠续期继续用"。
     */
    @Override
    public String refreshToken(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new JeecgBootException("请先登录");
        }
        ShopAccountUser sysUser = shopAccountMapper.selectByIdActive(userId);
        if (sysUser == null) {
            throw new JeecgBootException("当前登录用户不存在，请重新登录");
        }
        if (CommonConstant.USER_FREEZE.equals(sysUser.getStatus())) {
            throw new JeecgBootException("该用户已冻结，请联系客服");
        }
        return issueToken(sysUser);
    }

    // ==================================================================
    // 用户创建
    // ==================================================================

    /**
     * 创建店铺端买家账号。
     *
     * @param email     邮箱（已规范化，可为 null —— Google 未返回 email 时）
     * @param password  明文密码；为 null/空 表示自动注册（写随机占位密码）
     * @param googleSub Google sub，可空
     * @param avatar    头像 URL，可空（Google 自动注册时存 Google 头像）
     * @param store     注册来源店铺，可空（解析失败时不阻断注册）
     * @param pwdSet    true 表示用户自己设过密码
     */
    private ShopAccountUser createCustomer(String email, String password, String googleSub,
                                           String avatar, ShopStore store, boolean pwdSet) {
        String username = generateUniqueUsername();
        String salt = oConvertUtils.randomGen(ShopAccountConstant.SALT_LENGTH);

        // 自动注册也必须写一个非空密码哈希：JwtUtil.sign 把它纳入签名，
        // 为空签不出 token。明文取 32 位随机串并**立刻丢弃**——
        // 谁也不知道它，等于这个账号没有可用的密码。
        String rawPassword = StringUtils.isNotBlank(password) ? password : accountSupport.randomLowerAlnum(
                ShopAccountConstant.PLACEHOLDER_PASSWORD_LENGTH);
        String encrypted = PasswordUtil.encrypt(username, rawPassword, salt);

        Date now = new Date();
        ShopAccountUser user = new ShopAccountUser()
                .setId(UUIDGenerator.generate())
                .setUsername(username)
                .setRealname(accountSupport.defaultRealname(email))
                .setEmail(email)
                .setPassword(encrypted)
                .setSalt(salt)
                .setAvatar(avatar)
                // status=1 正常；del_flag=0 未删除（与 LoginController 的口径一致）
                .setStatus(CommonConstant.STATUS_1_INT)
                .setDelFlag(0)
                // org_code 留空：买家不属于任何店铺部门（契约决策 3）
                .setOrgCode(null)
                .setCreateTime(now)
                .setUpdateTime(now)
                .setLastPwdUpdateTime(pwdSet ? now : null)
                .setGoogleSub(googleSub)
                .setPwdSet(pwdSet ? ShopAccountConstant.PWD_SET_YES : ShopAccountConstant.PWD_SET_NO);
        if (store != null) {
            user.setRegisterStoreCode(store.getCode()).setRegisterStoreId(store.getId());
        }

        shopAccountMapper.insertCustomer(user);
        bindCustomerRole(user.getId());

        log.info("店铺端买家账号创建成功，userId={}，username={}，email={}，pwdSet={}，store={}",
                user.getId(), username, email, pwdSet, store == null ? null : store.getCode());
        return user;
    }

    /**
     * 绑定 {@code shop_customer} 角色。
     *
     * <p>找不到角色时**抛异常**而不是静默跳过：没有这个角色的账号
     * 在店铺端会被 {@link #requireShopCustomer} 一律拒登，
     * 用户会看到"注册成功但登录失败"这种最让人困惑的现象。
     * 宁可注册直接失败并提示"请先执行数据库迁移"。
     */
    private void bindCustomerRole(String userId) {
        String roleId = shopAccountMapper.selectRoleIdByRoleCode(ShopAccountConstant.ROLE_SHOP_CUSTOMER);
        if (StringUtils.isBlank(roleId)) {
            throw new JeecgBootException("系统缺少前台买家角色（shop_customer），请先执行数据库迁移脚本 V3.9.5_12");
        }
        // 幂等：同一用户重复绑同一角色会让权限计算出现重复项
        if (shopAccountMapper.countUserRoleByRoleCode(userId, ShopAccountConstant.ROLE_SHOP_CUSTOMER) > 0) {
            return;
        }
        shopAccountMapper.insertUserRole(UUIDGenerator.generate(), userId, roleId);
    }

    /**
     * 生成全局唯一的 username：{@code u_} + 8 位小写字母数字。
     *
     * <p>冲突则重试（最多 10 次）。用"先查再插"而不是"插入失败再重试"：
     * 后者会因唯一索引冲突把事务标记为 rollback-only，
     * 在 Spring 的 {@code @Transactional} 下即使 catch 住了也提交不了。
     * （真正的并发兜底仍靠 {@code uniq_sys_user_username} 索引。）
     */
    private String generateUniqueUsername() {
        for (int i = 0; i < ShopAccountConstant.USERNAME_MAX_RETRY; i++) {
            String candidate = ShopAccountConstant.USERNAME_PREFIX
                    + accountSupport.randomLowerAlnum(ShopAccountConstant.USERNAME_RANDOM_LENGTH);
            if (shopAccountMapper.selectIdByUsernameAny(candidate) == null) {
                return candidate;
            }
            log.warn("生成的 username 冲突，重试第 {} 次：{}", i + 1, candidate);
        }
        // 8 位小写字母数字有 36^8 ≈ 2.8e12 种组合，连撞 10 次说明系统出问题了
        throw new JeecgBootException("账号生成失败，请稍后重试");
    }

    /** 指定长度的小写字母 + 数字随机串 */

    /** 按邮箱查用户（仅未删除） */
    private ShopAccountUser findByEmail(String email) {
        return StringUtils.isBlank(email) ? null : shopAccountMapper.selectByEmail(email);
    }

    // ==================================================================
    // 校验
    // ==================================================================

    /**
     * 校验密码强度：8-32 位，至少含字母和数字。
     *
     * <p>刻意**不要求**特殊字符：本项目对 C 端买家开放，
     * 强制特殊符号会把大量用户挡在注册页；而"长度 + 字母数字混合"
     * 已能挡住绝大多数撞库字典。管理端的强密码策略由
     * {@code jeecg.firewall.enableStrongPwd} 单独控制，与此处无关。
     */
    private void validatePasswordStrength(String password) {
        if (StringUtils.isBlank(password)) {
            throw new JeecgBootException("请填写密码");
        }
        if (password.length() < ShopAccountConstant.PASSWORD_MIN_LENGTH
                || password.length() > ShopAccountConstant.PASSWORD_MAX_LENGTH) {
            throw new JeecgBootException("密码长度需为 "
                    + ShopAccountConstant.PASSWORD_MIN_LENGTH + "-" + ShopAccountConstant.PASSWORD_MAX_LENGTH + " 位");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new JeecgBootException("密码需同时包含字母和数字");
        }
    }

    /**
     * 邮箱格式校验。
     *
     * <p>刻意用**宽松**正则：严格按 RFC 5322 校验在实践中总会误伤合法地址
     * （+tag、多级域名、长 TLD），而真正的验证手段是"能不能收到验证码"。
     * 这里只挡明显不是邮箱的输入（没有 @、没有点）。
     */

    /**
     * 账号是否有效 —— {@code SysUserServiceImpl#checkUserIsEffective} 的等价实现。
     *
     * <p>那个方法在 jeecg-system-biz 里，ck-shop 编译期够不到（见类注释），
     * 因此按同样的语义自己实现三种拒绝情况：
     * <ol>
     *   <li>用户不存在；</li>
     *   <li>已注销（{@code del_flag = 1}）；</li>
     *   <li>已冻结（{@code status = 2}，{@code CommonConstant.USER_FREEZE}）。</li>
     * </ol>
     * 注意 {@code selectByEmail} 已经过滤了 {@code del_flag = 0}，
     * 情况 2 正常路径下不会触发；保留判断是为了"查询口径将来变化时不会静默放行"。
     */
    private void checkUserEffective(ShopAccountUser sysUser, String email, ShopStore store,
                                    String clientIp, String userAgent) {
        String reason = null;
        if (sysUser == null) {
            reason = "该用户不存在，请注册";
        } else if (CommonConstant.DEL_FLAG_1.equals(sysUser.getDelFlag())) {
            reason = "该用户已注销";
        } else if (CommonConstant.USER_FREEZE.equals(sysUser.getStatus())) {
            reason = "该用户已冻结";
        }
        if (reason != null) {
            loginLogRecorder.record(sysUser == null ? null : sysUser.getId(),
                    sysUser == null ? null : sysUser.getUsername(), email,
                    ShopAccountConstant.LOGIN_TYPE_PASSWORD, store, clientIp, userAgent, false, reason);
            throw new JeecgBootException(reason);
        }
    }

    /**
     * 必须是 {@code shop_customer} 才能走店铺端登录。
     *
     * <p>这是**关键的一道隔离**：sys_user 是管理端与店铺端共用的表，
     * 不加这道校验的话，管理端账号（比如 admin）可以调
     * {@code /shop/account/login/password} 拿到 token —— 而这个 token 在管理端是有效的，
     * 相当于开了一条从店前端接口获得管理端会话的通道。
     */
    private void requireShopCustomer(ShopAccountUser sysUser, String email, ShopStore store,
                                     String clientIp, String userAgent) {
        if (shopAccountMapper.countUserRoleByRoleCode(sysUser.getId(),
                ShopAccountConstant.ROLE_SHOP_CUSTOMER) > 0) {
            return;
        }
        // 日志里带上实际角色，便于回答"这个账号到底有什么角色"
        log.warn("非店铺端账号尝试登录店铺端，userId={}，email={}，roles={}",
                sysUser.getId(), email, shopAccountMapper.selectRoleCodesByUserId(sysUser.getId()));
        loginLogRecorder.record(sysUser.getId(), sysUser.getUsername(), email,
                ShopAccountConstant.LOGIN_TYPE_PASSWORD, store, clientIp, userAgent,
                false, "非店铺端账号（缺少 shop_customer 角色）");
        throw new JeecgBootException("该账号不是店铺端账号，无法在此登录");
    }

    /**
     * 重置密码路径上的角色校验：**不通过时只记日志、不阻断**。
     *
     * <p>理由：能走到这里说明请求者已通过邮箱验证码证明了邮箱所有权。
     * 管理端账号若在店铺端重置密码，改的也是同一个密码哈希，
     * 属于"用户自己的选择"，不该被挡住（挡了会变成"我的账号在两边都不好使"）。
     * 但仍记一条 warn，因为这是个不该频繁出现的路径。
     */
    private void requireShopCustomerQuietly(ShopAccountUser sysUser) {
        if (shopAccountMapper.countUserRoleByRoleCode(sysUser.getId(),
                ShopAccountConstant.ROLE_SHOP_CUSTOMER) == 0) {
            log.warn("非店铺端账号在店铺端重置密码，userId={}，username={}",
                    sysUser.getId(), sysUser.getUsername());
        }
    }

    // ==================================================================
    // 登录失败次数（与 LoginController 同一套机制，key 换成 email）
    // ==================================================================

    /** 失败次数 > 5 视为超限（与 LoginController#isLoginFailOvertimes 一致） */
    private boolean isLoginFailOvertimes(String email) {
        Object failTime = redisUtil.get(CommonConstant.LOGIN_FAIL + email);
        if (failTime != null) {
            try {
                return Integer.parseInt(failTime.toString()) > 5;
            } catch (NumberFormatException e) {
                // 脏数据：不作为锁定依据，但也不让它抛异常打断登录
                log.warn("登录失败计数非法，email={}，值={}", email, failTime);
            }
        }
        return false;
    }

    /** 失败次数 +1，10 分钟后过期（与 LoginController#addLoginFailOvertimes 一致） */
    private void addLoginFailOvertimes(String email) {
        String key = CommonConstant.LOGIN_FAIL + email;
        int val = 0;
        Object failTime = redisUtil.get(key);
        if (failTime != null) {
            try {
                val = Integer.parseInt(failTime.toString());
            } catch (NumberFormatException e) {
                log.warn("登录失败计数非法，重置为 0，email={}，值={}", email, failTime);
            }
        }
        redisUtil.set(key, ++val, 600);
    }

    // ==================================================================
    // token / 登录态
    // ==================================================================

    /**
     * 签发 token —— 契约 5.5.1 规定的最小集，**不**复用 LoginController#userInfo。
     *
     * <p>那个方法是 private，且会做设置登录租户、查部门、算 multi_depart、
     * 加载全量字典等一堆管理端专属动作（ck-shop 也够不到 jeecg-system-biz）。
     * 店铺端买家没有部门、不需要字典，只用最小集。
     *
     * <p>附带好处：{@code JwtUtil.sign} 把密码哈希纳入签名，
     * 因此重置密码后旧 token 的签名自然验不过 —— 这正是我们要的效果。
     */
    private String issueToken(ShopAccountUser sysUser) {
        String token = JwtUtil.sign(sysUser.getUsername(), sysUser.getPassword(), CommonConstant.CLIENT_TYPE_PC);
        redisUtil.set(CommonConstant.PREFIX_USER_TOKEN + token, token);
        // 与管理端同一口径：实际有效期是 JwtUtil.EXPIRE_TIME，Redis 里留双倍余额
        // （*2/1000 把毫秒换成秒），避免 Redis 先过期而 JWT 还有效导致"时好时坏"
        redisUtil.expire(CommonConstant.PREFIX_USER_TOKEN + token, JwtUtil.EXPIRE_TIME * 2 / 1000);
        return token;
    }

    /**
     * 清除某用户的全部登录态（改密 / 重置密码后调用）。
     *
     * <p>清理范围与 {@code LoginController#asyncClearLogoutCache} 对齐：
     * Shiro 授权缓存、用户名缓存、单点登录记录。
     *
     * <p>无法枚举"该用户当前所有 token"（Redis 里 token key 以 token 为后缀，
     * 没有 userId 索引），因此这里清的是**按 userId/username 能定位到的部分**。
     * 真正让旧 token 失效的是 JwtUtil 的签名（含密码哈希）——
     * 这一层才是硬保障，Redis 清理是纵深防御。
     */
    private void clearUserLoginState(ShopAccountUser sysUser) {
        try {
            redisUtil.del(CommonConstant.PREFIX_USER_SHIRO_CACHE + sysUser.getId());
            redisUtil.del(String.format("%s::%s",
                    org.jeecg.common.constant.CacheConstant.SYS_USERS_CACHE, sysUser.getUsername()));
            redisUtil.del(CommonConstant.PREFIX_USER_TOKEN_PC + sysUser.getUsername());
            redisUtil.del(CommonConstant.PREFIX_USER_TOKEN_APP + sysUser.getUsername());
            redisUtil.del(CommonConstant.PREFIX_USER_TOKEN_PHONE + sysUser.getUsername());
            // 登录失败计数也一并清掉：用户刚证明了邮箱所有权，不该还背着锁
            if (StringUtils.isNotBlank(sysUser.getEmail())) {
                redisUtil.del(CommonConstant.LOGIN_FAIL + sysUser.getEmail());
            }
        } catch (Exception e) {
            // 清理失败不能影响"密码已经改成功"这个事实 ——
            // 而且此时事务若回滚，用户会以为改密失败，实际是缓存清理报错，极为困惑
            log.warn("清除用户登录态时异常（密码已更新，不影响本次操作），userId={}，原因={}",
                    sysUser.getId(), e.getMessage());
        }
    }

    // ==================================================================
    // 凭据更新 / 判定
    // ==================================================================

    /**
     * 更新密码：重新生成 salt + 新哈希 + pwd_set=1 + last_pwd_update_time。
     *
     * <p>重新生成 salt 而不是复用旧的：salt 的作用是让相同明文产生不同哈希，
     * 长期复用同一个 salt 会让"这个用户改过几次密码"这类信息在库里留下痕迹。
     *
     * <p>走单条 SQL（{@code updateCredential}）保证原子：
     * 分两条写会出现"密码换了但 pwd_set 还是 0"的中间态。
     */
    private void updateCredential(ShopAccountUser sysUser, String newPassword) {
        String salt = oConvertUtils.randomGen(ShopAccountConstant.SALT_LENGTH);
        String encrypted = PasswordUtil.encrypt(sysUser.getUsername(), newPassword, salt);
        int updated = shopAccountMapper.updateCredential(sysUser.getId(), encrypted, salt,
                ShopAccountConstant.PWD_SET_YES);
        if (updated <= 0) {
            throw new JeecgBootException("密码更新失败，请稍后重试");
        }
        // 同步内存态：调用方若随后用这个对象签 token，必须拿到新哈希，
        // 否则签出来的 token 用的是旧哈希，立刻就是无效签名
        sysUser.setPassword(encrypted).setSalt(salt).setPwdSet(ShopAccountConstant.PWD_SET_YES);
    }

    /**
     * 用户是否**自己设置过**密码。
     *
     * <p>{@code pwd_set} 为 null（迁移脚本刚跑完的历史行、或该列为空）
     * 按 {@code true} 处理 —— 与列的 {@code DEFAULT 1} 语义一致：
     * 老用户都有真实密码，判成 false 会让他们在个人中心看到"设置密码"，
     * 然后绕过旧密码校验，属于安全回归。
     */
    private boolean hasRealPassword(ShopAccountUser sysUser) {
        return sysUser == null
                || !Integer.valueOf(ShopAccountConstant.PWD_SET_NO).equals(sysUser.getPwdSet());
    }

    // ==================================================================
    // 店铺 / 请求信息
    // ==================================================================

    /**
     * 解析店铺，**失败不阻断**注册与登录。
     *
     * <p>{@code StoreResolver.resolve} 在编码不认识、域名没登记、
     * 且不允许回退时会抛异常。对"商品列表"这类接口必须抛（否则串店），
     * 但**注册/登录**不该因为店铺解析失败就失败：
     * 用户此时还没有任何店铺归属数据，最多是"注册来源"记不上。
     * 硬要抛异常的话，一个前台站点漏配 STORE_CODE 就会让全站无法注册 ——
     * 代价远大于少一条归因数据。
     */
    private ShopStore resolveStoreQuietly(HttpServletRequest request) {
        try {
            return storeResolver.resolve(request);
        } catch (Exception e) {
            log.warn("注册/登录时解析店铺失败，注册来源将为空：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 取客户端 IP。
     *
     * <p>与 {@code OrderRequestContextResolver#resolveClientIp} 同口径：
     * 从代理链里取**最后一个**非空段，而不是 X-Forwarded-For 的第一段 ——
     * 第一段是客户端可伪造的，拿它做限流等于把限流开关交给攻击者。
     */


    // ==================================================================
    // VO 转换 / 小工具
    // ==================================================================

    private LoginUserVO toLoginUserVO(ShopAccountUser sysUser, boolean hasPassword) {
        return new LoginUserVO()
                .setId(sysUser.getId())
                .setUsername(sysUser.getUsername())
                .setRealname(sysUser.getRealname())
                .setEmail(sysUser.getEmail())
                .setAvatar(sysUser.getAvatar())
                .setHasPassword(hasPassword)
                .setCreateTime(sysUser.getCreateTime());
    }

    /**
     * 默认昵称：邮箱 @ 前缀。
     *
     * <p>截断到 100 字符：{@code sys_user.realname} 是 varchar(100)，
     * 超长邮箱前缀会在严格模式下报错（用户看到"注册失败"却不知道原因）。
     */

    private String formatDate(Date date) {
        return date == null ? null : new SimpleDateFormat(DATE_TIME_PATTERN).format(date);
    }

    /**
     * 前端加密传输：AES 密文解出明文；非密文原样返回（兼容 curl 联调）。
     *
     * <p>用 {@code AesEncryptUtil.resolvePassword} 而不是自己写解密：
     * 它是三段兼容入口（PKCS5 → NoPadding → 原样返回），容错最好。
     * ⚠️ 它对空输入原样返回空、不抛异常，所以调用方必须自己判空。
     */
    private String resolvePassword(String raw) {
        return AesEncryptUtil.resolvePassword(raw);
    }
}
