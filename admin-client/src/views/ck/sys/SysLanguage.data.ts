import {BasicColumn} from '/@/components/Table';
import {FormSchema} from '/@/components/Table';
import { rules} from '/@/utils/helper/validator';
import { render } from '/@/utils/common/renderUtils';
import { getWeekMonthQuarterYear } from '/@/utils';
//列表数据
export const columns: BasicColumn[] = [
  {
    title: '语言编码',
    align: 'center',
    dataIndex: 'code',
  },
  {
    title: '语言代码',
    align: 'center',
    dataIndex: 'language',
  },
  {
    title: '语言名称',
    align: 'center',
    dataIndex: 'name',
  },
  {
    title: '排序',
    align: 'center',
    dataIndex: 'sort',
  },
  {
    title: '启用',
    align: 'center',
    dataIndex: 'enabled',
    slots: { customRender: 'enabled' },
    // customRender: ({ text }) => {
    //   return render.renderSwitch(text, [
    //     { text: '是', value: 'Y' },
    //     { text: '否', value: 'N' },
    //   ]);
    // },
  },
];

// 高级查询数据
export const superQuerySchema = {
  code: { title: '语言编码', order: 0, view: 'text', type: 'string' },
  language: { title: '语言代码', order: 1, view: 'text', type: 'string' },
  name: { title: '语言名称', order: 2, view: 'text', type: 'string' },
  sort: { title: '排序', order: 3, view: 'text', type: 'number' },
  enabled: { title: '启用', order: 4, view: 'switch', type: 'string' },
};
