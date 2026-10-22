import { h, reactive } from 'vue';
import { Tag, Tooltip } from 'ant-design-vue';
import { BasicColumn } from '/@/components/Table';

/**
 * departOrgCode（= 商品 sysOrgCode，即所属店铺绑定的部门 org_code）-> 店铺名 的映射。
 *
 * 商品列表接口返回的是 ProductListVO，只有 sysOrgCode 没有店铺名；
 * 店铺数量很少，由列表页加载 /shop/store/all 后写进这个模块级响应式映射，
 * 列渲染直接查表，避免为了显示一个名字再往后端加一次连表查询。
 */
export const orgStoreNameMap = reactive<Record<string, string>>({});

/** 由列表页在加载店铺后调用：key = 店铺绑定的部门 org_code，value = 店铺名 */
export function setOrgStoreNameMap(list: any[]) {
  Object.keys(orgStoreNameMap).forEach((key) => delete orgStoreNameMap[key]);
  (list || []).forEach((item: any) => {
    if (item?.departOrgCode) {
      orgStoreNameMap[item.departOrgCode] = item.name || item.code || item.departOrgCode;
    }
  });
}

/** 价格展示：区间用 ~ 连接，单值直接展示 */
function formatPriceRange(min?: number | string | null, max?: number | string | null): string {
  if (min === null || min === undefined || min === '') return '-';
  const minText = Number(min).toFixed(2);
  if (max === null || max === undefined || max === '' || Number(max) === Number(min)) {
    return `¥${minText}`;
  }
  return `¥${minText} ~ ¥${Number(max).toFixed(2)}`;
}

// 列表数据
export const columns: BasicColumn[] = [
  {
    title: '商品',
    align: 'left',
    dataIndex: 'title',
    width: 320,
    customRender: ({ record }) => {
      const cover = record.coverUrl || record.mainImage;
      const image = cover
        ? h('img', {
            src: cover,
            style: 'width:44px;height:44px;object-fit:cover;border-radius:6px;border:1px solid #f0f0f0;flex:none',
            alt: record.title || '商品图',
            loading: 'lazy',
          })
        : h(
            'div',
            {
              style:
                'width:44px;height:44px;border-radius:6px;border:1px dashed #d9d9d9;display:flex;align-items:center;justify-content:center;color:#bfbfbf;font-size:12px;flex:none',
            },
            '无图'
          );
      return h('div', { style: 'display:flex;align-items:center;gap:10px;min-width:0' }, [
        image,
        h('div', { style: 'min-width:0;flex:1' }, [
          h('div', { style: 'font-weight:500;color:rgba(0,0,0,.88);overflow:hidden;text-overflow:ellipsis;white-space:nowrap' }, record.title || '-'),
          h('div', { style: 'font-size:12px;color:rgba(0,0,0,.45);overflow:hidden;text-overflow:ellipsis;white-space:nowrap' }, record.spuCode || '-'),
        ]),
      ]);
    },
  },
  {
    title: '分类',
    align: 'left',
    dataIndex: 'categoryName',
    width: 180,
    customRender: ({ record }) => h('span', record.categoryName || '-'),
  },
  {
    title: '标签',
    align: 'left',
    dataIndex: 'tags',
    width: 180,
    customRender: ({ record }) => {
      const tags = record.tags || [];
      if (!tags.length) return h('span', { style: 'color:rgba(0,0,0,.35)' }, '-');
      return h(
        'div',
        { style: 'display:flex;flex-wrap:wrap;gap:4px' },
        tags.slice(0, 3).map((tag: any) => h(Tag, { color: 'blue', key: tag.id }, () => tag.name || tag.code))
      );
    },
  },
  {
    /*
     * 注意：本列的 dataIndex 是商品自身的 sysOrgCode（= 店铺绑定的部门 org_code），
     * 而筛选区「所属店铺」传的是 storeId（店铺主键）。两者不是同一个值，
     * 后端对这两种入参都支持，不要"为了一致"把它们改成同一个字段。
     */
    title: '所属店铺',
    align: 'left',
    dataIndex: 'sysOrgCode',
    width: 170,
    customRender: ({ record }) => {
      const orgCode = record.sysOrgCode;
      if (!orgCode) {
        return h('span', { style: 'color:rgba(0,0,0,.35)' }, '-');
      }
      const storeName = orgStoreNameMap[orgCode];
      if (storeName) {
        return h('span', storeName);
      }
      // 映射查不到：店铺可能已删除，或该部门还没绑定店铺。
      // 不要直接把裸 org_code 当正常店铺名展示，否则看起来像是有效值。
      return h(
        Tooltip,
        { title: '该店铺可能已被删除，或该部门尚未绑定店铺' },
        () => h('span', { style: 'color:#d46b08' }, `未知店铺（${orgCode}）`)
      );
    },
  },
  {
    title: '售价',
    align: 'center',
    dataIndex: 'minPrice',
    width: 150,
    customRender: ({ record }) => formatPriceRange(record.minPrice ?? record.price, record.maxPrice ?? record.price),
  },
  {
    title: '总库存',
    align: 'center',
    dataIndex: 'stock',
    width: 100,
    sorter: true,
    customRender: ({ text }) => {
      const stock = Number(text ?? 0);
      if (stock <= 0) {
        return h(Tag, { color: 'red' }, () => '无货');
      }
      if (stock <= 10) {
        return h(Tag, { color: 'orange' }, () => `仅剩 ${stock}`);
      }
      return h(Tag, { color: 'green' }, () => String(stock));
    },
  },
  {
    title: '排序',
    align: 'center',
    dataIndex: 'sortOrder',
    width: 80,
    // 表头问号提示：排序方向与前台一致，避免误解成"越大越靠前"
    helpMessage: '数字越小越靠前（与前台一致）',
  },
  {
    title: '状态',
    align: 'center',
    dataIndex: 'status',
    width: 90,
    customRender: ({ text }) => {
      if (text === 'Y') return h(Tag, { color: 'green' }, () => '上架');
      if (text === 'N') return h(Tag, { color: 'default' }, () => '下架');
      return h('span', text);
    },
  },
  {
    title: '推荐',
    align: 'center',
    dataIndex: 'isRecommend',
    width: 80,
    customRender: ({ text }) => {
      return text === 'Y'
        ? h(Tooltip, { title: '首页推荐商品' }, () => h(Tag, { color: 'gold' }, () => '推荐'))
        : h('span', { style: 'color:rgba(0,0,0,.35)' }, '-');
    },
  },
  {
    title: '创建时间',
    align: 'center',
    dataIndex: 'createTime',
    width: 170,
  },
];

// 高级查询数据
export const superQuerySchema = {
  spuCode: { title: 'SPU编码', order: 0, view: 'text', type: 'string' },
  status: {
    title: '状态',
    order: 1,
    view: 'list',
    type: 'string',
    enum: [
      { label: '上架', value: 'Y' },
      { label: '下架', value: 'N' },
    ],
  },
  // categoryId 的选项在 ShopProductList 里按已加载的分类动态注入（view='list'），运营不必再手抄主键
  categoryId: { title: '分类', order: 2, view: 'list', type: 'string' },
  isRecommend: {
    title: '是否推荐',
    order: 4,
    view: 'list',
    type: 'string',
    enum: [
      { label: '推荐', value: 'Y' },
      { label: '不推荐', value: 'N' },
    ],
  },
  sortOrder: { title: '排序', order: 5, view: 'number', type: 'number' },
};
