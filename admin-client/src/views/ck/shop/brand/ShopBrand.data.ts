import { h } from 'vue';
import { Tag } from 'ant-design-vue';
import { BasicColumn } from '/@/components/Table';
import { render } from '/@/utils/common/renderUtils';

/**
 * 兼容后端返回「数组」或「JSON 字符串」两种 translations 形态
 */
function normalizeTranslations(value: any): any[] {
  if (Array.isArray(value)) {
    return value;
  }
  if (typeof value === 'string' && value) {
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }
  return [];
}

/**
 * 列表内部名称：优先中文（zh / zh_CN），其次第一条有名称的记录，最后回退品牌编码
 * 说明：品牌名称存在品牌多语言表，列表接口返回的 translations 里取用
 */
export function getInternalName(record: Recordable): string {
  const list = normalizeTranslations(record?.translations);
  const zh = list.find((item) => item && (item.languageCode === 'zh' || item.languageCode === 'zh_CN'));
  const first = list.find((item) => item && item.name);
  return zh?.name || first?.name || record?.code || '-';
}

/**
 * 启用状态渲染：绿色「启用」/ 红色「禁用」
 */
export function renderEnableTag(text: any) {
  if (text === 'Y') {
    return h(Tag, { color: 'green' }, () => '启用');
  }
  if (text === 'N') {
    return h(Tag, { color: 'red' }, () => '禁用');
  }
  return h('span', text ? String(text) : '-');
}

//列表数据
export const columns: BasicColumn[] = [
  {
    title: 'LOGO',
    align: 'center',
    dataIndex: 'logoUrl',
    width: 100,
    customRender: ({ text }) => {
      if (!text) {
        return text;
      }
      return render.renderImage({ text });
    },
  },
  {
    title: '内部名称',
    align: 'center',
    dataIndex: 'translations',
    customRender: ({ record }) => getInternalName(record),
  },
  {
    title: '品牌编码',
    align: 'center',
    dataIndex: 'code',
  },
  {
    title: '排序',
    align: 'center',
    sorter: true,
    dataIndex: 'sortOrder',
  },
  {
    title: '启用',
    align: 'center',
    dataIndex: 'isShow',
    customRender: ({ text }) => renderEnableTag(text),
  },
  {
    title: '创建时间',
    align: 'center',
    dataIndex: 'createTime',
    width: 160,
  },
];

// 高级查询数据
export const superQuerySchema = {
  code: { title: '品牌编码', order: 0, view: 'text', type: 'string' },
  logoUrl: { title: '品牌LOGO', order: 1, view: 'text', type: 'string' },
  sortOrder: { title: '排序', order: 2, view: 'number', type: 'number' },
  isShow: { title: '是否启用: N-禁用, Y-启用', order: 3, view: 'switch', type: 'string' },
};
