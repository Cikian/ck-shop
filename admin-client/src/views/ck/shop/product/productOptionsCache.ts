// 商品列表筛选项的模块级缓存。
//
// 背景（清单 A19）：商品列表每次进入都会拉
//   · categoryList({ pageSize: 200 })
//   · tagAll()（全量标签）
//   · storeAll()（全量店铺，且无 shop-product:filter-store 权限的账号根本看不到该筛选项）
// 三个请求都是"分钟级不变"的参考数据，来回切换列表页却重复拉取。
//
// 这里做两件事：
//   1) 模块级缓存：同一会话内只拉一次，翻页/返回列表不再重复请求；
//   2) 店铺按权限懒加载：没有筛选权限就不请求（避免无谓的数据暴露与耗时）。
//
// 说明：缓存不设 TTL —— 新增分类/标签后由「分类管理/标签管理」页面操作，
// 运营刷新浏览器即可看到；如果需要强一致，可在保存成功后调用 resetProductOptionsCache()。

import { categoryList, tagAll, storeAll } from './ShopProduct.api';

export interface ProductReferenceOptions {
  categories: Array<{ label: string; value: string }>;
  tags: Array<{ label: string; value: string }>;
  stores: any[];
}

let cache: ProductReferenceOptions | null = null;
let inflight: Promise<ProductReferenceOptions> | null = null;

function pickName(record: any): string {
  const list = record?.translations;
  if (Array.isArray(list) && list.length) {
    const zh = list.find((t: any) => t?.languageCode === 'zh' || t?.languageCode === 'zh-CN');
    return zh?.name || list[0]?.name || '';
  }
  return record?.name || record?.code || '';
}

/**
 * 读取商品筛选项（带缓存）。
 * @param includeStores 是否加载店铺（由 shop-product:filter-store 权限决定）
 */
export function loadProductReferenceOptions(includeStores: boolean): Promise<ProductReferenceOptions> {
  if (cache) {
    return Promise.resolve(cache);
  }
  if (inflight) {
    return inflight;
  }
  inflight = (async () => {
    const [categoryRes, tagRes, storeRes] = await Promise.all([
      categoryList({ pageNo: 1, pageSize: 200, isShow: 'Y' }),
      tagAll(),
      includeStores ? storeAll().catch(() => []) : Promise.resolve([]),
    ]);
    const categoryRecords = categoryRes?.records || categoryRes || [];
    const tagRecords = tagRes || [];
    const result: ProductReferenceOptions = {
      categories: categoryRecords.map((item: any) => ({ label: pickName(item) || item.id, value: item.id })),
      tags: tagRecords.map((item: any) => ({ label: pickName(item) || item.code, value: item.id })),
      stores: storeRes || [],
    };
    cache = result;
    return result;
  })()
    .catch((error) => {
      // 失败不缓存，下次进入可重试
      inflight = null;
      throw error;
    })
    .finally(() => {
      inflight = null;
    });
  return inflight;
}

/** 分类/标签/店铺数据变更后调用，强制下次重新加载 */
export function resetProductOptionsCache(): void {
  cache = null;
  inflight = null;
}
