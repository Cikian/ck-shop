<template>
  <LoginFormTitle v-show="getShow" class="enter-x" />
  <Form class="p-4 enter-x" :model="formData" :rules="getFormRules" ref="formRef" v-show="getShow" @keypress.enter="handleLogin">
    <FormItem name="account" class="enter-x">
      <Input size="large" v-model:value="formData.account" :placeholder="t('sys.login.userName')" class="fix-auto-fill" />
    </FormItem>
    <FormItem name="password" class="enter-x">
      <InputPassword size="large" visibilityToggle v-model:value="formData.password" :placeholder="t('sys.login.password')" />
    </FormItem>

    <!--人机验证：CikVerify 工作量证明（POW），通过后拿到一次性 ticket 随登录请求提交-->
    <FormItem class="enter-x">
      <div class="flex items-center justify-between">
        <Tag :color="captchaVerified ? 'success' : 'default'" class="!mr-0">
          <CheckCircleFilled v-if="captchaVerified" />
          <SafetyCertificateOutlined v-else />
          {{ captchaVerified ? t('sys.login.humanVerificationPassed') : t('sys.login.humanVerificationTip') }}
        </Tag>
        <Button type="link" size="small" :loading="captchaLoading" @click="handleCaptchaClick">
          {{ captchaVerified ? t('sys.login.humanVerificationRetry') : t('sys.login.humanVerificationAction') }}
        </Button>
      </div>
    </FormItem>

    <ARow class="enter-x">
      <ACol :span="12">
        <FormItem>
          <!-- No logic, you need to deal with it yourself -->
          <Checkbox v-model:checked="rememberMe" size="small">
            {{ t('sys.login.rememberMe') }}
          </Checkbox>
        </FormItem>
      </ACol>
      <ACol :span="12">
        <FormItem :style="{ 'text-align': 'right' }">
          <!-- No logic, you need to deal with it yourself -->
          <Button type="link" size="small" @click="setLoginState(LoginStateEnum.RESET_PASSWORD)">
            {{ t('sys.login.forgetPassword') }}
          </Button>
        </FormItem>
      </ACol>
    </ARow>

    <FormItem class="enter-x">
      <Button type="primary" size="large" block @click="handleLogin" :loading="loading">
        {{ t('sys.login.loginButton') }}
      </Button>
      <!-- <Button size="large" class="mt-4 enter-x" block @click="handleRegister">
              {{ t('sys.login.registerButton') }}
            </Button> -->
    </FormItem>
    <ARow class="enter-x">
      <ACol :md="8" :xs="24">
        <Button block @click="setLoginState(LoginStateEnum.MOBILE)">
          {{ t('sys.login.mobileSignInFormTitle') }}
        </Button>
      </ACol>
      <ACol :md="8" :xs="24" class="!my-2 !md:my-0 xs:mx-0 md:mx-2">
        <Button block @click="setLoginState(LoginStateEnum.QR_CODE)">
          {{ t('sys.login.qrSignInFormTitle') }}
        </Button>
      </ACol>
      <ACol :md="7" :xs="24">
        <Button block @click="setLoginState(LoginStateEnum.REGISTER)">
          {{ t('sys.login.registerButton') }}
        </Button>
      </ACol>
    </ARow>

    <Divider class="enter-x">{{ t('sys.login.otherSignIn') }}</Divider>

    <div class="flex justify-evenly enter-x" :class="`${prefixCls}-sign-in-way`">
      <a @click="onThirdLogin('github')" title="github"><GithubFilled /></a>
      <a @click="onThirdLogin('wechat_enterprise')" title="企业微信"> <icon-font class="item-icon" type="icon-qiyeweixin3" /></a>
      <a @click="onThirdLogin('dingtalk')" title="钉钉"><DingtalkCircleFilled /></a>
      <a @click="onThirdLogin('wechat_open')" title="微信"><WechatFilled /></a>
    </div>
  </Form>
  <!-- 第三方登录相关弹框 -->
  <ThirdModal ref="thirdModalRef"></ThirdModal>
</template>
<script lang="ts" setup>
  import { reactive, ref, toRaw, unref, computed } from 'vue';

  import { Checkbox, Form, Input, Row, Col, Button, Divider, Tag } from 'ant-design-vue';
  import { GithubFilled, WechatFilled, DingtalkCircleFilled, SafetyCertificateOutlined, CheckCircleFilled } from '@ant-design/icons-vue';
  import { IconFont } from '/@/utils/iconfont2';
  import LoginFormTitle from './LoginFormTitle.vue';
  import ThirdModal from './ThirdModal.vue';
  import { useI18n } from '/@/hooks/web/useI18n';
  import { useMessage } from '/@/hooks/web/useMessage';

  import { useUserStore } from '/@/store/modules/user';
  import { LoginStateEnum, useLoginState, useFormRules, useFormValid } from './useLogin';
  import { useDesign } from '/@/hooks/web/useDesign';
  import { isCaptchaClosed, useCikianCaptcha } from '/@/hooks/web/useCikianCaptcha';
  import { encryptAESCBC } from '/@/utils/cipher';

  const ACol = Col;
  const ARow = Row;
  const FormItem = Form.Item;
  const InputPassword = Input.Password;
  const { t } = useI18n();
  const { notification, createErrorModal } = useMessage();
  const { prefixCls } = useDesign('login');
  const userStore = useUserStore();
  const { open: openCaptcha } = useCikianCaptcha();

  const { setLoginState, getLoginState } = useLoginState();
  const { getFormRules } = useFormRules();

  const formRef = ref();
  const thirdModalRef = ref();
  const loading = ref(false);
  const rememberMe = ref(false);
  // CikVerify 一次性 ticket：120 秒有效、只能用一次，登录失败后必须重新验证
  const captchaTicket = ref('');
  const captchaLoading = ref(false);
  const captchaVerified = computed(() => !!unref(captchaTicket));

  const formData = reactive({
    account: 'admin',
    password: '123456',
  });

  const { validForm } = useFormValid(formRef);

  //onKeyStroke('Enter', handleLogin);

  const getShow = computed(() => unref(getLoginState) === LoginStateEnum.LOGIN);

  /**
   * 打开 CikVerify 人机验证弹窗并拿到 ticket。
   * 已有 ticket 时直接返回 true；用户主动关闭不算错误，只是中止登录。
   */
  async function handleCaptcha(): Promise<boolean> {
    if (unref(captchaTicket)) return true;
    // 验证中重复点击不再弹第二个遮罩
    if (unref(captchaLoading)) return false;
    captchaLoading.value = true;
    try {
      const ticket = await openCaptcha();
      if (!ticket) return false;
      captchaTicket.value = ticket;
      return true;
    } catch (error) {
      // closed 是用户主动关闭，不需要打扰；其余（CDN/CSP、超时、ticket 缺失）给出提示
      if (!isCaptchaClosed(error)) {
        notification.error({
          message: t('sys.api.errorTip'),
          description: t('sys.login.humanVerificationFailed'),
          duration: 3,
        });
      }
      return false;
    } finally {
      captchaLoading.value = false;
    }
  }

  /** 状态标签上的「去验证 / 重新验证」 */
  function handleCaptchaClick() {
    if (unref(captchaVerified)) {
      captchaTicket.value = '';
    }
    handleCaptcha();
  }

  async function handleLogin() {
    const data = await validForm();
    if (!data) return;
    // 先过安全验证：没有 ticket 就弹 POW 验证码，用户关闭则中止登录
    if (!(await handleCaptcha())) return;
    try {
      loading.value = true;

      // 密码使用AES加密传输
      const encryptedPassword = encryptAESCBC(data.password);
      const { userInfo } = await userStore.login(
        toRaw({
          password: encryptedPassword,
          username: data.account,
          // 后端用 CikVerify SDK 的 CaptchaTicketService 一次性消费该 ticket
          captchaTicket: unref(captchaTicket),
          mode: 'none', //不要默认的错误提示
        })
      );
      if (userInfo) {
        notification.success({
          message: t('sys.login.loginSuccessTitle'),
          description: `${t('sys.login.loginSuccessDesc')}: ${userInfo.realname}`,
          duration: 3,
        });
      }
    } catch (error) {
      notification.error({
        message: t('sys.api.errorTip'),
        description: error.message || t('sys.api.networkExceptionMsg'),
        duration: 3,
      });
      loading.value = false;
      // ticket 已随这次请求被消费（或过期），重试前必须重新验证
      captchaTicket.value = '';
    }
  }

  /**
   * 第三方登录
   * @param type
   */
  function onThirdLogin(type) {
    thirdModalRef.value.onThirdLogin(type);
  }
</script>
