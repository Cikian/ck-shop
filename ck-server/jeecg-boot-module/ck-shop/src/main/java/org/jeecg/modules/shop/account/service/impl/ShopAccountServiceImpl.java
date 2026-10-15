package org.jeecg.modules.shop.account.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.util.JwtUtil;
import org.jeecg.common.util.Md5Util;
import org.jeecg.common.util.PasswordUtil;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.UUIDGenerator;
import org.jeecg.common.util.encryption.AesEncryptUtil;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.modules.shop.account.constant.ShopAccountConstant;
import org.jeecg.modules.shop.account.entity.ShopAccountUser;
import org.jeecg.modules.shop.account.entity.dto.ChangePasswordDTO;
import org.jeecg.modules.shop.account.entity.dto.GoogleCallbackDTO;
import org.jeecg.modules.shop.account.entity.dto.LoginEmailCodeDTO;
import org.jeecg.modules.shop.account.entity.dto.LoginPasswordDTO;
import org.jeecg.modules.shop.account.entity.dto.RegisterDTO;
import org.jeecg.modules.shop.account.entity.dto.ResetPasswordDTO;
import org.jeecg.modules.shop.account.entity.dto.SendEmailCodeDTO;
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
import org.jeecg.modules.shop.store.StoreResolver;
import org.jeecg.modules.shop.store.entity.ShopStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
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
     * 用于图形验证码校验（{@code enableLoginCaptcha} 开关 + {@code signatureSecret}）。
     * 直接构造注入而不是 {@code LoginController} 那样用字段 @Autowired：
     * 依赖一眼可见，也便于单测。
     */
    private final JeecgBaseConfig jeecgBaseConfig;

    public ShopAccountServiceImpl(ShopAccountMapper shopAccountMapper,
                                  ShopEmailCodeService emailCodeService,
                                  ShopGoogleOAuthService googleOAuthService,
                                  ShopLoginLogRecorder loginLogRecorder,
                                  StoreResolver storeResolver,
                                  RedisUtil redisUtil,
                                  JeecgBaseConfig jeecgBaseConfig) {
        this.shopAccountMapper = shopAccountMapper;
        this.emailCodeService = emailCodeService;
        this.googleOAuthService = googleOAuthService;
        this.loginLogRecorder = loginLogRecorder;
        this.storeResolver = storeResolver;
        this.redisUtil = redisUtil;
        this.jeecgBaseConfig = jeecgBaseConfig;
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
        if (!isValidEmail(email)) {
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

        // 图形验证码必须**先**校验：它是挡在发信能力前面的那道闸。
        // 如果先做邮箱存在性检查，攻击者能用响应差异枚举出平台注册过哪些邮箱，
        // 且完全不需要过验证码 —— 那就等于白送一个用户枚举接口。
        validateCaptcha(dto.getCaptcha(), dto.getCheckKey());

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

        return emailCodeService.sendCode(email, scene, resolveClientIp(request));
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
        if (!isValidEmail(email)) {
            throw new JeecgBootException("邮箱格式不正确");
        }
        String password = resolvePassword(dto.getPassword());
        validatePasswordStrength(password);
        if (!password.equals(resolvePassword(dto.getConfirmPassword()))) {
            throw new JeecgBootException("两次输入的密码不一致");
        }

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
                resolveClientIp(request), resolveUserAgent(request), true, null);

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
        String clientIp = resolveClientIp(request);
        String userAgent = resolveUserAgent(request);

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
        if (!isValidEmail(email)) {
            throw new JeecgBootException("邮箱格式不正确");
        }

        ShopStore store = resolveStoreQuietly(request);
        String clientIp = resolveClientIp(request);
        String userAgent = resolveUserAgent(request);

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
        String clientIp = resolveClientIp(request);
        String userAgent = resolveUserAgent(request);

        String email = null;
        boolean isNewUser = false;
        try {
            // state 一次性消费，返回的是申请 auth-url 时登记的 redirectUri
            String registeredRedirectUri = googleOAuthService.consumeState(dto.getState());
            // redirectUri 优先用请求带的值（BFF 按站点 origin 拼的），
            // 缺失时回退到 state 里登记的那个 —— 两者不一致时 Google 会拒绝，
            // 这是 Google 自己的安全设计，不需要我们额外判断
            String redirectUri = StringUtils.defaultIfBlank(dto.getRedirectUri(), registeredRedirectUri);

            String accessToken = googleOAuthService.exchangeAccessToken(dto.getCode(), redirectUri);
            ShopGoogleOAuthService.GoogleUserInfo googleUser = googleOAuthService.fetchUserInfo(accessToken);

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
        String rawPassword = StringUtils.isNotBlank(password) ? password : randomLowerAlnum(
                ShopAccountConstant.PLACEHOLDER_PASSWORD_LENGTH);
        String encrypted = PasswordUtil.encrypt(username, rawPassword, salt);

        Date now = new Date();
        ShopAccountUser user = new ShopAccountUser()
                .setId(UUIDGenerator.generate())
                .setUsername(username)
                .setRealname(defaultRealname(email))
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
                    + randomLowerAlnum(ShopAccountConstant.USERNAME_RANDOM_LENGTH);
            if (shopAccountMapper.selectIdByUsernameAny(candidate) == null) {
                return candidate;
            }
            log.warn("生成的 username 冲突，重试第 {} 次：{}", i + 1, candidate);
        }
        // 8 位小写字母数字有 36^8 ≈ 2.8e12 种组合，连撞 10 次说明系统出问题了
        throw new JeecgBootException("账号生成失败，请稍后重试");
    }

    /** 指定长度的小写字母 + 数字随机串 */
    private String randomLowerAlnum(int length) {
        final String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** 按邮箱查用户（仅未删除） */
    private ShopAccountUser findByEmail(String email) {
        return StringUtils.isBlank(email) ? null : shopAccountMapper.selectByEmail(email);
    }

    // ==================================================================
    // 校验
    // ==================================================================

    /**
     * 图形验证码校验 —— 与 {@code LoginController#validateCaptcha} 的算法**逐字一致**。
     *
     * <p>那边的同名方法是 private，且它所在的 jeecg-system-biz 属于共享模块
     * （本次改造不允许改动），因此只能在这里实现一份等价的。
     * 算法错一个字符（大小写、拼接顺序、是否带 signatureSecret）就永远校验不过，
     * 所以下面每一行都保持与原实现同形。
     *
     * <p>关闭开关也一致：{@code jeecg.firewall.enableLoginCaptcha=false} 时跳过校验，
     * 便于本地开发不用真的看图输码。
     */
    private void validateCaptcha(String captcha, String checkKey) {
        if (jeecgFirewallDisablesCaptcha()) {
            log.debug("jeecg.firewall.enableLoginCaptcha=false，跳过图形验证码校验");
            return;
        }
        if (StringUtils.isBlank(captcha)) {
            throw new JeecgBootException("请输入图形验证码");
        }
        if (StringUtils.isBlank(checkKey)) {
            throw new JeecgBootException("图形验证码已失效，请点击图片刷新");
        }
        String lowerCaseCaptcha = captcha.toLowerCase();
        // 前端调 /sys/randomImage/{key} 时服务端用 md5(key + signatureSecret) 作为
        // realKey 前缀，这里必须用同样的前缀去拼，否则永远查不到
        String keyPrefix = Md5Util.md5Encode(checkKey + jeecgSignatureSecret(), "utf-8");
        String realKey = keyPrefix + lowerCaseCaptcha;
        Object checkCode = redisUtil.get(realKey);
        if (checkCode == null || !checkCode.toString().equals(lowerCaseCaptcha)) {
            log.warn("图形验证码校验失败，checkKey={}，输入={}，redis值={}", checkKey, lowerCaseCaptcha, checkCode);
            throw new JeecgBootException("图形验证码错误");
        }
        // 验证码一次性：用掉就删。图形码虽有 60 秒 TTL，但可被重复使用意味着
        // 攻击者只要过一次码就能复用同一 key 批量调接口
        redisUtil.del(realKey);
    }

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
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

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
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String[] candidates = {
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getHeader("Proxy-Client-IP"),
                request.getHeader("WL-Proxy-Client-IP"),
                request.getRemoteAddr()
        };
        for (String candidate : candidates) {
            if (StringUtils.isBlank(candidate) || "unknown".equalsIgnoreCase(candidate)) {
                continue;
            }
            String[] parts = candidate.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String ip = parts[i].trim();
                if (StringUtils.isNotBlank(ip)) {
                    return StringUtils.left(ip, 64);
                }
            }
        }
        return null;
    }

    private String resolveUserAgent(HttpServletRequest request) {
        return request == null ? null : StringUtils.left(request.getHeader("User-Agent"), 500);
    }

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
    private String defaultRealname(String email) {
        if (StringUtils.isBlank(email)) {
            return "用户";
        }
        int at = email.indexOf('@');
        String prefix = at > 0 ? email.substring(0, at) : email;
        return StringUtils.left(prefix, 100);
    }

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

    // ------------------------------------------------------------------
    // 图形验证码开关与密钥
    //
    // signatureSecret 在 dev profile 来自 ${JEECG_SIGNATURE_SECRET} 环境变量；
    // JeecgBaseConfig 的字段本身有默认值，不会为 null，
    // 但为了与 LoginController 行为完全对齐，仍做空安全处理。
    // ------------------------------------------------------------------

    private boolean jeecgFirewallDisablesCaptcha() {
        try {
            return jeecgBaseConfig != null
                    && jeecgBaseConfig.getFirewall() != null
                    && Boolean.FALSE.equals(jeecgBaseConfig.getFirewall().getEnableLoginCaptcha());
        } catch (Exception e) {
            // 取不到配置时**不跳过**校验（fail-closed）：宁可让用户多输一次验证码，
            // 也不能因为配置读取异常就把防刷闸门打开
            log.warn("读取 jeecg.firewall 配置失败，按【需要校验验证码】处理：{}", e.getMessage());
            return false;
        }
    }

    private String jeecgSignatureSecret() {
        return jeecgBaseConfig == null ? null : jeecgBaseConfig.getSignatureSecret();
    }
}
