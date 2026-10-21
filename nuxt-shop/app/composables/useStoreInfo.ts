// composables/useStoreInfo.ts
//
// 当前店铺的公开信息（品牌名 / Logo / 主题）。
// 数据来自 BFF `/api/store/info`（→ Java `/shop/store/store-info`，按 X-Store-Code 解析）。
//
// 全站共享同一个 useAsyncData key：Header、Footer、首页 SEO 都调用它时只会请求一次，
// SSR 期间完成取数，客户端复用 payload，不产生额外往返。

export interface StorePublicInfo {
  code: string
  name: string
  logoUrl: string | null
  themeConfig: string | null
}

const EMPTY_STORE_INFO: StorePublicInfo = {
  code: '',
  name: '',
  logoUrl: null,
  themeConfig: null,
}

export function useStoreInfo() {
  const { data, error } = useAsyncData<StorePublicInfo>(
    'store-info',
    () => $fetch<StorePublicInfo>('/api/store/info'),
    { default: () => ({ ...EMPTY_STORE_INFO }) },
  )

  /** 店铺名；为空时调用方自行回退（不要在组件里写死品牌） */
  const storeName = computed(() => (data.value?.name || '').trim())
  const storeLogoUrl = computed(() => data.value?.logoUrl || '')

  return { storeInfo: data, storeName, storeLogoUrl, storeInfoError: error }
}
