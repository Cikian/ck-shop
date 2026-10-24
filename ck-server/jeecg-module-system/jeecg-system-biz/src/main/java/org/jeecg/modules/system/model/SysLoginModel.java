package org.jeecg.modules.system.model;


import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录表单
 *
 * @Author scott
 * @since  2019-01-18
 */
@Schema(description="登录对象")
public class SysLoginModel {
	@Schema(description = "账号")
    private String username;
	@Schema(description = "密码")
    private String password;
	@Schema(description = "登录部门")
    private String loginOrgCode;
	@Schema(description = "验证码")
    private String captcha;
	@Schema(description = "验证码key")
    private String checkKey;
	/**
	 * CikVerify 人机验证（工作量证明 POW）一次性 ticket。
	 *
	 * <p>前端调 {@code /api/captcha/challenge} + {@code /api/captcha/verify} 拿到，
	 * 服务端用 SDK 的 CaptchaTicketService#consume 消费：一次性、默认 120 秒、绑定 IP 与 UA。
	 * 有值即走 CikVerify 校验，为空则回退到上面的字符图形验证码（captcha + checkKey）。</p>
	 */
	@Schema(description = "CikVerify 人机验证 ticket（可选，优先于图形验证码）")
    private String captchaTicket;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCaptcha() {
        return captcha;
    }

    public void setCaptcha(String captcha) {
        this.captcha = captcha;
    }

	public String getCheckKey() {
		return checkKey;
	}

	public void setCheckKey(String checkKey) {
		this.checkKey = checkKey;
	}

	public String getCaptchaTicket() {
		return captchaTicket;
	}

	public void setCaptchaTicket(String captchaTicket) {
		this.captchaTicket = captchaTicket;
	}

    public String getLoginOrgCode() {
        return loginOrgCode;
    }

    public void setLoginOrgCode(String loginOrgCode) {
        this.loginOrgCode = loginOrgCode;
    }
}