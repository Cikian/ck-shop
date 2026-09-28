import { h } from 'vue';
import { Tag } from 'ant-design-vue';
import {BasicColumn} from '/@/components/Table';
import {FormSchema} from '/@/components/Table';
import { rules} from '/@/utils/helper/validator';
import { render } from '/@/utils/common/renderUtils';
import { getWeekMonthQuarterYear } from '/@/utils';
//列表数据
export const columns: BasicColumn[] = [
  {
    title: '内部名称',
    align: "center",
    dataIndex: 'name'
  },
  {
    title: '排序',
    align: "center",
    sorter: true,
    dataIndex: 'sortOrder'
  },
//   {
//     title: '分类图标URL',
//     align: "center",
//     dataIndex: 'iconUrl'
//   },
  {
    title: '默认封面',
    align: "center",
    dataIndex: 'coverUrl',
    width: 100,
    customRender: ({ text }) => {
      if(!text){
        return text;
      }
      return render.renderImage({text});
    },
  },
  {
    title: '启用',
    align: "center",
    dataIndex: 'isShow',
    customRender:({text}) => {
      if (text === 'Y') {
        return h(Tag, { color: 'green' }, () => '启用');
      } else if (text === 'N') {
        return h(Tag, { color: 'red' }, () => '禁用');
      }
      return h('span', text);
    },
  },
];

// 高级查询数据
export const superQuerySchema = {
  parentId: {title: '父级分类ID, 0表示顶级分类',order: 0,view: 'text', type: 'string',},
  level: {title: '分类层级: 1-一级, 2-二级, 3-三级',order: 1,view: 'number', type: 'number',},
  sortOrder: {title: '排序',order: 2,view: 'number', type: 'number',},
  iconUrl: {title: '分类图标URL',order: 3,view: 'text', type: 'string',},
  coverUrl: {title: '封面URL',order: 4,view: 'text', type: 'string',},
  isShow: {title: '是否显示: N-隐藏, Y-显示',order: 5,view: 'switch', type: 'string',},
};
