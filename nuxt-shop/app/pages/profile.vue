<template>
  <div class="flex min-h-[calc(100vh-var(--nav-total-height))] flex-col bg-[var(--color-cream)] pt-navbar">
    <!--
      ⚠️ 根节点**不能**用 min-h-screen：
      布局里 `main` 本身就是 `flex-1`（页脚钉在视口底部），页面再写 100vh 就等于
      在"已经被导航下推了一段"的位置又占满一屏 —— 实测视口 900 时文档高 1382、
      内容只到 591，多出来的 400 多像素可以滚，滚下去只是一片奶油色空白
      （用户反馈："下面没有内容，为什么要留白这么多"）。

      改成「视口高度 - 导航高度」并纵向 flex，内容区填满剩余高度；
      右栏内容卡 `flex-1` 吃掉这部分空白 —— 空白变成白卡内部的留白，而不是裸露的空场。
    -->
    <div class="mx-auto flex w-full max-w-7xl flex-1 flex-col px-2 pb-14 sm:px-3 lg:px-4">
      <!-- ===================== 页头 ===================== -->
      <!--
        视觉页头（「个人中心 / 管理您的账户，查看订单信息」）已按要求整块删除：
        身份卡本身就说明了"这是谁的账户"，再顶一行标题只是重复。

        ⚠️ 但 h1 必须留着 —— 只对读屏软件可见（sr-only）：
        每个页面都要有的一级标题是文档结构与"跳到主要内容"的锚点，
        删掉可见文字不等于删掉语义。视觉上不占位、不影响排版。
      -->
      <h1 class="sr-only">{{ t('profile.title') }}</h1>

      <!-- ===================== 未登录态 ===================== -->
      <template v-if="!isLoggedIn">
        <div class="mx-auto max-w-lg">
          <div class="ui-card relative overflow-hidden px-6 py-12 text-center sm:px-10">
            <span class="brand-gradient-soft mx-auto flex h-20 w-20 items-center justify-center rounded-full">
              <UIcon name="lucide:user" class="h-9 w-9 text-[var(--color-brand-500)]" aria-hidden="true" />
            </span>
            <h2 class="mt-6 text-xl font-black text-[var(--color-ink)]">
              {{ t('profile.notLoggedIn') }}
            </h2>
            <p class="mt-2 text-sm text-[var(--color-muted)]">
              {{ t('profile.pleaseLogin') }}
            </p>
            <div class="mt-7 flex flex-col gap-3 sm:flex-row sm:justify-center">
              <NuxtLinkLocale to="/login" class="ui-pill ui-pill-brand h-11 px-7 text-sm">
                <UIcon name="lucide:log-in" class="h-4 w-4" aria-hidden="true" />
                {{ t('profile.goLogin') }}
              </NuxtLinkLocale>
              <NuxtLinkLocale to="/register" class="ui-pill ui-pill-ghost h-11 px-7 text-sm">
                <UIcon name="lucide:user-plus" class="h-4 w-4" aria-hidden="true" />
                {{ t('profile.register') }}
              </NuxtLinkLocale>
            </div>
          </div>
        </div>
      </template>

      <!-- ===================== 已登录态 ===================== -->
      <template v-else>
        <!--
          账户中心双栏布局（≥1024px）：
            左栏 = 身份卡 + 分区导航（随滚动吸顶）；右栏 = 数据概览 + 当前分区内容。

          为什么改成双栏：
            早先是「通栏身份卡 → 通栏数据条 → 通栏标签 → 通栏面板」四段纵向堆叠。
            在 1760px 内容宽度下，每段都被拉成一条很长的横条：昵称在左、按钮在右，
            中间空出上千像素；表单输入框也被拉成 700px 宽的两列，整页看着松散、
            没有重心（这就是"排版不好看"的直接来源）。
            拆成 300px 左栏 + 自适应右栏后，身份信息与导航收在一个固定宽度的竖列里，
            右栏内容有稳定的行宽，视觉重心和阅读路径都清楚了。

          <1024px 退回单列，顺序仍是：身份 → 导航 → 数据 → 内容
          （导航是同一份 DOM，只是把竖排换成横滑药丸，不重复渲染、id 不会撞）。
        -->
        <div class="grid flex-1 items-start gap-4 lg:grid-cols-[minmax(260px,300px)_minmax(0,1fr)] lg:items-stretch lg:gap-5">
          <!-- ========== 左栏 ========== -->
          <!--
            ⚠️ min-w-0 不能省：grid item 的 min-width 默认是 auto（= min-content），
            而左栏里的分区导航是横向药丸行，它的 min-content 是「四个药丸的总宽」（390px 下约 434px）。
            少了这一条，整个隐式列会被撑到 434px，进而把右栏也一起撑宽，
            移动端就会出现「页面被缩小、右边被截断」（实测 doc.scrollWidth 442 > 390）。
          -->
          <!-- lg:self-start 不能省：网格宽屏改成 items-stretch 后，左栏会被拉高，sticky 就失效了 -->
          <aside class="flex min-w-0 flex-col gap-4 lg:sticky lg:top-[calc(var(--nav-total-height)+1.5rem)] lg:self-start">
            <!-- 身份卡：头像 / 昵称 / 邮箱 / 两个动作 -->
            <section class="ui-card relative overflow-hidden px-5 pb-5 pt-6 text-center">
              <!--
                顶部品牌色晕染（不是一条色带）：早先用的是「104px 高的纯色矩形 + 头像压在边界上」，
                实机上能看见一条横贯卡片的硬边，像贴纸歪了。改成向下淡出的渐变 + 右上角一团明黄晕染，
                边缘完全消失，和全站 brand-gradient-soft 的柔和气质一致。
              -->
              <div
                class="pointer-events-none absolute inset-x-0 top-0 h-[150px] bg-gradient-to-b from-[var(--color-brand-100)] to-transparent"
                aria-hidden="true"
              ></div>
              <div
                class="pointer-events-none absolute right-[-46px] top-[-64px] h-[150px] w-[150px] rounded-full bg-[var(--color-accent-200)]/50 blur-2xl"
                aria-hidden="true"
              ></div>

              <div class="relative mx-auto w-full max-w-[420px] lg:max-w-none">
                <!-- 头像：整块可点，点击打开「更换头像」（hover/focus 出相机遮罩） -->
                <button
                  type="button"
                  class="group relative mx-auto block h-[92px] w-[92px] cursor-pointer overflow-hidden rounded-full bg-[var(--color-surface-secondary)] shadow-[0_8px_20px_-8px_rgba(31,26,28,0.35)] ring-4 ring-[var(--color-surface)] transition-shadow duration-200 hover:shadow-[0_10px_24px_-8px_rgba(255,77,109,0.45)] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[var(--color-brand-500)] motion-reduce:transition-none"
                  :aria-label="t('profile.changeAvatar')"
                  @click="avatarDialogOpen = true"
                >
                  <img
                    v-if="userInfo?.avatar && !avatarFailed"
                    :src="userInfo.avatar"
                    :alt="displayName"
                    width="92"
                    height="92"
                    class="h-full w-full object-cover"
                    @error="avatarFailed = true"
                  >
                  <span
                    v-else-if="avatarInitial"
                    class="flex h-full w-full items-center justify-center text-[30px] font-black text-[var(--color-brand-500)]"
                    aria-hidden="true"
                  >
                    {{ avatarInitial }}
                  </span>
                  <span v-else class="flex h-full w-full items-center justify-center">
                    <UIcon name="lucide:user" class="h-10 w-10 text-[var(--color-brand-300)]" aria-hidden="true" />
                  </span>

                  <span
                    class="absolute inset-0 flex items-center justify-center bg-[var(--color-ink)]/45 opacity-0 transition-opacity duration-200 group-hover:opacity-100 group-focus-visible:opacity-100 motion-reduce:transition-none"
                    aria-hidden="true"
                  >
                    <UIcon name="lucide:camera" class="h-6 w-6 text-white" />
                  </span>
                </button>

                <h2 class="mt-3.5 truncate px-1 text-[19px] font-black leading-tight text-[var(--color-ink)]">
                  {{ displayName }}
                </h2>
                <p v-if="userInfo?.email" class="mt-1 truncate px-1 text-[12.5px] text-[var(--color-ink-soft)]">
                  {{ userInfo.email }}
                </p>

                <div class="mt-5 flex flex-col items-stretch">
                  <!--
                    「编辑资料」= 进入「账户资料」分区 **并** 打开编辑模式。
                    只切分区不打开编辑的话，用户点了按钮却仍看到只读信息，等于点了没反应。
                  -->
                  <button
                    type="button"
                    class="ui-pill ui-pill-brand h-11 px-3 text-[13px]"
                    @click="startEditProfile"
                  >
                    <UIcon name="lucide:pencil" class="h-4 w-4" aria-hidden="true" />
                    {{ t('profile.editProfile') }}
                  </button>
                  <!--
                    退出登录降级成文字按钮：两个等宽药丸并排时，法语 "Modifier le profil" /
                    "Se déconnecter" 会各自折成两行，把卡片撑高、两个按钮高度还不对称。
                    主操作独占一行 + 次要操作用中性文字链，三种语言都不会换行，层级也更清楚。
                  -->
                  <button
                    type="button"
                    class="mx-auto mt-2 inline-flex min-h-[38px] cursor-pointer items-center gap-1.5 rounded-full px-4 py-2 text-[12.5px] font-medium text-[var(--color-muted)] transition-colors duration-200 hover:text-[var(--color-error-600)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-500)] motion-reduce:transition-none"
                    @click="handleLogout"
                  >
                    <UIcon name="lucide:log-out" class="h-3.5 w-3.5" aria-hidden="true" />
                    {{ t('profile.logout') }}
                  </button>
                </div>
              </div>
            </section>

            <!--
              分区导航（WAI-ARIA tabs）。
              宽屏是竖排卡片（白底 + 选中项浅珊瑚底），窄屏是横滑药丸：
              同一份 DOM、同一批 id，只靠断点换排布方向，不重复渲染两套导航。
            -->
            <nav class="lg:rounded-[var(--radius-card)] lg:bg-[var(--color-surface)] lg:p-2 lg:shadow-[var(--shadow-card)]">
              <div
                class="no-scrollbar flex gap-1.5 overflow-x-auto lg:flex-col lg:gap-1 lg:overflow-visible"
                role="tablist"
                :aria-label="t('profile.title')"
              >
                <button
                  v-for="(tab, index) in tabs"
                  :id="`profile-tab-${tab.key}`"
                  :key="tab.key"
                  type="button"
                  role="tab"
                  :aria-selected="activeTab === tab.key"
                  :aria-controls="`profile-panel-${tab.key}`"
                  :tabindex="activeTab === tab.key ? 0 : -1"
                  class="flex h-9 shrink-0 cursor-pointer items-center gap-2 rounded-full px-3.5 text-[13px] font-bold transition-colors duration-200 motion-reduce:transition-none lg:h-11 lg:w-full lg:justify-start lg:rounded-[14px] lg:px-3"
                  :class="tabItemClass(tab.key)"
                  @click="selectTab(tab.key)"
                  @keydown="handleTabKeydown($event, index)"
                >
                  <UIcon :name="tab.icon" class="h-4 w-4 shrink-0" aria-hidden="true" />
                  <span class="truncate">{{ t(tab.label) }}</span>
                  <UIcon
                    name="lucide:chevron-right"
                    class="ml-auto hidden h-4 w-4 shrink-0 opacity-45 lg:block"
                    aria-hidden="true"
                  />
                </button>
              </div>
            </nav>
          </aside>

          <!-- ========== 右栏 ========== -->
          <!-- 右栏撑满可视高度，内容卡 flex-1：内容少时空白落在白卡里，不是裸露的奶油色空场 -->
          <div class="flex min-w-0 flex-col gap-4 lg:gap-5">
            <!--
              这里原本还有一排通栏「数据卡」（订单数 / 收藏数 / 优惠券 / 积分）。
              本次按要求把它**并进「账户资料」的只读视图**（账户概览：订单 / 收藏 / 收货地址）：
                · 同一组数字在一屏里出现两次没有意义 —— 用户已经因为"两个改头像入口"提过一次重复问题；
                · 优惠券 / 积分后端至今没有接口，长期占着两张「未开通」的空卡只是噪音。

              当前分区内容
            -->
            <section class="ui-card flex flex-1 flex-col px-5 py-5 sm:px-6 sm:py-6">
              <!--
                分区表头：标题 + 一句说明（+ 该分区的主操作）。
                主操作和标题同一条基线，而不是在标题下面再单起一行只有按钮的横带。
              -->
              <header class="flex flex-wrap items-start justify-between gap-x-4 gap-y-3 border-b border-[var(--color-line-soft)] pb-4">
                <div class="min-w-0">
                  <h2 class="text-[17px] font-black leading-tight text-[var(--color-ink)]">
                    {{ t(activeTabMeta.label) }}
                  </h2>
                  <p class="mt-1 text-[12.5px] leading-relaxed text-[var(--color-muted)]">
                    {{ t(activeTabMeta.desc) }}
                  </p>
                </div>

                <!--
                  分区主操作（与标题同一条基线）。
                  「编辑资料」不放在这里：资料卡上已经有一个同样的主按钮，
                  同一屏出现两个一模一样的"编辑资料"就是用户刚提过的"两个改头像入口"那类重复。
                  这里只保留收货地址的「新增地址」（空态里已经有一个居中主按钮时不出，避免同屏两个主按钮）。
                -->
                <button
                  v-if="activeTab === 'addresses' && addressItems.length"
                  type="button"
                  class="ui-pill ui-pill-brand h-9 shrink-0 px-4 text-[13px]"
                  @click="openAddressCreate"
                >
                  <UIcon name="lucide:plus" class="h-4 w-4" aria-hidden="true" />
                  {{ t('address.add') }}
                </button>
              </header>

              <!--
                panel 容器也撑满：内容少时（空态 / 只有一条地址）让空态在伸展后的白卡里垂直居中，
                而不是"内容贴着卡片顶部 + 底下几百像素白"。
              -->
              <Transition name="fade" mode="out-in">
                <div
                  :id="`profile-panel-${activeTab}`"
                  :key="activeTab"
                  role="tabpanel"
                  :aria-labelledby="`profile-tab-${activeTab}`"
                  class="flex flex-1 flex-col pt-5"
                >
                  <!--
                    账户资料：默认只读（账户概览 + 个人资料 + 默认收货地址 + 账户密码入口），
                    点「编辑资料」进入编辑模式。编辑态由本页持有并下发，
                    因为入口有两个（资料卡按钮 / 分区表头按钮），状态不能只活在面板里。
                  -->
                  <ProfileSettingsPanel
                    v-if="activeTab === 'settings'"
                    v-model:editing="editingProfile"
                    :order-total="orderTotal"
                    :favorite-total="favoriteTotal"
                    :address-total="addressItems.length"
                    @goto-tab="selectTab"
                  />

                  <!-- 我的收藏：真实数据（GET /api/favorite），支持在本页直接取消收藏 -->
                  <ProfileFavoritesPanel v-else-if="activeTab === 'favorites'" />

                  <!--
                    我的订单：真实数据（GET /api/order）。
                    这里刻意做得比 /orders 整页更紧凑（单行摘要 + 缩略图条），
                    但状态文字与按钮可见性**完全沿用后端字段**，与 /orders 是同一套口径。
                  -->
                  <ProfileOrdersPanel v-else-if="activeTab === 'orders'" />

                  <ProfileAddressesPanel v-else-if="activeTab === 'addresses'" ref="addressPanelRef" />
                </div>
              </Transition>
            </section>
          </div>
        </div>
      </template>
    </div>

    <!-- 更换头像弹层：唯一入口是资料卡上的大头像（面板里不再重复一个「更换头像」按钮） -->
    <ProfileAvatarDialog v-model:open="avatarDialogOpen" />
  </div>
</template>

<script setup lang="ts">
/**
 * 个人中心是私有页面：未登录 / 登录过期时由 auth 中间件重定向到登录页，
 * 并带上 redirect 参数，登录成功后回到这里。
 */
definePageMeta({ middleware: 'auth' })

import { useAuth } from '~~/composables/useAuth'
import { useFavoriteList } from '~~/composables/useFavorites'
import { useOrderList } from '~~/composables/useOrderList'
import { useAddressBook } from '~~/composables/useAddressBook'
import { useProfileAccount } from '~~/composables/useProfileAccount'
import { useProfileFields } from '~~/composables/useProfileFields'

/**
 * 个人中心。
 *
 * 本页最重要的一件事：**删掉所有编造的 mock 数据**。
 * 「我的收藏」「我的订单」「收货地址」三个面板都已接通真实接口
 * （GET /api/favorite、GET /api/order、GET /api/address/list）；
 * 优惠券 / 积分后端仍无接口，因此只能显示「— / 未开通」，绝不显示假订单号、假金额、
 * 假地址、假等级。
 *
 * 订单面板的两条硬规则与 /orders 整页完全一致（同一套后端契约，不能各写一套）：
 *   1) **按钮可见性只用后端 canPay / canCancel / canReceive / canRefund**，前端不重算状态机；
 *   2) **状态文字一律用后端 orderStatusLabel**，前端不自建「状态码 -> 中文」映射。
 *
 * 2026-10-27 改造要点：
 *   · 头像可改（本机上传 / 8 张预设 / 恢复默认），走 BFF → ck-shop 真写库；
 *   · 全站不再展示系统生成的 username（u_xxxxxxxx）；
 *   · 「个人信息」与「账户设置」合并成「账户资料」一个分区：
 *     两页展示的是同一份数据（一个只读、一个可编辑），分开摆只会让导航更长、
 *     内容更薄、页面更空。合并后 4 个分区：账户资料 / 我的订单 / 我的收藏 / 收货地址。
 *
 * 2026-10-27（第二轮）改造要点：
 *   · 排版从「四段通栏堆叠」改成 **左栏（身份 + 导航）+ 右栏（数据 + 内容）** 的账户中心结构，
 *     见模板顶部注释：通栏布局在 1760px 内容宽度下会被拉成横条，表单被撑到 700px 宽；
 *   · 数据概览卡改成「大号数字 + 图标 + 语义名」的可点卡片（订单 / 收藏直达对应分区），
 *     未开通的两项保持「— / 未开通」但不做成噪音角标；
 *   · 删掉「点头像即可更换」「上传自己的照片，或从预设插画里挑一个」这类**解释交互的提示文字**。
 *
 * 2026-10-27（第三轮，本次）改造要点：
 *   · **删掉视觉页头**（「个人中心 / 管理您的账户，查看订单信息」），只留 sr-only 的 h1；
 *   · **资料默认只读**：点「编辑资料」（资料卡按钮或分区表头按钮）才进入编辑模式，
 *     保存成功 / 取消都退回只读；密码表单也改成点开才出现；
 *   · **头像入口只保留一个**：资料卡大头像；面板里不再有「更换头像」；
 *   · **资料里补信息**：账户概览（订单 / 收藏 / 收货地址，可点直达）+ 默认收货地址；
 *     顶部那排通栏数据卡随之删除（同样的数字不在同一屏重复两次）；
 *   · 邮箱输入框不再有特殊底色；不再展示注册时间。
 */

const { t } = useI18n()
const { userInfo, isLoggedIn, logout } = useAuth()
const localePath = useLocalePath()
const route = useRoute()

/** 头像加载失败时回退到首字母/图标占位（外链头像会 403/被下线） */
const avatarFailed = ref(false)
watch(() => userInfo.value?.avatar, () => { avatarFailed.value = false })

/** 「更换头像」弹层开关（唯一入口：资料卡上的大头像） */
const avatarDialogOpen = ref(false)

/**
 * 「账户资料」是否处于编辑模式。
 *
 * 状态放在页面而不是面板里，因为入口有两个（资料卡「编辑资料」、分区表头「编辑资料」），
 * 而"保存成功 / 取消"的出口在面板里 —— 单向上/下发的 v-model 比两个组件各存一份更难出错：
 *   · 进分区一律**只读**（切换分区会重置，见下面的 watch）；
 *   · 只有显式点「编辑资料」才置 true。
 */
const editingProfile = ref(false)

function startEditProfile() {
  selectTab('settings')
  editingProfile.value = true
  /*
    滚动只发生在窄屏（<1024）：
      · 宽屏左栏是吸顶的，资料卡与表单本来就在视野里，再滚一下只会把内容卡表头滚到固定导航后面
        （实测 scrollIntoView 会把面板顶到 y≈0，被 116px 高的 header 盖住，页面看起来像错位）；
      · 窄屏资料卡在最上面，点完按钮表单在下方，用户会以为"点了没反应"，所以滚到面板位置。
    偏移量用 --nav-total-height 手动算，避免面板被固定导航压住。
  */
  void nextTick(() => {
    if (!import.meta.client) return
    if (window.innerWidth >= 1024) return
    /*
      等面板的 fade 切换完成再量位置：只读视图（概览 + 资料 + 地址 + 密码）比编辑表单高，
      `mode="out-in"` 的切换期间量的还是旧高度，算出来的目标位置会偏，
      结果就是卡片标题被顶到固定导航后面。
    */
    window.setTimeout(() => {
      const panel = document.getElementById('profile-panel-settings')
      if (!panel) return
      // 滚到**内容卡**（而不是面板本身）：这样"账户资料 + 说明 + 表单"整块都在视野里，
      // 只滚面板会把卡片标题和第一个字段的标签顶出屏幕。
      const card = panel.closest('section') ?? panel
      /*
        偏移量取 html 的 scroll-padding-top（= --nav-total-height + 16px，@theme 里定义好的）：
        它已经由浏览器解析成 px（移动端 144px / 桌面 132px）。
        ⚠️ 不要直接 parseFloat(--nav-total-height)：移动端它是 `calc(64px + 64px)` 字符串，
        parseFloat 得到 64，会比真实导航（128px）少一半，卡片标题就被压在导航下面。
      */
      const scrollPad = Number.parseFloat(getComputedStyle(document.documentElement).scrollPaddingTop) || 144
      const top = window.scrollY + card.getBoundingClientRect().top - scrollPad
      const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
      window.scrollTo({ top: Math.max(top, 0), behavior: reduceMotion ? 'auto' : 'smooth' })
    }, 360)
  })
}

/**
 * 「收货地址」面板实例。
 *
 * 让页面能调面板内部的 openCreate：入口按钮渲染在分区表头右侧（和标题同基线），
 * 但弹层状态、表单字段、提交逻辑仍然全部留在面板里 —— 页面只决定"按钮放在哪"。
 * 面板通过 defineExpose({ openCreate }) 把这个方法暴露出来。
 */
const addressPanelRef = ref<{ openCreate: () => void } | null>(null)

function openAddressCreate() {
  addressPanelRef.value?.openCreate()
}

/**
 * 分区。
 *
 * 'info'（旧「个人信息」）已合并进 'settings'，见下面 resolveTabFromQuery 的兼容处理：
 * 老链接 / 老书签里带 ?tab=info 的仍然能进来，只是落到「账户资料」。
 * desc 是该分区的说明行（渲染在右栏内容卡的表头里），不是操作提示。
 */
type TabKey = 'settings' | 'orders' | 'favorites' | 'addresses'

const tabs: { key: TabKey; label: string; desc: string; icon: string }[] = [
  { key: 'settings', label: 'shop.profileAccount', desc: 'profile.tabDescSettings', icon: 'lucide:user-round' },
  { key: 'orders', label: 'shop.profileMyOrders', desc: 'profile.tabDescOrders', icon: 'lucide:package' },
  { key: 'favorites', label: 'shop.profileMyFavorites', desc: 'profile.tabDescFavorites', icon: 'lucide:heart' },
  { key: 'addresses', label: 'shop.profileAddresses', desc: 'profile.tabDescAddresses', icon: 'lucide:map-pin' },
]

// 默认落在「账户资料」：一进来就能看到并能改自己的资料/头像。
//
// ★ 分区必须能**通过 URL 指定**（?tab=orders / favorites / addresses / settings），
//   否则会出这个真实缺陷：Header 下拉菜单里的「个人中心 / 我的订单 / 收藏夹」三个入口
//   都指向 /profile，而本页永远从第一个分区开始 —— 用户点「我的订单」也只会停在账户资料，
//   看起来像"点了没反应/跳错了"。让 URL 成为分区的唯一真相来源后，
//   这些入口只要带上 ?tab=xxx 就能直达对应面板，且刷新/分享链接都能保持在同一面板。
const TAB_KEYS: TabKey[] = ['settings', 'orders', 'favorites', 'addresses']

/** 旧标签名 -> 新标签名（'info' 已合并进 'settings'，这里保证老链接不落空） */
const LEGACY_TAB_ALIAS: Record<string, TabKey> = { info: 'settings', profile: 'settings' }

/** 把 URL 上的 ?tab= 解析成合法标签；非法/缺失一律回退 settings（不做 404，也不留空面板） */
function resolveTabFromQuery(value: unknown): TabKey {
  const raw = String(Array.isArray(value) ? value[0] : (value ?? '')).trim()
  const aliased = LEGACY_TAB_ALIAS[raw] ?? raw
  return (TAB_KEYS as string[]).includes(aliased) ? (aliased as TabKey) : 'settings'
}

const activeTab = ref<TabKey>(resolveTabFromQuery(route.query.tab))

/**
 * 切换分区时把 URL 同步过去（replace 而不是 push）：
 *   - replace 不往历史里塞记录，用户点几次分区后按浏览器「返回」不会被标签页困住；
 *   - 同时又让当前面板**可刷新、可复制链接**（这正是 Header 入口依赖的能力）。
 * 只在 query 真的变了才跳，避免无意义的重复导航。
 */
function selectTab(key: TabKey) {
  activeTab.value = key
  // 换分区一律退回只读：编辑态是"这一屏的临时状态"，不该跟着用户走到别的分区再带回来
  // （startEditProfile 会在 selectTab 之后立刻置 true，所以"点编辑资料"不会被这行清掉）
  editingProfile.value = false
  const current = resolveTabFromQuery(route.query.tab)
  if (current === key) return
  void navigateTo(
    localePath({ path: '/profile', query: key === 'settings' ? {} : { tab: key } }),
    { replace: true },
  )
}

/**
 * 导航项配色。
 *
 * 宽屏是「白底卡片里的竖排行」：选中项浅珊瑚底 + 深珊瑚字 + 细描边，
 * 未选中项透明、hover 浅底 —— 与全站 .chip 的选中语义一致。
 * 窄屏没有卡片底，退回药丸：选中实心 CTA 深珊瑚（白字满足 4.5:1），未选中白底。
 */
function tabItemClass(key: TabKey) {
  if (activeTab.value === key) {
    return 'bg-[var(--color-brand-cta)] text-white lg:bg-[var(--color-brand-50)] lg:text-[var(--color-brand-700)] lg:ring-1 lg:ring-[var(--color-brand-200)]'
  }
  return 'bg-[var(--color-surface)] text-[var(--color-ink-soft)] hover:text-[var(--color-brand-600)] lg:bg-transparent lg:hover:bg-[var(--color-line-soft)]'
}

// 浏览器前进/后退时 query 变了，面板要跟着变（否则后退了但界面不动）
watch(() => route.query.tab, (value) => {
  const next = resolveTabFromQuery(value)
  if (next !== activeTab.value) {
    activeTab.value = next
    // 后退/前进回到别的分区时同样退回只读（避免带着上一次的编辑态）
    editingProfile.value = false
  }
})

const activeTabMeta = computed(
  () => tabs.find(tab => tab.key === activeTab.value) ?? tabs[0]!,
)

// ---------------------------------------------------------------------------
// 我的收藏（真实数据）
// ---------------------------------------------------------------------------

// 页面只保留「账户概览要用的 total」和「登录后预加载的 load」；
// 列表渲染与取消收藏已搬进 components/profile/FavoritesPanel.vue ——
// 它调用同一个 composable（内部是 useState），拿到的是同一份数据，
// 所以在面板里取消收藏后，账户概览里的收藏数会一起更新。
const {
  total: favoriteTotal,
  loaded: favoritesLoaded,
  load: loadFavorites,
} = useFavoriteList()

/**
 * 收藏数据在**登录态恢复后立刻拉一次**（不是等用户切到收藏分区）：
 * 账户概览里的「收藏数」要立即显示真实数量，否则用户会看到"收藏数 0"却明明收藏过商品。
 *   - 未登录不打接口（未登录时走的是登录引导，不该产生必然 401 的请求）；
 *   - 已经加载过就不再重复拉（取消收藏是本地移除那一行，不需要整页重拉）；
 *   - **只在客户端跑**：登录态已在 SSR 播种，`immediate: true` 在服务端也会命中，
 *     会在 SSR setup 里多发一次依赖 cookie 的请求（hydration 后还会再来一次）。
 */
watch(isLoggedIn, () => {
  if (!import.meta.client) return
  if (isLoggedIn.value && !favoritesLoaded.value) {
    void loadFavorites()
  }
}, { immediate: true })

// ---------------------------------------------------------------------------
// 我的订单（真实数据，字段口径与 /orders 页一致）
// ---------------------------------------------------------------------------

// 订单状态与加载逻辑已搬进 composables/useOrderList.ts：
// 页面与 components/profile/OrdersPanel.vue 调用它拿到的是同一份 useState 状态，
// 所以面板里翻页 / 确认收货后刷新，账户概览里的订单数会同步更新。
const { total: orderTotal, load: loadOrders } = useOrderList()

// ---------------------------------------------------------------------------
// 收货地址（真实数据 + 完整增删改 / 设为默认，字段口径与 /address 页一致）
// ---------------------------------------------------------------------------

// 列表渲染、增删改与两个弹层已搬进 components/profile/AddressesPanel.vue，
// 数据与接口在 composables/useAddressBook.ts（useState 共享，页面与面板同一份数据）。
// 页面只保留「登录后预加载一次」用的 load —— 让用户切到该面板前数据就绪；
// 另外用 addressItems.length 决定表头要不要出「新增地址」：
// 一条都没有时，空态里已经有一个居中主按钮，表头再放一个就是同屏两个同义主按钮。
const { addresses: addressItems, load: loadAddresses } = useAddressBook()

// 注意：「订单/地址/账户资料首次登录后各拉一次」的那个 immediate watch 放在本文件
// setup 的**末尾**（见 loadProfile 定义之后）—— immediate watch 的回调会在 setup
// 期间**同步**执行，如果它被放在 profileLoading / ordersLoading 等 ref 声明之前，
// 回调里访问这些 const 会直接抛
//   ReferenceError: Cannot access 'profileLoading' before initialization
// 而 SSR 下这个异常会让整页 500。这类"顺序依赖"是巨型单文件组件最难查的坑之一，
// 所以：任何 immediate watch 都必须写在它所用到的所有声明之后。

// 展示派生字段（昵称 / 头像首字母 / 联系方式）
// 都在 composables/useProfileFields.ts —— 资料卡与账户资料面板共用同一套口径。
// 注意：**不再取 memberSinceText**（注册时间按要求不再展示，见需求 7）。
const { displayName, avatarInitial } = useProfileFields()

// 账户资料（是否有密码 / 加载状态）与密码表单见：
//   composables/useProfileAccount.ts + components/profile/SettingsPanel.vue
// （hasPassword 的判据说明也随实现移到了 composable 里，避免两处解释不一致）
const { load: loadProfile } = useProfileAccount()

/**
 * 订单、地址与账户资料都在**登录态恢复后立刻各拉一次**（不是等用户切到对应分区）：
 * 账户概览里的订单数 / 地址数、默认收货地址摘要都要立即是真的，
 * 否则用户会看到 0 条地址却明明存过地址、或看到"还没有默认收货地址"。
 *   - 未登录不打接口（未登录时本页整页是登录引导，不该产生必然 401 的请求）；
 *   - 三个接口都只在登录态**首次**变为 true 时拉一次，避免重试/翻页后被 watch 冲掉。
 *
 * ⚠️ 位置要求：`immediate: true` 的回调会在 setup 期间**同步**执行，所以本段必须放在
 * useOrderList / useAddressBook / useProfileAccount 三个数据源声明**之后**。
 * （历史上这里踩过一次：watch 写在 profileLoading 声明之前，回调同步访问该 ref 直接抛
 *   ReferenceError: Cannot access 'profileLoading' before initialization，
 *   SSR 下表现为 /profile 整页 500。）
 */
const privateDataLoaded = ref(false)

watch(isLoggedIn, () => {
  // SSR 已播种登录态：immediate 在服务端也会命中，这 3 个 load 依赖浏览器 cookie 语义/客户端
  // 交互状态，只应在客户端首次进页面时跑一次（否则 SSR 多发 3 个请求，且部分会 401）
  if (!import.meta.client) return
  if (!isLoggedIn.value || privateDataLoaded.value) return
  privateDataLoaded.value = true
  void loadOrders()
  void loadAddresses()
  void loadProfile()
}, { immediate: true })


/**
 * WAI-ARIA tabs 键盘交互。
 *
 * 同一个 tablist 在宽屏是竖排、窄屏是横排，所以四个方向键都接受：
 * ← / ↑ 上一个，→ / ↓ 下一个（首尾循环），Home / End 跳首尾，并把焦点移到新选中的项。
 */
function handleTabKeydown(event: KeyboardEvent, index: number) {
  const last = tabs.length - 1
  let next = index
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') next = index === last ? 0 : index + 1
  else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') next = index === 0 ? last : index - 1
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = last
  else return

  event.preventDefault()
  const target = tabs[next]
  if (!target) return
  // 走 selectTab 而不是直接赋值：键盘切换同样要把 ?tab= 同步到 URL
  selectTab(target.key)
  if (import.meta.client) {
    document.getElementById(`profile-tab-${target.key}`)?.focus()
  }
}

async function handleLogout() {
  await logout()
  await navigateTo(localePath('/login'))
}

useSeoMeta({
  title: () => t('profile.title'),
  // 个人中心是私有页面（昵称/邮箱/手机号），不允许被搜索引擎收录
  robots: 'noindex, nofollow',
})
</script>
