import { h, reactive } from 'vue';
import { Tag, Tooltip } from 'ant-design-vue';
import {BasicColumn} from '/@/components/Table';
import { render } from '/@/utils/common/renderUtils';

/**
 * sysOrgCode（= 店铺绑定的部门 org_code）-> 店铺名 的映射。
 *
 * 分类列表接口返回的是实体，只有 sysOrgCode 没有店铺名；店铺数量很少，
 * 由列表页加载一次后写进这个模块级响应式映射，列渲染直接查表，
 * 避免为了显示一个名字再往后端加一次连表查询。
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

//列表数据
export const columns: BasicColumn[] = [
  {
    title: '内部名称',
    align: "center",
    dataIndex: 'name'
  },
  {
    title: '所属店铺',
    align: 'center',
    dataIndex: 'sysOrgCode',
    width: 160,
    customRender: ({ record }) => {
      // 分类的归属列就是店铺绑定的部门 org_code，空值表示尚未归属
      if (!record.sysOrgCode) {
        return h('span', { style: 'color:rgba(0,0,0,.45)' }, '未设置');
      }
      const storeName = orgStoreNameMap[record.sysOrgCode];
      if (storeName) {
        return h(Tag, { color: 'blue' }, () => storeName);
      }
      // 映射查不到：店铺可能已删除，或该部门还没绑定店铺，别把裸 org_code 当正常值展示
      return h(
        Tooltip,
        { title: '该店铺可能已被删除，或该部门尚未绑定店铺' },
        () => h(Tag, { color: 'orange' }, () => `未知店铺（${record.sysOrgCode}）`)
      );
    },
  },
  {
    title: '排序',
    align: "center",
    sorter: true,
    dataIndex: 'sortOrder',
    // 表头问号提示：排序方向与前台一致
    helpMessage: '数字越小越靠前（与前台一致）',
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
