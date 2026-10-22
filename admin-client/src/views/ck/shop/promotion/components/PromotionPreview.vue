<template>
  <section class="promo-preview">
    <div class="promo-preview__caption">
      <span class="promo-preview__caption-title">前台秒杀条效果预览</span>
      <span class="promo-preview__caption-hint">
        与店铺端首页一致：{{ bgImage ? '使用背景图（叠暗色蒙层）' : '未设背景图，用品牌色渐变兜底' }}；倒计时读结束时间
      </span>
    </div>

    <!-- 与 nuxt-shop 的 HomeFlashSaleBar 同一套视觉：品牌渐变 / 背景图 + 暗色蒙层、活动名、倒计时、查看全部 -->
    <div class="promo-preview__bar" :class="{ 'is-gradient': !bgImage }" :style="barStyle" :aria-label="title || '限时活动'">
      <span v-if="!bgImage" class="promo-preview__glow" aria-hidden="true"></span>
      <div v-else class="promo-preview__mask" aria-hidden="true"></div>

      <div class="promo-preview__inner">
        <div class="promo-preview__left">
          <p class="promo-preview__name">
            <Icon icon="ant-design:thunderbolt-outlined" class="promo-preview__zap" />
            {{ displayTitle }}
          </p>
          <p v-if="subTitle" class="promo-preview__sub">{{ subTitle }}</p>
        </div>

        <div class="promo-preview__timer">
          <span class="promo-preview__timer-label">{{ countdownLabel }}</span>
          <span v-if="timeReady" class="promo-preview__units" role="timer">
            <template v-for="(unit, index) in countdownUnits" :key="unit.key">
              <span class="promo-preview__unit">{{ unit.value }}</span>
              <span v-if="index < countdownUnits.length - 1" class="promo-preview__colon" aria-hidden="true">:</span>
            </template>
          </span>
          <span v-else class="promo-preview__units promo-preview__units--empty">--:--:--</span>
        </div>

        <a-button v-if="linkUrl" class="promo-preview__cta" size="small" type="primary" ghost>
          查看全部
          <Icon icon="ant-design:right-outlined" />
        </a-button>
        <span v-else class="promo-preview__cta-missing">未填落地页，前台不渲染「查看全部」</span>
      </div>
    </div>

    <p class="promo-preview__note">
      <Icon icon="ant-design:info-circle-outlined" />
      预览只反映当前表单里的值，保存并启用后才会出现在店铺端首页；活动名取「简体中文」那一组。
    </p>
  </section>
</template>

<script lang="ts" name="PromotionPreview" setup>
  import { computed, onMounted, onUnmounted, ref } from 'vue';
  import dayjs from 'dayjs';

  /**
   * 前台秒杀条预览。
   *
   * 只做渲染，不发请求：让运营在保存前看到「活动名 + 倒计时 + 落地页按钮」的实际样子。
   * 倒计时的依据与前台完全一致 —— 只认 endTime（契约 2.2：end_time 是前台倒计时的唯一依据）。
   */
  const props = defineProps<{
    title?: string;
    subTitle?: string;
    bgImage?: string;
    startTime?: string;
    endTime?: string;
    linkUrl?: string;
  }>();

  /** 品牌渐变（与 nuxt-shop/app/assets/css/tailwind.css 的 .brand-gradient 完全一致） */
  const BRAND_GRADIENT = 'linear-gradient(100deg, #FF4D6D 0%, #FF6F86 55%, #FF8FA3 100%)';

  const now = ref(Date.now());
  let timer: ReturnType<typeof setInterval> | null = null;

  const hasStart = computed(() => !!props.startTime && dayjs(props.startTime).isValid());
  const hasEnd = computed(() => !!props.endTime && dayjs(props.endTime).isValid());
  const timeReady = computed(() => hasStart.value || hasEnd.value);
  /** 还没到开始时间：倒计时改成「距开始」，避免显示一个一直不动的结束倒计时 */
  const notStarted = computed(() => hasStart.value && now.value < dayjs(props.startTime).valueOf());

  const displayTitle = computed(() => (props.title || '').trim() || '限时活动');
  const bgImage = computed(() => (props.bgImage || '').trim());

  const barStyle = computed(() =>
    bgImage.value
      ? { backgroundImage: `url(${bgImage.value})`, backgroundSize: 'cover', backgroundPosition: 'center' }
      : { backgroundImage: BRAND_GRADIENT }
  );

  const countdownLabel = computed(() => {
    if (!timeReady.value) {
      return '倒计时（未设置时间）';
    }
    return notStarted.value ? '距开始还有' : '活动结束倒计时';
  });

  const remainingSeconds = computed(() => {
    const target = notStarted.value ? dayjs(props.startTime).valueOf() : hasEnd.value ? dayjs(props.endTime).valueOf() : 0;
    if (!target) {
      return 0;
    }
    return Math.max(0, Math.floor((target - now.value) / 1000));
  });

  const countdownUnits = computed(() => {
    const total = remainingSeconds.value;
    const pad = (value: number) => String(value).padStart(2, '0');
    return [
      { key: 'hours', value: pad(Math.floor(total / 3600)) },
      { key: 'minutes', value: pad(Math.floor((total % 3600) / 60)) },
      { key: 'seconds', value: pad(total % 60) },
    ];
  });

  // 每秒一跳（倒计时是信息、不是动效，减少动效偏好下也照跳）
  onMounted(() => {
    timer = setInterval(() => {
      now.value = Date.now();
    }, 1000);
  });

  onUnmounted(() => {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  });
</script>

<style lang="less" scoped>
  @preview-border: var(--shop-border);

  .promo-preview {
    padding: 0 0 4px;
  }

  .promo-preview__caption {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 12px;
    align-items: baseline;
    margin-bottom: 8px;
  }

  .promo-preview__caption-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--shop-text);
  }

  .promo-preview__caption-hint {
    font-size: 12px;
    color: var(--shop-text-secondary);
  }

  /* 秒杀条本体：宽高比与前台接近，圆角/阴影对齐首页卡片 */
  .promo-preview__bar {
    position: relative;
    overflow: hidden;
    border-radius: 10px;
    box-shadow: 0 8px 24px -10px rgba(255, 77, 109, 0.6);
  }

  .promo-preview__mask {
    position: absolute;
    inset: 0;
    background: var(--shop-text-secondary);
  }

  .promo-preview__glow {
    position: absolute;
    top: -64px;
    right: -32px;
    width: 160px;
    height: 160px;
    background: rgba(255, 255, 255, 0.15);
    border-radius: 50%;
    filter: blur(28px);
  }

  .promo-preview__inner {
    position: relative;
    display: flex;
    flex-wrap: wrap;
    gap: 8px 16px;
    align-items: center;
    justify-content: space-between;
    padding: 14px 24px;
    color: #fff;
  }

  .promo-preview__left {
    min-width: 0;
  }

  .promo-preview__name {
    display: flex;
    gap: 8px;
    align-items: center;
    margin: 0;
    font-size: 18px;
    font-weight: 800;
    line-height: 1.3;
  }

  .promo-preview__zap {
    color: #ffe58f;
  }

  .promo-preview__sub {
    margin: 2px 0 0;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.85);
  }

  .promo-preview__timer {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .promo-preview__timer-label {
    font-size: 12px;
    font-weight: 700;
    color: rgba(255, 255, 255, 0.8);
  }

  .promo-preview__units {
    display: flex;
    gap: 4px;
    align-items: center;

    &--empty {
      font-size: 15px;
      font-weight: 800;
      color: rgba(255, 255, 255, 0.7);
    }
  }

  .promo-preview__unit {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 32px;
    height: 32px;
    padding: 0 6px;
    font-size: 15px;
    font-weight: 800;
    font-variant-numeric: tabular-nums;
    line-height: 1;
    background: var(--shop-text-tertiary);
    border-radius: 8px;
  }

  .promo-preview__colon {
    font-size: 14px;
    font-weight: 800;
    color: rgba(255, 255, 255, 0.7);
  }

  .promo-preview__cta {
    color: #fff !important;
    border-color: rgba(255, 255, 255, 0.7) !important;
  }

  .promo-preview__cta-missing {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.75);
  }

  .promo-preview__note {
    display: flex;
    gap: 6px;
    align-items: center;
    margin: 10px 0 0;
    font-size: 12px;
    line-height: 1.6;
    color: var(--shop-text-secondary);
  }

  .promo-preview__caption + .promo-preview__bar {
    border: 1px solid @preview-border;
  }
</style>
