<!--
  商品页底部动作条：加入购物车（黄） / 立即购买（主色 + 闪电） / 收藏（心形 + 小字，竖向）

  口径：docs/商品页布局与底部动作条-设计说明.md 2.3 / 八 / 九 / 十 / 十一
    · 桌面（≥1024px）：宽度 = 右侧信息栏；移动端：通栏（左右各留 8px）；
    · 三个动作的语义全部沿用：未登录 → 点按钮直接引导登录；规格未选全 → 按钮置灰（面板里有提示）；
      库存/组合不可售 → 禁用；收藏状态以后端返回为准（不做本地乐观更新）。

  定位（第 5 轮改为 **CSS sticky**，不再由 JS 注入 transform）：
    根节点 = `sticky bottom-0 h-0`，是商品主信息区（`[data-sticky-zone]`）的最后一个子节点：
      · zone 还很长时，sticky 把根节点钉在视口底部 → 表现为「固定底栏」；
      · 滚到 zone 底部（=「为你推荐」上方）时，根节点自然落位 → 动作条随内容一起上滚，回滚自动恢复。
    为什么不用 `fixed + translateY`（第 4 轮的做法）：那需要 JS 每帧跟着 scroll 改 transform，
    真机惯性滚动时永远慢半拍，表现为「抖一下 / 轻微漂移」。sticky 由浏览器合成层负责，
    零 JS、零延迟，桌面/移动端行为完全一致。
    纵向偏移由内层绝对定位的 `bottom-*` 提供（移动端 = 安全区 + 8px，桌面 = 32px），
    根节点 `h-0` 所以不占文档流高度、不影响页面高度。

  浮层底（第 3 轮，需求方给定 CSS）：
    `.ui-action-dock` = border-radius 28px / background #fff6f2 / 双向 30px 柔和投影。
    它同时修掉了"透明的收藏按钮透出下方内容"（原先 `.ui-pill-ghost` 把 background 强制成 transparent）。
    ⚠️ 桌面 32px 必须与 useStickyAside 的 BAR_BOTTOM_OFFSET 一致（主图底边与这里同线）。

  帘子（第 4 轮）：浮层底与视口底之间那段空隙必须不透明，否则右栏内容会从按钮下方漏出来。
    · 绝对定位、从浮层底盖到视口底，颜色 = 页面底色（看起来"什么都没有"）；
    · 桌面只在右列宽内，移动端全屏出血；
    · 只在「钉在视口底部」时显示：滚到 zone 底部、动作条开始上滚后，帘子会变成一块悬在内容
      中间、把文字切掉一截的色块 → 由 useStickyAside 写入 `--action-mask-visibility: hidden` 隐藏；
    · 放在浮层底之前 + 浮层底 `relative`：让浮层底与其投影画在帘子之上（投影不被切掉）。

  收藏按钮（第 5 轮）：改为**竖向**布局 —— 心形在上、小字在下，按钮变窄，前面两个按钮因此更长。
    文案用短标签（收藏 / 已收藏；en Wishlist / Saved；fr Favoris / Enregistré），
    长文案（"登录后加入购物车"之类）一律不再进按钮，避免多语言溢出。

  层级 z-40：高于页面内容与吸顶锚点（z-30），低于全局 Header（z-50）与 toast/modal。
-->
<template>
  <div data-sticky-actions class="pointer-events-none sticky bottom-0 z-40 h-0">
    <!-- 纵向偏移：把动作条整体抬离视口底（移动端让出安全区 + 8px；桌面 32px） -->
    <div class="absolute inset-x-0 bottom-[calc(env(safe-area-inset-bottom,0px)+8px)] lg:bottom-8">
      <div class="lg:grid lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <!-- 第 1 列占位：让动作条精确落在右侧 40% 信息栏上（与页面商品页栅格一致） -->
        <div class="hidden lg:block" aria-hidden="true"></div>
        <div class="relative">
          <!-- 帘子：浮层底 → 视口底，遮住从按钮下方漏出来的内容（隐藏时机见文件头注释）。
               移动端要全屏出血 → 负边距正好抵掉页容器的 px-4（16px）；桌面只盖右列，不偏移 -->
          <div
            data-sticky-actions-mask
            aria-hidden="true"
            class="ui-action-mask pointer-events-auto absolute top-full left-[-1rem] right-[-1rem] h-[calc(env(safe-area-inset-bottom,0px)+8px)] lg:left-0 lg:right-0 lg:h-8"
          ></div>
          <div
            data-sticky-actions-row
            class="ui-action-dock pointer-events-auto relative flex w-full items-center gap-2 px-3 py-2 sm:gap-3"
          >
            <!-- 加入购物车：黄色（accent 系），主体是 SKU，必须先命中可售组合（无规格商品除外） -->
            <AsyncButton
              type="button"
              class="ui-pill ui-pill-accent h-11 flex-1 gap-1.5 text-[13px] font-bold shadow-[0_8px_20px_-10px_rgba(31,26,28,0.35)] disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:shadow-none sm:text-sm"
              :pending="adding"
              :pending-label="t('product.adding')"
              :disabled="!canAddToCart"
              @click="emit('add-to-cart')"
            >
              <UIcon name="lucide:shopping-cart" class="h-4 w-4 shrink-0 sm:h-5 sm:w-5" aria-hidden="true" />
              <span class="truncate">{{ addToCartLabel }}</span>
            </AsyncButton>

            <!-- 立即购买：品牌主色 CTA，闪电图标；不经过购物车，写结算快照 -->
            <AsyncButton
              type="button"
              class="ui-pill ui-pill-brand h-11 flex-1 gap-1.5 text-[13px] font-bold shadow-[0_8px_20px_-10px_rgba(31,26,28,0.35)] disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:shadow-none sm:text-sm"
              :pending="buying"
              :pending-label="t('product.buying')"
              :disabled="!canBuyNow"
              @click="emit('buy-now')"
            >
              <UIcon name="lucide:zap" class="h-4 w-4 shrink-0 sm:h-5 sm:w-5" aria-hidden="true" />
              <span class="truncate">{{ buyNowLabel }}</span>
            </AsyncButton>

            <!-- 收藏：竖向（心形在上 + 小字在下），窄按钮把空间让给前两个动作；
                 状态与 aria-pressed 直接来自后端返回的 favorited -->
            <AsyncButton
              type="button"
              class="ui-pill ui-pill-fav h-11 w-[52px] shrink-0 px-0 text-[10px] font-bold shadow-[0_8px_20px_-10px_rgba(31,26,28,0.25)] sm:w-[58px]"
              :pending="favoritePending"
              :pending-label="t('common.loading')"
              :aria-pressed="favorited"
              :aria-label="favorited ? t('product.removeFromWishlist') : t('product.addToWishlist')"
              @click="emit('toggle-favorite')"
            >
              <span class="flex flex-col items-center gap-0.5 leading-none">
                <UIcon
                  name="lucide:heart"
                  class="h-[18px] w-[18px] shrink-0"
                  :class="favorited ? 'fill-current' : ''"
                  aria-hidden="true"
                />
                <span class="whitespace-nowrap">{{ favorited ? t('product.wishlistedShort') : t('product.favShort') }}</span>
              </span>
            </AsyncButton>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  /** 加入购物车 */
  adding: boolean
  addToCartLabel: string
  canAddToCart: boolean
  /** 立即购买 */
  buying: boolean
  buyNowLabel: string
  canBuyNow: boolean
  /** 收藏 */
  favorited: boolean
  favoritePending: boolean
}>()

const emit = defineEmits<{
  (e: 'add-to-cart'): void
  (e: 'buy-now'): void
  (e: 'toggle-favorite'): void
}>()

const { t } = useI18n()

</script>
