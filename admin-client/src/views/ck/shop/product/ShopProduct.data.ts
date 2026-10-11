import { h } from 'vue';
import { Tag, Tooltip } from 'ant-design-vue';
import { BasicColumn } from '/@/components/Table';

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
    title: '分类 / 品牌',
    align: 'left',
    dataIndex: 'categoryName',
    width: 180,
    customRender: ({ record }) => {
      const text = [record.categoryName, record.brandName].filter(Boolean).join(' / ');
      return h('span', text || '-');
    },
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
    title: '销售渠道',
    align: 'left',
    dataIndex: 'stores',
    width: 170,
    customRender: ({ record }) => {
      const stores = record.stores || [];
      if (!stores.length) {
        return h('span', { style: 'color:rgba(0,0,0,.35)' }, '未上架');
      }
      return h(
        'div',
        { style: 'display:flex;flex-wrap:wrap;gap:4px' },
        stores.map((item: any) =>
          h(
            Tag,
            // 本店下架用灰色，上架用蓝色：一眼能看出"上架到几个店、其中哪些是下架的"
            { color: item.status === 'N' ? 'default' : 'blue', key: item.storeId },
            () => item.storeName || item.storeCode || item.storeId
          )
        )
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
  status: { title: '状态: N-下架, Y-上架', order: 1, view: 'text', type: 'string' },
  categoryId: { title: '分类ID', order: 2, view: 'text', type: 'string' },
  brandId: { title: '品牌ID', order: 3, view: 'text', type: 'string' },
  isRecommend: { title: '是否推荐: N-否, Y-是', order: 4, view: 'text', type: 'string' },
  sortOrder: { title: '排序', order: 5, view: 'number', type: 'number' },
};
