package org.jeecg.modules.shop.account.constant;

/**
 * <p>
 * 店铺端账号体系常量
 * </p>
 *
 * 全部集中在这里，而不是散落在 Controller / Service 里写字面量：
 * Redis key 前缀一旦有两处不同（比如控制台加了冒号、模板没加），
 * 就会出现"验证码发出去了但校验永远不过"这种最难查的问题。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-15
 */

public final class ShopAccountConstant {

    private ShopAccountConstant() {
    }

    // ==================================================================
    // 角色
    // ==================================================================

    /** 前台买家角色编码（由迁移脚本 V3.9.5_12 写入 sys_role） */
    public static final String ROLE_SHOP_CUSTOMER = "shop_customer";

    // ==================================================================
    // 邮箱验证码
    // ==================================================================

    /** 邮箱验证码 Redis key：shop:email:code:{scene}:{email} */
    public static final String EMAIL_CODE_PREFIX = "shop:email:code:";

    /** 发送冷却 Redis key：shop:email:code:cd:{email}（与契约逐字一致，scene 无关） */
    public static final String EMAIL_CODE_COOLDOWN_PREFIX = "shop:email:code:cd:";

    /** 同一邮箱每日发送条数上限计数 key */
    public static final String EMAIL_DAILY_EMAIL_PREFIX = "shop:email:daily:email:";

    /** 同一 IP 每日发送条数上限计数 key */
    public static final String EMAIL_DAILY_IP_PREFIX = "shop:email:daily:ip:";

    /** 验证码位数 */
    public static final int EMAIL_CODE_LENGTH = 6;

    /** 验证码有效期（秒）：5 分钟 */
    public static final long EMAIL_CODE_TTL_SECONDS = 5 * 60L;

    /** 同一邮箱发送冷却（秒）：60 秒 */
    public static final long EMAIL_CODE_COOLDOWN_SECONDS = 60L;

    /** 同一邮箱每日上限 */
    public static final long EMAIL_DAILY_LIMIT_PER_EMAIL = 10L;

    /** 同一 IP 每日上限 */
    public static final long EMAIL_DAILY_LIMIT_PER_IP = 30L;

    /** 邮箱验证码校验失败计数 key：shop:email:code:fail:{scene}:{email} */
    public static final String EMAIL_CODE_FAIL_PREFIX = "shop:email:code:fail:";

    /** 同一邮箱验证码最多允许输错次数，超过后该验证码作废并临时锁定该场景 */
    public static final int EMAIL_CODE_MAX_ATTEMPTS = 5;

    /** 邮箱验证码输错锁定时长（秒）：15 分钟 */
    public static final long EMAIL_CODE_ATTEMPT_LOCK_SECONDS = 15 * 60L;

    // ==================================================================
    // 限流 / 图形验证码防爆破（Redis 固定窗口）
    // ==================================================================

    /** 店铺端限流 key 前缀：shop:rl:{scope} */
    public static final String RATE_LIMIT_PREFIX = "shop:rl:";

    /** 发邮箱验证码：同一 IP 每分钟上限 */
    public static final int RL_SEND_CODE_PER_MINUTE = 5;

    /** 发邮箱验证码：同一 IP 每小时上限 */
    public static final int RL_SEND_CODE_PER_HOUR = 20;

    /** 注册：同一 IP 每分钟上限 */
    public static final int RL_REGISTER_PER_MINUTE = 3;

    /** 注册：同一 IP 每小时上限 */
    public static final int RL_REGISTER_PER_HOUR = 10;

    /** 邮箱验证码登录：同一 IP 每分钟上限 */
    public static final int RL_LOGIN_CODE_PER_MINUTE = 10;

    /** 重置密码：同一 IP 每分钟上限 */
    public static final int RL_RESET_PER_MINUTE = 5;

    /** 密码登录：同一 IP 每分钟上限 */
    public static final int RL_PASSWORD_LOGIN_PER_MINUTE = 10;

    /** 密码登录：同一 IP 每小时上限 */
    public static final int RL_PASSWORD_LOGIN_PER_HOUR = 60;

    /** 图形验证码：同一张（checkKey 前缀）最多允许输错次数 */
    public static final int CAPTCHA_MAX_FAIL_PER_KEY = 5;

    /** 图形验证码：失败计数保留时长（秒） */
    public static final long CAPTCHA_FAIL_TTL_SECONDS = 300L;

    /** 图形验证码：同一 IP 在窗口内最多允许输错次数 */
    public static final int CAPTCHA_MAX_FAIL_PER_IP = 20;

    /** 图形验证码：IP 失败计数窗口（秒） */
    public static final long CAPTCHA_FAIL_IP_TTL_SECONDS = 600L;

    /** 场景：注册 */
    public static final String SCENE_REGISTER = "register";

    /** 场景：登录 */
    public static final String SCENE_LOGIN = "login";

    /** 场景：重置密码 */
    public static final String SCENE_RESET = "reset";

    // ==================================================================
    // Google OAuth
    // ==================================================================

    /** OAuth state Redis key：shop:oauth:state:{state} */
    public static final String OAUTH_STATE_PREFIX = "shop:oauth:state:";

    /** OAuth state 有效期（秒）：10 分钟 */
    public static final long OAUTH_STATE_TTL_SECONDS = 10 * 60L;

    /** Google OAuth 授权端点 */
    public static final String GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";

    /** Google OAuth 换取 token 端点 */
    public static final String GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    /** Google 用户信息端点（返回 sub / email / name / picture） */
    public static final String GOOGLE_USERINFO_ENDPOINT = "https://openidconnect.googleapis.com/v1/userinfo";

    /** Google userinfo 需要的 scope */
    public static final String GOOGLE_SCOPE = "openid email profile";

    // ==================================================================
    // 登录方式（写入 ck_shop_login_log.login_type）
    // ==================================================================

    public static final String LOGIN_TYPE_PASSWORD = "PASSWORD";
    public static final String LOGIN_TYPE_EMAIL_CODE = "EMAIL_CODE";
    public static final String LOGIN_TYPE_GOOGLE = "GOOGLE";

    // ==================================================================
    // 其它
    // ==================================================================

    /** 密码长度下限 */
    public static final int PASSWORD_MIN_LENGTH = 8;

    /** 密码长度上限 */
    public static final int PASSWORD_MAX_LENGTH = 32;

    /** username 前缀 */
    public static final String USERNAME_PREFIX = "u_";

    /** username 随机部分长度（共 10 字符：u_ + 8） */
    public static final int USERNAME_RANDOM_LENGTH = 8;

    /** username 生成冲突时的最大重试次数 */
    public static final int USERNAME_MAX_RETRY = 10;

    /** salt 长度 */
    public static final int SALT_LENGTH = 8;

    /** 自动注册时写入的"占位密码"明文长度（32 位随机串，无人知晓） */
    public static final int PLACEHOLDER_PASSWORD_LENGTH = 32;

    /** pwd_set 语义：用户已自行设置过密码 */
    public static final int PWD_SET_YES = 1;

    /** pwd_set 语义：系统随机占位（用户没有真实密码） */
    public static final int PWD_SET_NO = 0;

    // ==================================================================
    // 个人中心：资料与头像
    // ==================================================================

    /** 昵称长度上限（sys_user.realname 是 varchar(100)，但对 C 端限制到 30 更合理） */
    public static final int REALNAME_MAX_LENGTH = 30;

    /** 手机号长度上限（sys_user.phone 是 varchar(45)，这里按 E.164 + 分隔符留足余量） */
    public static final int PHONE_MAX_LENGTH = 20;

    /** 头像原图大小上限：2MB（BFF 已裁成 512×512 webp，正常只有几十 KB） */
    public static final long AVATAR_MAX_BYTES = 2L * 1024 * 1024;

    /**
     * 头像在 OSS 中的目录（最终 key = ck.oss.object-dir-prefix + "/" + 本值 + "/" + 文件名）。
     *
     * <p>单独开一个目录而不是混在商品图里：便于按前缀清理/统计，也避免
     * "商品图目录被当成头像目录"这种误操作。</p>
     */
    public static final String AVATAR_OBJECT_DIR = "shop/avatar";

    /**
     * 预设头像白名单（站内路径，与 {@code nuxt-shop/public/avatars/} 下的文件一一对应）。
     *
     * <p>白名单必须在服务端：前端提交的 {@code avatar} 只能命中这里，
     * 否则"选择头像"接口就成了一个可以往库里写任意 URL 的入口
     * （存储型追踪像素 / 外部资源引用）。</p>
     */
    public static final java.util.List<String> PRESET_AVATARS = java.util.List.of(
            "/avatars/preset-1.webp",
            "/avatars/preset-2.webp",
            "/avatars/preset-3.webp",
            "/avatars/preset-4.webp",
            "/avatars/preset-5.webp",
            "/avatars/preset-6.webp",
            "/avatars/preset-7.webp",
            "/avatars/preset-8.webp"
    );
}
