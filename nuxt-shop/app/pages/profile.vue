<template>
  <div class="min-h-screen bg-[var(--color-cream)] pt-navbar">
    <!-- 页头 -->
    <div class="mx-auto max-w-7xl px-2 sm:px-3 lg:px-4">
      <div class="mb-6 sm:mb-8">
        <span class="section-eyebrow inline-flex items-center gap-2">
          <UIcon name="lucide:user-round" class="h-4 w-4" aria-hidden="true" />
          {{ t('profile.title') }}
        </span>
        <h1 class="mt-3 text-3xl font-black tracking-tight text-[var(--color-ink)] sm:text-4xl">
          {{ t('profile.title') }}
        </h1>
        <p class="mt-2 text-sm text-[var(--color-muted)]">
          {{ t('profile.subtitle') }}
        </p>
      </div>
    </div>

    <div class="mx-auto max-w-7xl px-2 pb-16 sm:px-3 lg:px-4">
      <!-- 未登录态 -->
      <template v-if="!isLoggedIn">
        <div class="mx-auto max-w-lg">
          <div class="ui-card px-6 py-10 text-center sm:px-10">
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
              <!--
                /register 现在是**真实存在**的页面（app/pages/register.vue，契约 4.2）。
                这里曾经把「注册账号」也指向 /login 并附了一段"站点没有注册页"的注释 ——
                那是当时的权宜之计（宁可两个入口都落到登录页，也不给用户一个 404 的假入口）。
                注册页已落地，这个 404 顾虑消失，两个入口各自直达即可。
                （Header.vue 的「登录 / 注册」同批做了同样的拆分。）
              -->
              <NuxtLinkLocale to="/register" class="ui-pill ui-pill-ghost h-11 px-7 text-sm">
                <UIcon name="lucide:user-plus" class="h-4 w-4" aria-hidden="true" />
                {{ t('profile.register') }}
              </NuxtLinkLocale>
            </div>
          </div>
        </div>
      </template>

      <!-- 已登录态：lg 及以上 = 左 1 栏资料卡 + 右 3 栏（统计 + 内容面板），以下上下堆叠 -->
      <template v-else>
        <div class="grid gap-6 lg:grid-cols-4 lg:gap-8">
          <!-- ===== 左：资料卡（全部字段都来自 userInfo，没有的就不渲染） ===== -->
          <div class="lg:col-span-1">
            <div class="ui-card overflow-hidden">
              <div class="brand-gradient relative p-6 text-white">
                <div class="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
                  <span class="absolute -right-8 -top-10 h-28 w-28 rounded-full bg-white/20 blur-2xl"></span>
                  <span class="absolute -bottom-12 -left-8 h-28 w-28 rounded-full bg-[var(--color-accent-400)]/25 blur-2xl"></span>
                </div>

                <div class="relative flex items-start gap-4">
                  <span class="h-16 w-16 shrink-0 overflow-hidden rounded-full border-2 border-white/40 bg-white/20">
                    <img
                      v-if="userInfo?.avatar"
                      :src="userInfo.avatar"
                      :alt="displayName"
                      width="64"
                      height="64"
                      class="h-full w-full object-cover"
                      loading="lazy"
                    >
                    <span
                      v-else-if="avatarInitial"
                      class="flex h-full w-full items-center justify-center text-2xl font-black"
                      aria-hidden="true"
                    >
                      {{ avatarInitial }}
                    </span>
                    <span v-else class="flex h-full w-full items-center justify-center">
                      <UIcon name="lucide:user" class="h-7 w-7" aria-hidden="true" />
                    </span>
                  </span>

                  <div class="min-w-0 flex-1">
                    <h2 class="truncate text-lg font-black">
                      {{ displayName || t('profile.title') }}
                    </h2>
                    <p v-if="userInfo?.username" class="truncate text-sm text-white/75">
                      @{{ userInfo.username }}
                    </p>
                    <!--
                      这里原本是写死的 "Gold Member" 徽章。后端没有任何会员等级字段，
                      改为只显示真实的注册时间（userInfo.createTime 来自后端 SysUser），
                      取不到就整块不渲染 —— 绝不编造会员等级或日期。
                    -->
                    <p
                      v-if="memberSinceText"
                      class="mt-2 inline-flex max-w-full items-center gap-1.5 rounded-full bg-white/15 px-3 py-1 text-[11px] font-bold"
                    >
                      <UIcon name="lucide:calendar" class="h-3 w-3 shrink-0" aria-hidden="true" />
                      <span class="truncate">{{ t('profile.memberSince') }} · {{ memberSinceText }}</span>
                    </p>
                  </div>
                </div>

                <button
                  type="button"
                  class="relative mt-5 flex h-10 w-full cursor-pointer items-center justify-center gap-2 rounded-full border border-white/40 bg-white/10 text-[13px] font-bold text-white transition-colors hover:bg-white/20 motion-reduce:transition-none"
                  @click="selectTab('settings')"
                >
                  <UIcon name="lucide:pencil" class="h-3.5 w-3.5" aria-hidden="true" />
                  {{ t('profile.editProfile') }}
                </button>
              </div>

              <!-- 邮箱 / 手机号：只在后端返回了对应字段时才渲染 -->
              <dl v-if="contactRows.length" class="space-y-3.5 p-5">
                <div v-for="row in contactRows" :key="row.label" class="min-w-0">
                  <dt class="flex items-center gap-2 text-[11px] font-bold uppercase tracking-wider text-[var(--color-muted)]">
                    <UIcon :name="row.icon" class="h-3.5 w-3.5 shrink-0 text-[var(--color-brand-500)]" aria-hidden="true" />
                    {{ t(row.label) }}
                  </dt>
                  <dd class="mt-0.5 truncate text-[13px] font-medium text-[var(--color-ink)]">
                    {{ row.value }}
                  </dd>
                </div>
              </dl>

              <div class="border-t border-[var(--color-line-soft)] p-4">
                <button
                  type="button"
                  class="flex w-full cursor-pointer items-center justify-center gap-2 rounded-full border border-[var(--color-line)] px-4 py-2.5 text-sm font-bold text-[var(--color-error-600)] transition-colors hover:bg-[var(--color-error-50)] motion-reduce:transition-none"
                  @click="handleLogout"
                >
                  <UIcon name="lucide:log-out" class="h-4 w-4" aria-hidden="true" />
                  {{ t('profile.logout') }}
                </button>
              </div>
            </div>
          </div>

          <!-- ===== 右 3 栏：统计 + 标签页 ===== -->
          <div class="lg:col-span-3">
            <!--
              统计卡：**收藏数已接通真实接口**（GET /api/favorite 的 total）；
              订单 / 优惠券 / 积分后端仍没有对应接口（订单模块有接口，但本页尚未接入），
              所以这三项一律显示真实的 0，绝不用 12 / 28 / 5 / 1250 这种编造数字。
            -->
            <div class="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
              <div v-for="stat in stats" :key="stat.label" class="ui-card flex items-center gap-3 p-4">
                <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-[12px]" :class="stat.iconClass">
                  <UIcon :name="stat.icon" class="h-5 w-5" aria-hidden="true" />
                </span>
                <span class="min-w-0">
                  <span class="tabular block text-2xl font-black leading-none text-[var(--color-ink)]">
                    {{ stat.value }}
                  </span>
                  <span class="mt-1 block truncate text-xs text-[var(--color-muted)]">
                    {{ t(stat.label) }}
                  </span>
                </span>
              </div>
            </div>

            <!-- 标签页（WAI-ARIA tabs 模式：左右方向键 / Home / End 切换） -->
            <div class="no-scrollbar mt-6 flex gap-2 overflow-x-auto" role="tablist" :aria-label="t('profile.title')">
              <button
                v-for="(tab, index) in tabs"
                :id="`profile-tab-${tab.key}`"
                :key="tab.key"
                type="button"
                role="tab"
                :aria-selected="activeTab === tab.key"
                :aria-controls="`profile-panel-${tab.key}`"
                :tabindex="activeTab === tab.key ? 0 : -1"
                class="chip shrink-0"
                :class="activeTab === tab.key ? 'chip-active' : ''"
                @click="selectTab(tab.key)"
                @keydown="handleTabKeydown($event, index)"
              >
                <UIcon :name="tab.icon" class="h-4 w-4" aria-hidden="true" />
                {{ t(tab.label) }}
              </button>
            </div>

            <div
              :id="`profile-panel-${activeTab}`"
              role="tabpanel"
              :aria-labelledby="`profile-tab-${activeTab}`"
              class="ui-card mt-4 p-5 sm:p-7"
            >
              <h2 class="text-lg font-black text-[var(--color-ink)]">
                {{ t(activeTabMeta.label) }}
              </h2>

              <!-- 个人信息：只列真实存在的字段 -->
              <template v-if="activeTab === 'info'">
                <dl v-if="infoRows.length" class="mt-5 grid gap-3 sm:grid-cols-2">
                  <div
                    v-for="row in infoRows"
                    :key="row.label"
                    class="min-w-0 rounded-[var(--radius-card-sm)] bg-[var(--color-line-soft)] px-4 py-3"
                  >
                    <dt class="flex items-center gap-2 text-[11px] font-bold uppercase tracking-wider text-[var(--color-muted)]">
                      <UIcon :name="row.icon" class="h-3.5 w-3.5 shrink-0 text-[var(--color-brand-500)]" aria-hidden="true" />
                      {{ t(row.label) }}
                    </dt>
                    <dd class="mt-1 truncate text-sm font-medium text-[var(--color-ink)]">
                      {{ row.value }}
                    </dd>
                  </div>
                </dl>
                <p v-else class="mt-5 text-sm text-[var(--color-muted)]">
                  {{ t('shop.noData') }}
                </p>
              </template>

              <!-- 账户设置：真实姓名 / 邮箱 / 手机号可编辑，用户名只读 -->
              <template v-else-if="activeTab === 'settings'">
                <form class="mt-5 grid gap-4 sm:grid-cols-2" @submit.prevent="handleSave">
                  <div>
                    <label for="profile-realname" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
                      {{ t('profile.realname') }}
                    </label>
                    <input
                      id="profile-realname"
                      v-model="form.realname"
                      type="text"
                      autocomplete="name"
                      class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] transition-colors focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
                      @input="markTouched"
                    >
                  </div>

                  <div>
                    <label for="profile-username" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
                      {{ t('profile.username') }}
                    </label>
                    <input
                      id="profile-username"
                      :value="userInfo?.username || ''"
                      type="text"
                      disabled
                      aria-describedby="profile-username-hint"
                      class="h-11 w-full cursor-not-allowed rounded-[var(--radius-input)] border border-[var(--color-line)] bg-[var(--color-line-soft)] px-4 text-sm text-[var(--color-muted)]"
                    >
                    <!--
                      用户名规则（契约 0 节决策 7）：u_ + 8 位小写字母数字，系统生成，**永不可改**。
                      必须把"为什么灰着"讲出来 —— 只给一个 disabled 的框，用户会以为是页面坏了
                      或者以为自己该去别处改。
                    -->
                    <p id="profile-username-hint" class="mt-1.5 flex items-start gap-1.5 text-[12px] leading-relaxed text-[var(--color-muted)]">
                      <UIcon name="lucide:lock" class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                      <span>{{ t('auth.usernameReadonlyHint') }}</span>
                    </p>
                  </div>

                  <div>
                    <label for="profile-email" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
                      {{ t('profile.email') }}
                    </label>
                    <input
                      id="profile-email"
                      v-model="form.email"
                      type="email"
                      autocomplete="email"
                      class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] transition-colors focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
                      @input="markTouched"
                    >
                  </div>

                  <div>
                    <label for="profile-phone" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
                      {{ t('profile.phone') }}
                    </label>
                    <input
                      id="profile-phone"
                      v-model="form.phone"
                      type="tel"
                      autocomplete="tel"
                      class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] transition-colors focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
                      @input="markTouched"
                    >
                  </div>

                  <div class="sm:col-span-2">
                    <button
                      type="submit"
                      class="ui-pill ui-pill-brand h-11 px-6 text-sm disabled:cursor-not-allowed disabled:opacity-45"
                      :disabled="!dirty"
                    >
                      <UIcon name="lucide:save" class="h-4 w-4" aria-hidden="true" />
                      {{ t('profile.saveChanges') }}
                    </button>
                  </div>
                </form>

                <!--
                  ===== 账户密码：设置 / 修改 =====
                  不新增 Tab，内嵌在「账户设置」里（Lead 已裁决）：
                  「账户设置」在语义上本来就应该同时管资料与密码，
                  再开一个 Tab 只会挤压移动端本就不宽裕的横向 tab 栏。

                  标题由 hasPassword 决定（契约 2.8 / 5.5.2）：
                    false -> 「设置密码」（邮箱验证码 / Google 自动注册的用户，没有旧密码）
                    true  -> 「修改密码」（必须先验旧密码）
                    null  -> 资料还没拉到 / 接口失败：整块不渲染，
                             避免在"不知道用户有没有密码"的情况下给出错误的表单。
                -->
                <section class="mt-7 border-t border-[var(--color-line-soft)] pt-6">
                  <h3 class="flex items-center gap-2 text-[15px] font-black text-[var(--color-ink)]">
                    <UIcon name="lucide:key-round" class="h-4 w-4 text-[var(--color-brand-500)]" aria-hidden="true" />
                    {{ t('profile.passwordSection') }}
                  </h3>
                  <p class="mt-1.5 text-[12px] leading-relaxed text-[var(--color-muted)]">
                    {{ t('profile.passwordSectionDesc') }}
                  </p>

                  <!-- 资料加载失败：把后端/网络的原话展示出来，并给一个重试入口 -->
                  <div
                    v-if="profileError"
                    class="mt-4 flex items-start gap-2 rounded-[var(--radius-card-sm)] bg-[var(--color-error-50)] px-4 py-3 text-[13px] font-medium text-[var(--color-error-600)]"
                    role="alert"
                  >
                    <UIcon name="lucide:circle-alert" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                    <span class="min-w-0 flex-1">
                      {{ t('profile.profileLoadFailed') }}：{{ profileError }}
                      <button
                        type="button"
                        class="ml-2 cursor-pointer font-bold underline underline-offset-2 disabled:opacity-45"
                        :disabled="profileLoading"
                        @click="loadProfile()"
                      >
                        {{ t('common.retry') }}
                      </button>
                    </span>
                  </div>

                  <!-- 加载中：骨架屏（不猜字段数，固定两行） -->
                  <div v-else-if="profileLoading && hasPassword === null" class="mt-4 space-y-3" aria-busy="true">
                    <span class="block h-11 w-full animate-pulse rounded-[var(--radius-input)] bg-[var(--color-line-soft)]"></span>
                    <span class="block h-11 w-2/3 animate-pulse rounded-[var(--radius-input)] bg-[var(--color-line-soft)]"></span>
                  </div>

                  <form v-else-if="hasPassword !== null" class="mt-4 grid gap-4 sm:grid-cols-2" @submit.prevent="handlePasswordSubmit">
                    <!-- 旧密码：只在用户**确实已有密码**时渲染。
                         没设过密码的人填不出旧密码，给一个必填框就是纯阻塞。 -->
                    <div v-if="hasPassword" class="sm:col-span-2">
                      <label for="profile-old-password" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
                        {{ t('auth.oldPassword') }}
                      </label>
                      <input
                        id="profile-old-password"
                        v-model="passwordForm.oldPassword"
                        type="password"
                        autocomplete="current-password"
                        class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] transition-colors focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
                        @input="markPasswordTouched"
                      >
                    </div>

                    <div>
                      <label for="profile-new-password" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
                        {{ t('auth.password') }}
                      </label>
                      <div class="relative">
                        <input
                          id="profile-new-password"
                          v-model="passwordForm.newPassword"
                          :type="showNewPassword ? 'text' : 'password'"
                          autocomplete="new-password"
                          :aria-invalid="passwordMismatch"
                          aria-describedby="profile-password-strength"
                          class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white pl-4 pr-11 text-sm text-[var(--color-ink)] transition-colors focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)] motion-reduce:transition-none"
                          @input="markPasswordTouched"
                        >
                        <!-- 密码可见性切换：aria-label 随状态变化，不用 emoji -->
                        <button
                          type="button"
                          class="absolute inset-y-0 right-0 flex w-11 cursor-pointer items-center justify-center text-[var(--color-muted)] transition-colors hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
                          :aria-label="showNewPassword ? t('auth.hidePassword') : t('auth.showPassword')"
                          :aria-pressed="showNewPassword"
                          @click="showNewPassword = !showNewPassword"
                        >
                          <UIcon :name="showNewPassword ? 'lucide:eye-off' : 'lucide:eye'" class="h-4 w-4" aria-hidden="true" />
                        </button>
                      </div>

                      <!-- 密码强度：轻量三段提示条，只提示不阻断 -->
                      <div v-if="passwordStrength" id="profile-password-strength" class="mt-2" aria-live="polite">
                        <span class="block h-1 w-full overflow-hidden rounded-full bg-[var(--color-line-soft)]">
                          <span
                            class="block h-full rounded-full transition-[width] duration-200 motion-reduce:transition-none"
                            :class="passwordStrengthBarClass"
                          ></span>
                        </span>
                        <!-- 文案随强度变化：只挂 passwordTooWeak 会在"强"的时候也说密码太弱 -->
                        <span class="mt-1 block text-[11px] text-[var(--color-muted)]">
                          {{ passwordStrengthText }}
                        </span>
                      </div>
                    </div>

                    <div>
                      <label for="profile-confirm-password" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
                        {{ t('auth.confirmPassword') }}
                      </label>
                      <div class="relative">
                        <input
                          id="profile-confirm-password"
                          v-model="passwordForm.confirmPassword"
                          :type="showConfirmPassword ? 'text' : 'password'"
                          autocomplete="new-password"
                          :aria-invalid="passwordMismatch"
                          :aria-describedby="passwordMismatch ? 'profile-confirm-password-error' : undefined"
                          class="h-11 w-full rounded-[var(--radius-input)] border bg-white pl-4 pr-11 text-sm text-[var(--color-ink)] transition-colors focus:outline-none focus:ring-2 motion-reduce:transition-none"
                          :class="passwordMismatch
                            ? 'border-[var(--color-error)] focus:border-[var(--color-error)] focus:ring-[var(--color-error-50)]'
                            : 'border-[var(--color-line)] focus:border-[var(--color-brand-400)] focus:ring-[var(--color-brand-100)]'"
                          @input="markPasswordTouched"
                        >
                        <button
                          type="button"
                          class="absolute inset-y-0 right-0 flex w-11 cursor-pointer items-center justify-center text-[var(--color-muted)] transition-colors hover:text-[var(--color-brand-600)] motion-reduce:transition-none"
                          :aria-label="showConfirmPassword ? t('auth.hidePassword') : t('auth.showPassword')"
                          :aria-pressed="showConfirmPassword"
                          @click="showConfirmPassword = !showConfirmPassword"
                        >
                          <UIcon :name="showConfirmPassword ? 'lucide:eye-off' : 'lucide:eye'" class="h-4 w-4" aria-hidden="true" />
                        </button>
                      </div>
                      <!-- 字段级错误：不只在 toast 里说，输入框下方也要留痕 -->
                      <p
                        v-if="passwordMismatch"
                        id="profile-confirm-password-error"
                        class="mt-1.5 text-[12px] font-medium text-[var(--color-error-600)]"
                        role="alert"
                      >
                        {{ t('auth.passwordMismatch') }}
                      </p>
                    </div>

                    <!-- 标题随 hasPassword 走：这是"设置"还是"修改"由后端状态决定，不由前端猜 -->
                    <div class="flex flex-wrap items-center gap-3 sm:col-span-2">
                      <button
                        type="submit"
                        class="ui-pill ui-pill-brand h-11 px-6 text-sm disabled:cursor-not-allowed disabled:opacity-45"
                        :disabled="!passwordFormValid"
                      >
                        <UIcon
                          :name="passwordSubmitting ? 'lucide:loader-circle' : 'lucide:key-round'"
                          class="h-4 w-4"
                          :class="passwordSubmitting ? 'animate-spin' : ''"
                          aria-hidden="true"
                        />
                        {{ passwordSubmitting
                          ? t('common.loading')
                          : (hasPassword ? t('auth.changePassword') : t('auth.setPassword')) }}
                      </button>
                      <button
                        v-if="passwordTouched"
                        type="button"
                        class="cursor-pointer text-[13px] font-medium text-[var(--color-muted)] underline underline-offset-2 transition-colors hover:text-[var(--color-ink-soft)] motion-reduce:transition-none"
                        :disabled="passwordSubmitting"
                        @click="resetPasswordForm"
                      >
                        {{ t('common.cancel') }}
                      </button>
                    </div>
                  </form>
                </section>
              </template>

              <!--
                我的收藏：真实数据（GET /api/favorite，商品信息由后端实时读取，
                已下架/已删除的商品不会出现在这里）。支持在本页直接取消收藏。
              -->
              <ProfileFavoritesPanel v-else-if="activeTab === 'favorites'" />

              <!--
                我的订单：真实数据（GET /api/order）。
                这里刻意做得比 /orders 整页更紧凑（单行摘要 + 缩略图条），
                但状态文字与按钮可见性**完全沿用后端字段**，与 /orders 是同一套口径；
                需要看明细/付款/退款就跳 /orders。
              -->
              <ProfileOrdersPanel v-else-if="activeTab === 'orders'" />

              <!-- 收货地址：真实数据（GET /api/address/list）+ 完整增删改 / 设为默认 -->
              <template v-else-if="activeTab === 'addresses'">
                <div class="mt-4 flex justify-end">
                  <button type="button" class="ui-pill ui-pill-brand h-10 shrink-0 px-5 text-[13px]" @click="openAddressCreate">
                    <UIcon name="lucide:plus" class="h-4 w-4" aria-hidden="true" />
                    {{ t('address.add') }}
                  </button>
                </div>

                <!-- 加载失败 -->
                <div v-if="addressesError" class="mt-4 flex items-start gap-2 rounded-[var(--radius-card-sm)] bg-[var(--color-error-50)] px-4 py-3 text-[13px] font-medium text-[var(--color-error-600)]">
                  <UIcon name="lucide:circle-alert" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                  <span class="min-w-0 flex-1">
                    {{ t('address.loadError') }}：{{ addressesError }}
                    <button
                      type="button"
                      class="ml-2 cursor-pointer font-bold underline underline-offset-2 disabled:opacity-45"
                      :disabled="addressesLoading"
                      @click="loadAddresses()"
                    >
                      {{ t('common.retry') }}
                    </button>
                  </span>
                </div>

                <!-- 加载中：骨架屏 -->
                <ul v-else-if="addressesLoading && !addresses.length" class="mt-4 space-y-3" aria-busy="true" :aria-label="t('common.loading')">
                  <li v-for="index in 3" :key="`addr-skeleton-${index}`" class="space-y-2.5 rounded-[var(--radius-card-sm)] bg-[var(--color-line-soft)]/50 p-4">
                    <span class="block h-4 w-1/4 animate-pulse rounded bg-[var(--color-line-soft)]"></span>
                    <span class="block h-3 w-2/3 animate-pulse rounded bg-[var(--color-line-soft)]"></span>
                  </li>
                </ul>

                <!-- 有地址 -->
                <ul v-else-if="addresses.length" class="mt-4 space-y-3">
                  <li
                    v-for="address in addresses"
                    :key="address.id"
                    class="rounded-[var(--radius-card-sm)] border border-[var(--color-line-soft)] p-4"
                  >
                    <div class="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
                      <div class="min-w-0 flex-1">
                        <p class="flex flex-wrap items-center gap-x-2 gap-y-1">
                          <span class="text-[14px] font-bold text-[var(--color-ink)]">{{ address.receiverName || '—' }}</span>
                          <!-- 手机号是否脱敏由后端决定，前端不加工 -->
                          <span class="text-[13px] tabular text-[var(--color-ink-soft)]">{{ address.receiverPhone || '' }}</span>
                          <span
                            v-if="address.isDefault"
                            class="rounded-full bg-[var(--color-brand-500)] px-2 py-0.5 text-[10px] font-bold text-white"
                          >
                            {{ t('address.defaultTag') }}
                          </span>
                          <span
                            v-if="address.addressTag"
                            class="rounded-full bg-[var(--color-line-soft)] px-2 py-0.5 text-[10px] font-bold text-[var(--color-ink-soft)]"
                          >
                            {{ address.addressTag }}
                          </span>
                        </p>
                        <!-- fullAddress 由后端拼好，前端不再各处拼一遍 -->
                        <p class="mt-1.5 text-[13px] leading-relaxed text-[var(--color-muted)]">
                          {{ address.fullAddress || '—' }}
                        </p>
                        <p v-if="isPositive(address.useCount)" class="mt-1.5 text-[12px] tabular text-[var(--color-muted)]">
                          {{ t('address.useCount', { count: address.useCount }) }}
                        </p>
                      </div>

                      <div class="flex shrink-0 flex-wrap items-center gap-2">
                        <button
                          v-if="!address.isDefault"
                          type="button"
                          class="inline-flex h-9 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-4 text-[12px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-brand-500)] hover:text-[var(--color-brand-600)] disabled:opacity-40 motion-reduce:transition-none"
                          :disabled="addressMutating"
                          @click="setDefaultAddress(address.id)"
                        >
                          {{ t('address.setDefault') }}
                        </button>
                        <button
                          type="button"
                          class="inline-flex h-9 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-4 text-[12px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-brand-500)] hover:text-[var(--color-brand-600)] disabled:opacity-40 motion-reduce:transition-none"
                          :disabled="addressMutating"
                          @click="openAddressEdit(address)"
                        >
                          {{ t('common.edit') }}
                        </button>
                        <button
                          type="button"
                          class="inline-flex h-9 cursor-pointer items-center rounded-full border border-[var(--color-line)] px-4 text-[12px] font-medium text-[var(--color-ink-soft)] transition-colors duration-200 hover:border-[var(--color-error)] hover:text-[var(--color-error)] disabled:opacity-40 motion-reduce:transition-none"
                          :disabled="addressMutating"
                          @click="askDeleteAddress(address)"
                        >
                          {{ t('common.delete') }}
                        </button>
                      </div>
                    </div>
                  </li>
                </ul>

                <!-- 真的没有地址 -->
                <div v-else class="flex flex-col items-center justify-center py-14 text-center">
                  <span class="flex h-20 w-20 items-center justify-center rounded-full bg-[var(--color-brand-50)] text-[var(--color-brand-400)]">
                    <UIcon name="lucide:map-pin-off" class="h-9 w-9" aria-hidden="true" />
                  </span>
                  <p class="mt-5 text-sm font-bold text-[var(--color-ink)]">
                    {{ t('address.empty') }}
                  </p>
                  <p class="mt-2 max-w-md text-[13px] text-[var(--color-muted)]">
                    {{ t('address.emptyDesc') }}
                  </p>
                  <button type="button" class="ui-pill ui-pill-brand mt-5 h-10 px-6 text-[13px]" @click="openAddressCreate">
                    <UIcon name="lucide:plus" class="h-4 w-4" aria-hidden="true" />
                    {{ t('address.add') }}
                  </button>
                </div>
              </template>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 地址新增 / 编辑弹层（字段口径与 /address 页一致，只有这一处定义） -->
    <UModal
      v-model:open="addressFormState.open"
      :title="addressFormState.id ? t('address.editTitle') : t('address.addTitle')"
      :ui="{ content: 'rounded-[var(--radius-card)]' }"
      @update:open="onAddressFormOpenChange"
    >
      <template #body>
        <form class="grid gap-4 sm:grid-cols-2" @submit.prevent="submitAddressForm">
          <div>
            <label for="profile-address-name" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.receiverName') }}
            </label>
            <input
              id="profile-address-name"
              v-model="addressForm.receiverName"
              type="text"
              autocomplete="name"
              :placeholder="t('address.namePlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <div>
            <label for="profile-address-phone" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.receiverPhone') }}
            </label>
            <input
              id="profile-address-phone"
              v-model="addressForm.receiverPhone"
              type="tel"
              autocomplete="tel"
              :placeholder="t('address.phonePlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <div class="grid grid-cols-3 gap-3 sm:col-span-2">
            <input
              v-model="addressForm.province"
              type="text"
              :aria-label="t('address.provincePlaceholder')"
              :placeholder="t('address.provincePlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-3 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
            <input
              v-model="addressForm.city"
              type="text"
              :aria-label="t('address.cityPlaceholder')"
              :placeholder="t('address.cityPlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-3 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
            <input
              v-model="addressForm.district"
              type="text"
              :aria-label="t('address.districtPlaceholder')"
              :placeholder="t('address.districtPlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-3 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <div class="sm:col-span-2">
            <label for="profile-address-detail" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.detailAddress') }}
            </label>
            <input
              id="profile-address-detail"
              v-model="addressForm.detailAddress"
              type="text"
              autocomplete="street-address"
              :placeholder="t('address.detailPlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <div>
            <label for="profile-address-postal" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.postalCode') }}
            </label>
            <input
              id="profile-address-postal"
              v-model="addressForm.postalCode"
              type="text"
              autocomplete="postal-code"
              :placeholder="t('address.postalPlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <div>
            <label for="profile-address-tag" class="mb-1.5 block text-[13px] font-bold text-[var(--color-ink-soft)]">
              {{ t('address.addressTag') }}
            </label>
            <input
              id="profile-address-tag"
              v-model="addressForm.addressTag"
              type="text"
              :placeholder="t('address.tagPlaceholder')"
              class="h-11 w-full rounded-[var(--radius-input)] border border-[var(--color-line)] bg-white px-4 text-sm text-[var(--color-ink)] focus:border-[var(--color-brand-400)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-100)]"
            >
          </div>

          <label class="flex cursor-pointer items-center gap-2.5 sm:col-span-2">
            <input v-model="addressForm.isDefault" type="checkbox" class="h-4 w-4 cursor-pointer accent-[var(--color-brand-500)]">
            <span class="text-[13px] font-medium text-[var(--color-ink-soft)]">{{ t('address.isDefaultLabel') }}</span>
          </label>
        </form>
      </template>

      <template #footer>
        <div class="flex w-full justify-end gap-3">
          <button
            type="button"
            class="cursor-pointer rounded-full border border-[var(--color-line)] px-5 py-2.5 text-sm font-semibold text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-surface-secondary)] motion-reduce:transition-none"
            :disabled="addressFormState.pending"
            @click="addressFormState.open = false"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            type="button"
            class="cursor-pointer rounded-full bg-[var(--color-brand-500)] px-5 py-2.5 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[var(--color-brand-600)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
            :disabled="addressFormState.pending"
            @click="submitAddressForm"
          >
            {{ addressFormState.pending ? t('address.saving') : t('common.save') }}
          </button>
        </div>
      </template>
    </UModal>

    <!-- 删除地址二次确认（删除不可撤销，必须让用户再点一次） -->
    <UModal
      v-model:open="addressDeleteState.open"
      :title="t('address.deleteTitle')"
      :description="t('address.deleteDesc')"
      :ui="{ content: 'rounded-[var(--radius-card)]' }"
    >
      <template #footer>
        <div class="flex w-full justify-end gap-3">
          <button
            type="button"
            class="cursor-pointer rounded-full border border-[var(--color-line)] px-5 py-2.5 text-sm font-semibold text-[var(--color-ink-soft)] transition-colors duration-200 hover:bg-[var(--color-surface-secondary)] motion-reduce:transition-none"
            @click="addressDeleteState.open = false"
          >
            {{ t('common.cancel') }}
          </button>
          <button
            type="button"
            class="cursor-pointer rounded-full bg-[var(--color-error)] px-5 py-2.5 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[var(--color-error-600)] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none"
            :disabled="addressDeleteState.pending"
            @click="confirmDeleteAddress"
          >
            {{ t('address.deleteConfirm') }}
          </button>
        </div>
      </template>
    </UModal>

    <!-- Footer -->
    <SiteFooter />
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
import { isPositive, isUnauthorizedError, readErrorMessage } from '~~/composables/useApiError'
import { formatDate } from '~~/composables/useDateTimeFormat'

/**
 * 个人中心。
 *
 * 本页最重要的一件事：**删掉所有编造的 mock 数据**。
 * 「我的收藏」「我的订单」「收货地址」三个面板都已接通真实接口
 * （GET /api/favorite、GET /api/order、GET /api/address/list）；
 * 优惠券 / 积分后端仍无接口，因此只能显示真实的 0，
 * 绝不显示假订单号、假金额、假地址、假等级。
 *
 * 订单面板的两条硬规则与 /orders 整页完全一致（同一套后端契约，不能各写一套）：
 *   1) **按钮可见性只用后端 canPay / canCancel / canReceive / canRefund**，前端不重算状态机；
 *   2) **状态文字一律用后端 orderStatusLabel**，前端不自建「状态码 -> 中文」映射。
 */

const { t, locale } = useI18n()
const { userInfo, isLoggedIn, logout, changePassword } = useAuth()
const localePath = useLocalePath()
const route = useRoute()
const toast = useToast()

type TabKey = 'info' | 'orders' | 'favorites' | 'addresses' | 'settings'

const tabs: { key: TabKey; label: string; icon: string }[] = [
  { key: 'info', label: 'shop.profilePersonalInfo', icon: 'lucide:user-round' },
  { key: 'orders', label: 'shop.profileMyOrders', icon: 'lucide:package' },
  { key: 'favorites', label: 'shop.profileMyFavorites', icon: 'lucide:heart' },
  { key: 'addresses', label: 'shop.profileAddresses', icon: 'lucide:map-pin' },
  { key: 'settings', label: 'shop.profileSettings', icon: 'lucide:settings' },
]

// 默认落在"个人信息"：一进来就能看到真实资料，比直接看订单/地址空态友好
//
// ★ 但标签页必须能**通过 URL 指定**（?tab=orders / favorites / addresses / settings）。
//   否则会出这个真实缺陷：Header 下拉菜单里的「个人中心 / 我的订单 / 收藏夹」三个入口
//   都指向 /profile，而本页永远从 info 开始 —— 用户点「我的订单」也只会停在「个人信息」，
//   看起来像"点了没反应/跳错了"。让 URL 成为标签页的唯一真相来源后，
//   这些入口只要带上 ?tab=xxx 就能直达对应面板，且刷新/分享链接都能保持在同一面板。
const TAB_KEYS: TabKey[] = ['info', 'orders', 'favorites', 'addresses', 'settings']

/** 把 URL 上的 ?tab= 解析成合法标签；非法/缺失一律回退 info（不做 404，也不留空面板） */
function resolveTabFromQuery(value: unknown): TabKey {
  const raw = String(Array.isArray(value) ? value[0] : (value ?? '')).trim()
  return (TAB_KEYS as string[]).includes(raw) ? (raw as TabKey) : 'info'
}

const activeTab = ref<TabKey>(resolveTabFromQuery(route.query.tab))

/**
 * 切换标签时把 URL 同步过去（replace 而不是 push）：
 *   - replace 不往历史里塞记录，用户点几次标签后按浏览器「返回」不会被标签页困住；
 *   - 同时又让当前面板**可刷新、可复制链接**（这正是 Header 入口依赖的能力）。
 * 只在 query 真的变了才跳，避免无意义的重复导航。
 */
function selectTab(key: TabKey) {
  activeTab.value = key
  const current = resolveTabFromQuery(route.query.tab)
  if (current === key) return
  void navigateTo(
    localePath({ path: '/profile', query: key === 'info' ? {} : { tab: key } }),
    { replace: true },
  )
}

// 浏览器前进/后退时 query 变了，面板要跟着变（否则后退了但界面不动）
watch(() => route.query.tab, (value) => {
  const next = resolveTabFromQuery(value)
  if (next !== activeTab.value) activeTab.value = next
})

const activeTabMeta = computed(
  () => tabs.find(tab => tab.key === activeTab.value)
    ?? { key: 'info' as TabKey, label: 'shop.profilePersonalInfo', icon: 'lucide:user-round' },
)

// ---------------------------------------------------------------------------
// 我的收藏（真实数据）
// ---------------------------------------------------------------------------

// 页面只保留「统计卡要用的 total」和「登录后预加载的 load」；
// 列表渲染与取消收藏已搬进 components/profile/FavoritesPanel.vue ——
// 它调用同一个 composable（内部是 useState），拿到的是同一份数据，
// 所以在面板里取消收藏后，顶部统计卡会一起更新。
const {
  total: favoriteTotal,
  loaded: favoritesLoaded,
  load: loadFavorites,
} = useFavoriteList()

/**
 * 4 张统计卡。
 * 「收藏数」用收藏接口的真实 total，「订单数」用订单接口的真实 total；
 * 优惠券 / 积分后端没有对应接口，只能写常量 0（接口补齐后再接数据）——**绝不用编造的数字填充**。
 */
const stats = computed(() => [
  { label: 'shop.profileOrders', value: orderTotal.value, icon: 'lucide:package', iconClass: 'bg-[var(--color-brand-50)] text-[var(--color-brand-600)]' },
  { label: 'shop.profileFavorites', value: favoriteTotal.value, icon: 'lucide:heart', iconClass: 'bg-[var(--color-error-50)] text-[var(--color-error-600)]' },
  { label: 'shop.profileCoupons', value: 0, icon: 'lucide:ticket', iconClass: 'bg-[var(--color-warning-50)] text-[var(--color-warning-600)]' },
  { label: 'shop.profilePoints', value: 0, icon: 'lucide:gift', iconClass: 'bg-[var(--color-success-50)] text-[var(--color-success-600)]' },
])

/**
 * 收藏数据在**登录态恢复后立刻拉一次**（不是等用户切到收藏标签页）：
 * 顶部「收藏数」统计卡要立即显示真实数量，否则用户会看到"收藏数 0"却明明收藏过商品。
 *   - 未登录不打接口（个人中心未登录时走的是登录引导，不该产生必然 401 的请求）；
 *   - 已经加载过就不再重复拉（取消收藏是本地移除那一行，不需要整页重拉）。
 */
watch(isLoggedIn, () => {
  if (isLoggedIn.value && !favoritesLoaded.value) {
    void loadFavorites()
  }
}, { immediate: true })

// ---------------------------------------------------------------------------
// 我的订单（真实数据，字段口径与 /orders 页一致）
// ---------------------------------------------------------------------------

// 订单状态与加载逻辑已搬进 composables/useOrderList.ts：
// 页面与 components/profile/OrdersPanel.vue 调用它拿到的是同一份 useState 状态，
// 所以面板里翻页 / 确认收货后刷新，顶部「订单数」统计卡会同步更新。
const { total: orderTotal, load: loadOrders } = useOrderList()

// ---------------------------------------------------------------------------
// 收货地址（真实数据 + 完整增删改 / 设为默认，字段口径与 /address 页一致）
// ---------------------------------------------------------------------------

/**
 * isDefault 的三态（后端 AddressDTO 的注释）：null = 不改动当前默认状态，true = 设为默认。
 * 所以提交时"没勾选"必须传 null 而不是 false ——
 * 否则编辑一条非默认地址会把用户真正想保留的默认地址顺手取消掉。
 */
interface AddressVO {
  id: string
  receiverName?: string | null
  receiverPhone?: string | null
  province?: string | null
  city?: string | null
  district?: string | null
  detailAddress?: string | null
  postalCode?: string | null
  addressTag?: string | null
  isDefault?: boolean | null
  masked?: boolean | null
  useCount?: number | null
  fullAddress?: string | null
}

const addresses = ref<AddressVO[]>([])
const addressesLoading = ref(false)
const addressesError = ref('')
/** 设为默认/删除等写操作进行中：统一禁用各行的按钮，防重复提交 */
const addressMutating = ref(false)

const addressFormState = reactive({ open: false, id: '', pending: false })
const addressDeleteState = reactive({ open: false, pending: false, id: '' })

const addressForm = reactive({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  postalCode: '',
  addressTag: '',
  isDefault: false,
})

async function loadAddresses() {
  addressesLoading.value = true
  addressesError.value = ''
  try {
    const list = await $fetch<AddressVO[]>('/api/address/list', { query: { lang: locale.value } })
    // 没有 id 的记录无法编辑/删除，直接过滤掉，避免渲染出点了就报错的按钮
    addresses.value = (Array.isArray(list) ? list : []).filter(item => !!item?.id)
  } catch (error) {
    if (isUnauthorizedError(error)) return
    addressesError.value = readErrorMessage(error, t('address.loadErrorDesc'))
  } finally {
    addressesLoading.value = false
  }
}

function notifyAddressError(error: unknown) {
  toast.add({
    title: t('address.actionFailed'),
    description: readErrorMessage(error, t('address.loadErrorDesc')),
    color: 'error',
    icon: 'lucide:circle-alert',
  })
}

function resetAddressForm() {
  addressForm.receiverName = ''
  addressForm.receiverPhone = ''
  addressForm.province = ''
  addressForm.city = ''
  addressForm.district = ''
  addressForm.detailAddress = ''
  addressForm.postalCode = ''
  addressForm.addressTag = ''
  addressForm.isDefault = false
}

function openAddressCreate() {
  resetAddressForm()
  addressFormState.id = ''
  addressFormState.pending = false
  addressFormState.open = true
}

/** 把一条地址填进表单（新增/编辑共用，字段口径只有这一处） */
function fillAddressForm(address: AddressVO) {
  addressForm.receiverName = address.receiverName ?? ''
  addressForm.receiverPhone = address.receiverPhone ?? ''
  addressForm.province = address.province ?? ''
  addressForm.city = address.city ?? ''
  addressForm.district = address.district ?? ''
  addressForm.detailAddress = address.detailAddress ?? ''
  addressForm.postalCode = address.postalCode ?? ''
  addressForm.addressTag = address.addressTag ?? ''
  // 已经是默认地址时勾上，避免用户以为"编辑一下默认就没了"
  addressForm.isDefault = address.isDefault === true
  addressFormState.id = address.id
}

/**
 * 打开编辑弹层：先用列表数据把表单填上（不阻塞用户），
 * 再拉一次**单条详情**覆盖 —— 用户在别的标签页改过这条地址时，看到的是改后的值。
 * 详情拉不到就继续用列表数据，不打扰用户。
 */
async function openAddressEdit(address: AddressVO) {
  resetAddressForm()
  addressFormState.pending = false
  addressFormState.open = true
  fillAddressForm(address)
  try {
    const fresh = await $fetch<AddressVO>(`/api/address/${encodeURIComponent(address.id)}`, {
      query: { lang: locale.value },
    })
    if (fresh?.id && addressFormState.id === fresh.id) fillAddressForm(fresh)
  } catch {
    // 静默降级：列表数据已经够用，这里不再弹一次错误
  }
}

function onAddressFormOpenChange(open: boolean) {
  addressFormState.open = open
  if (!open) addressFormState.pending = false
}

/** 前端先做一次必填校验，把话说清楚（后端也会校验，这里只是少一次往返） */
function validateAddressForm(): string {
  if (!addressForm.receiverName.trim()) return t('address.receiverRequired')
  if (!addressForm.receiverPhone.trim()) return t('address.phoneRequired')
  if (!addressForm.detailAddress.trim()) return t('address.detailRequired')
  return ''
}

async function submitAddressForm() {
  if (addressFormState.pending) return
  const invalid = validateAddressForm()
  if (invalid) {
    toast.add({ title: invalid, color: 'warning', icon: 'lucide:circle-alert' })
    return
  }

  addressFormState.pending = true
  try {
    const payload = {
      receiverName: addressForm.receiverName.trim(),
      receiverPhone: addressForm.receiverPhone.trim(),
      province: addressForm.province.trim(),
      city: addressForm.city.trim(),
      district: addressForm.district.trim(),
      detailAddress: addressForm.detailAddress.trim(),
      postalCode: addressForm.postalCode.trim(),
      addressTag: addressForm.addressTag.trim(),
      // 勾选 -> true（设为默认）；未勾选 -> null（不改动默认状态，而不是"取消默认"）
      isDefault: addressForm.isDefault ? true : null,
      lang: locale.value,
    }

    if (addressFormState.id) {
      await $fetch('/api/address/edit', { method: 'POST', body: { ...payload, id: addressFormState.id } })
    } else {
      await $fetch('/api/address/add', { method: 'POST', body: payload })
    }

    toast.add({ title: t('address.savedToast'), color: 'success', icon: 'lucide:check' })
    addressFormState.open = false
    await loadAddresses()
  } catch (error) {
    notifyAddressError(error)
  } finally {
    addressFormState.pending = false
  }
}

async function setDefaultAddress(id: string) {
  if (addressMutating.value) return
  addressMutating.value = true
  try {
    // id 走查询参数（后端方法签名没有 @RequestBody）
    await $fetch('/api/address/set-default', {
      method: 'POST',
      query: { id, lang: locale.value },
    })
    toast.add({ title: t('address.defaultSetToast'), color: 'success', icon: 'lucide:check' })
    await loadAddresses()
  } catch (error) {
    notifyAddressError(error)
  } finally {
    addressMutating.value = false
  }
}

function askDeleteAddress(address: AddressVO) {
  addressDeleteState.id = address.id
  addressDeleteState.open = true
}

async function confirmDeleteAddress() {
  if (addressDeleteState.pending || !addressDeleteState.id) return
  addressDeleteState.pending = true
  try {
    await $fetch('/api/address/delete', {
      method: 'POST',
      query: { id: addressDeleteState.id, lang: locale.value },
    })
    toast.add({ title: t('address.deletedToast'), color: 'success', icon: 'lucide:check' })
    addressDeleteState.open = false
    await loadAddresses()
  } catch (error) {
    notifyAddressError(error)
  } finally {
    addressDeleteState.pending = false
  }
}

// 注意：「订单/地址/账户资料首次登录后各拉一次」的那个 immediate watch 放在本文件
// setup 的**末尾**（见 loadProfile 定义之后）—— immediate watch 的回调会在 setup
// 期间**同步**执行，如果它被放在 profileLoading / ordersLoading 等 ref 声明之前，
// 回调里访问这些 const 会直接抛
//   ReferenceError: Cannot access 'profileLoading' before initialization
// 而 SSR 下这个异常会让整页 500。这类"顺序依赖"是巨型单文件组件最难查的坑之一，
// 所以：任何 immediate watch 都必须写在它所用到的所有声明之后。

const displayName = computed(() => userInfo.value?.realname || userInfo.value?.username || '')

/** 头像兜底：没有头像图时用姓名首字母（中文取第一个字） */
const avatarInitial = computed(() => {
  const source = displayName.value.trim()
  return source ? source.charAt(0).toUpperCase() : ''
})

const contactRows = computed(() => {
  const rows: { label: string; value: string; icon: string }[] = []
  if (userInfo.value?.email) {
    rows.push({ label: 'profile.email', value: String(userInfo.value.email), icon: 'lucide:mail' })
  }
  if (userInfo.value?.phone) {
    rows.push({ label: 'profile.phone', value: String(userInfo.value.phone), icon: 'lucide:phone' })
  }
  return rows
})

/**
 * 注册时间：userInfo.createTime 是后端 SysUser 真实字段（LoginController 直接返回 SysUser）。
 * 后端可能给 "2024-01-15 10:20:30"，也可能是 ISO，这里两种都能解析；
 * 真的拿不到就返回空串 -> 整块不渲染（绝不写死一个日期）。
 */
const memberSinceText = computed(() => {
  const raw = userInfo.value?.createTime
  if (!raw) return ''
  // 统一走 composables/useDateTimeFormat.ts：语言码要经 BCP-47 映射（zh -> zh-CN），
  // 解析规则也要与订单页保持一致（后端给的是 "yyyy-MM-dd HH:mm:ss"）。
  return formatDate(raw, locale.value)
})

/** 个人信息面板：只放真实有值的字段，空字段整行不渲染 */
const infoRows = computed(() => {
  const rows: { label: string; value: string; icon: string }[] = []
  if (userInfo.value?.realname) {
    rows.push({ label: 'profile.realname', value: String(userInfo.value.realname), icon: 'lucide:user-round' })
  }
  if (userInfo.value?.username) {
    rows.push({ label: 'profile.username', value: String(userInfo.value.username), icon: 'lucide:at-sign' })
  }
  rows.push(...contactRows.value)
  if (memberSinceText.value) {
    rows.push({ label: 'profile.memberSince', value: memberSinceText.value, icon: 'lucide:calendar' })
  }
  return rows
})

// ---------------------------------------------------------------------------
// 账户密码（设置 / 修改）
// ---------------------------------------------------------------------------

/**
 * 密码区块要回答两个问题，两个都**不能靠猜**：
 *
 *  1) 用户**有没有**设置过密码？—— 决定标题是「设置密码」还是「修改密码」。
 *     判据是后端 `GET /api/auth/profile` 的 `hasPassword`（契约 2.8），
 *     它读的是 `sys_user.pwd_set`：邮箱验证码 / Google 自动注册的用户为 false
 *     （他们的 password 字段是系统随机占位串，非空但没人知道明文，所以不能拿"password 非空"当判据）。
 *
 *     ⚠️ **不能**用 useAuth().userInfo 里的字段来判断：
 *        userInfo 来自 `/api/auth/session`，契约 3.2 明确规定它的形状 AuthSession 保持不变，
 *        并不保证带 hasPassword。所以这里像上面的收藏/订单一样**显式打一次接口**，
 *        拿到这个页面真正需要的字段，而不是去依赖一个契约没承诺的字段。
 *
 *  2) 当前密码要不要填？—— 已经有密码的用户必须验旧密码；没设过密码的用户
 *     压根没有旧密码，输入框直接不渲染（让用户面对一个必填却填不出的框是纯粹的阻塞）。
 */
const hasPassword = ref<boolean | null>(null)
const profileLoading = ref(false)
const profileError = ref('')

const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
/** 与资料表单同一套"用户动过就别覆盖"的思路：改动过程中不被任何异步回填冲掉 */
const passwordTouched = ref(false)
const passwordSubmitting = ref(false)
/** 两个密码框的可见性：默认遮住，眼睛图标切换（aria-label 随状态变化） */
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)

interface AuthProfileVO {
  id?: string | null
  username?: string | null
  realname?: string | null
  email?: string | null
  avatar?: string | null
  phone?: string | null
  hasPassword?: boolean | null
  registerStoreCode?: string | null
  createTime?: string | null
}

async function loadProfile() {
  profileLoading.value = true
  profileError.value = ''
  try {
    const data = await $fetch<AuthProfileVO>('/api/auth/profile', { query: { lang: locale.value } })
    // 只有后端**明确给了** hasPassword 才认；给不出就当 null（未知），
    // 由模板走"未知就先不渲染密码表单"的保守分支 —— 绝不默认成 false 骗用户去"设置密码"，
    // 那会给已有密码的用户一个不验旧密码的表单，最终被后端拒绝。
    hasPassword.value = typeof data?.hasPassword === 'boolean' ? data.hasPassword : null
  } catch (error) {
    // 401/403 不弹错：未登录时本页整页已是登录引导（auth 中间件 + isLoggedIn 分支）
    if (isUnauthorizedError(error)) return
    // 这里原本用的是 useAuth 的 resolveAuthErrorMessage，注释说它"把 statusMessage 排在
    // message 前面、与本页 readErrorMessage 不同"。实测两份实现的优先级**逐字相同**
    // （data.statusMessage > data.message > statusMessage > message），只是 fallback 传法不同，
    // 所以本次统一到 composables/useApiError.ts 的 readErrorMessage（useAuth 那边也已改为委托它）。
    profileError.value = readErrorMessage(error, t('profile.profileLoadFailed'))
  } finally {
    profileLoading.value = false
  }
}

/**
 * 订单、地址与账户资料都在**登录态恢复后立刻各拉一次**（不是等用户切到对应标签页）：
 * 顶部「订单数」统计卡要立即显示真实数量，否则用户会看到"订单数 0"却明明下过单。
 *   - 未登录不打接口（个人中心未登录时走的是登录引导，不该产生必然 401 的请求）；
 *   - 三个接口都只在登录态**首次**变为 true 时拉一次，避免重试/翻页后被 watch 冲掉。
 *
 * /api/auth/profile 同样在这里拉：密码区块要靠它的 hasPassword 决定标题，
 * 等用户点到「账户设置」再拉会让标题先空一下再跳变；顺手也让资料表单
 * 在有真实资料时更早拿到数据（契约 3 节新增的 profile.get.ts）。
 *
 * ⚠️ 位置要求：`immediate: true` 的回调会在 setup 期间**同步**执行，因此本段必须放在
 * ordersLoading / addressesLoading / profileLoading 以及 loadOrders/loadAddresses/loadProfile
 * 的声明**之后**。放在前面会抛 "Cannot access 'profileLoading' before initialization"，
 * SSR 下表现为 /profile 整页 500 —— 这是本页真实踩过的线上故障，不要为了"代码好读"把它上移。
 */
const privateDataLoaded = ref(false)

watch(isLoggedIn, () => {
  if (!isLoggedIn.value || privateDataLoaded.value) return
  privateDataLoaded.value = true
  void loadOrders()
  void loadAddresses()
  void loadProfile()
}, { immediate: true })

/**
 * 密码强度：与后端规则一致（8-32 位，至少含字母和数字，契约 2.2）。
 * 做成**轻量**的三段提示条，只提示不阻断 —— 真正的拒绝交给后端校验。
 */
const passwordStrength = computed<'' | 'weak' | 'medium' | 'strong'>(() => {
  const value = passwordForm.newPassword
  if (!value) return ''
  const hasLetter = /[a-zA-Z]/.test(value)
  const hasDigit = /\d/.test(value)
  if (value.length < 8 || !hasLetter || !hasDigit) return 'weak'
  if (value.length >= 12 && /[^a-zA-Z0-9]/.test(value)) return 'strong'
  return 'medium'
})

const passwordStrengthBarClass = computed(() => {
  if (passwordStrength.value === 'weak') return 'bg-[var(--color-error)] w-1/3'
  if (passwordStrength.value === 'medium') return 'bg-[var(--color-warning)] w-2/3'
  if (passwordStrength.value === 'strong') return 'bg-[var(--color-success)] w-full'
  return 'w-0'
})

const passwordMismatch = computed(
  () => !!passwordForm.confirmPassword && passwordForm.newPassword !== passwordForm.confirmPassword,
)

/**
 * 强度提示文案。
 *
 * frontend-auth 提供了专门的档位文案，按语义分两档用（不再自己拼字符串）：
 *   - 弱：`auth.passwordTooWeak` —— 它就是后端规则的说明（8-32 位、至少含字母和数字），
 *     正是在"弱"这一档需要讲清楚的话；
 *   - 中 / 强：换用 `auth.strengthMedium` / `auth.strengthStrong` 的档位词，
 *     前缀 `${auth.passwordStrength}:`，读起来是「密码强度：强」。
 *     若这里仍用 passwordTooWeak，密码很强时界面却在说"密码太弱"，自相矛盾。
 */
const passwordStrengthText = computed(() => {
  if (passwordStrength.value === 'weak') return t('auth.passwordTooWeak')
  if (passwordStrength.value === 'medium') return `${t('auth.passwordStrength')}: ${t('auth.strengthMedium')}`
  if (passwordStrength.value === 'strong') return `${t('auth.passwordStrength')}: ${t('auth.strengthStrong')}`
  return ''
})

/** 前端先拦一次低成本错误，省一轮往返；服务端仍会独立校验 */
const passwordFormValid = computed(() => {
  if (passwordSubmitting.value || hasPassword.value === null) return false
  if (hasPassword.value && !passwordForm.oldPassword) return false
  if (!passwordForm.newPassword) return false
  if (passwordForm.newPassword !== passwordForm.confirmPassword) return false
  return true
})

function markPasswordTouched() {
  passwordTouched.value = true
}

function resetPasswordForm() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordTouched.value = false
  showNewPassword.value = false
  showConfirmPassword.value = false
}

/**
 * 提交新密码。
 *
 * 走 useAuth().changePassword（契约 4.1，POST /api/auth/change-password），
 * 它负责带 httpOnly cookie 打 BFF 并解包结果；本页不自己 $fetch，避免两套错误语义。
 *
 * ⚠️ 方法由 frontend-auth 在 composables/useAuth.ts 中提供。若该文件还是旧版本，
 *    这里会是 undefined —— 那时应当等它落地，而不是在本页绕过它自己发请求（契约 5 节禁止跨范围）。
 */
async function handlePasswordSubmit() {
  if (passwordSubmitting.value) return

  if (hasPassword.value && !passwordForm.oldPassword) {
    toast.add({ title: t('profile.oldPasswordRequired'), color: 'warning', icon: 'lucide:circle-alert' })
    return
  }
  if (!passwordForm.newPassword) {
    toast.add({ title: t('profile.newPasswordRequired'), color: 'warning', icon: 'lucide:circle-alert' })
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    toast.add({ title: t('auth.passwordMismatch'), color: 'warning', icon: 'lucide:circle-alert' })
    return
  }

  passwordSubmitting.value = true
  // 标题要在这次提交前定下来：提交成功后 hasPassword 会立刻变 true，
  // 否则下面的成功提示会从「密码设置成功」变成「密码已更新」，与用户刚做的事对不上。
  const wasSettingPassword = hasPassword.value === false
  try {
    await changePassword({
      // 没设过密码的账号不该传 oldPassword（后端也接受空，见契约 2.7 注释）
      oldPassword: hasPassword.value ? passwordForm.oldPassword : undefined,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword,
    })

    // ★ 成功后必须把本页状态同步成"已设置密码"：
    //   否则用户改完密码，标题还停在「设置密码」，会以为刚才没成功（Lead 已明确要求）。
    hasPassword.value = true
    resetPasswordForm()
    toast.add({
      title: wasSettingPassword ? t('profile.passwordSetSuccess') : t('profile.passwordSaved'),
      color: 'success',
      icon: 'lucide:check',
    })
  } catch (error) {
    toast.add({
      title: t('profile.passwordSaveFailed'),
      // 同 loadProfile：错误文案统一走 readErrorMessage（useAuth 的 resolveAuthErrorMessage
      // 现在就是它的一层薄封装），才能把后端那句真实提示（如"原密码不正确"）显示出来。
      description: readErrorMessage(error, t('profile.passwordSaveFailed')),
      color: 'error',
      icon: 'lucide:circle-alert',
    })
  } finally {
    passwordSubmitting.value = false
  }
}

// ---------------------------------------------------------------------------
// 账户设置表单
// ---------------------------------------------------------------------------

const form = reactive({ realname: '', email: '', phone: '' })
/** 用户是否动过表单：动过之后就不要再被 userInfo 的变化覆盖，避免正在输入的内容被冲掉 */
const formTouched = ref(false)

function markTouched() {
  formTouched.value = true
}

function syncForm() {
  form.realname = userInfo.value?.realname ?? ''
  form.email = userInfo.value?.email ?? ''
  form.phone = userInfo.value?.phone ?? ''
}

// immediate：SSR 时 userInfo 为 null；客户端 init-auth 插件恢复登录态后，
// 这个 watch 会再跑一次把真实资料填进表单
watch(() => userInfo.value, () => {
  if (!formTouched.value) syncForm()
}, { immediate: true })

const dirty = computed(() => {
  const current = userInfo.value
  if (!current) return false
  return form.realname !== (current.realname ?? '')
    || form.email !== (current.email ?? '')
    || form.phone !== (current.phone ?? '')
})

/**
 * 保存资料。
 *
 * 注意：后端目前**没有**更新用户资料的接口（server/api 下只有 auth/login + auth/logout），
 * 所以这里只更新前端共享的 userInfo 状态并写回 localStorage —— 写进去的是用户自己刚输入
 * 的内容，不属于伪造数据，但它**不会**同步到服务器，换设备/清缓存后会变回后端原值。
 * 等 BFF 提供 /api/auth/profile 之后，这里应替换成真实请求。
 */
function handleSave() {
  const current = userInfo.value
  if (!current) return

  const next = {
    ...current,
    realname: form.realname.trim(),
    email: form.email.trim(),
    phone: form.phone.trim(),
  }
  userInfo.value = next
  form.realname = next.realname
  form.email = next.email
  form.phone = next.phone
  formTouched.value = false

  if (import.meta.client) {
    // key 必须与 composables/useAuth.ts 里 restoreUser 读取的 'user-info' 一致
    localStorage.setItem('user-info', JSON.stringify(next))
  }

  toast.add({ title: t('shop.profileSaveSuccess'), color: 'success', icon: 'lucide:check' })
}

/** WAI-ARIA tabs 键盘交互：左右方向键循环，Home / End 跳首尾，并把焦点移到新选中的标签 */
function handleTabKeydown(event: KeyboardEvent, index: number) {
  const last = tabs.length - 1
  let next = index
  if (event.key === 'ArrowRight') next = index === last ? 0 : index + 1
  else if (event.key === 'ArrowLeft') next = index === 0 ? last : index - 1
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = last
  else return

  event.preventDefault()
  const target = tabs[next]
  if (!target) return
  // 走 selectTab 而不是直接赋值：键盘切换标签同样要把 ?tab= 同步到 URL
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
})
</script>
