import { defHttp } from '/@/utils/http/axios';
import { useMessage } from '/@/hooks/web/useMessage';

const { createConfirm } = useMessage();

/**
 * 促销活动 · 后台接口
 *
 * 路径与入参严格按《促销活动管理-接口契约》第 2 节实现，不要自行改名：
 * - 店铺端（免登录）的活动接口是 /shop/promotion/store/active，与后台 CRUD 不在同一前缀下；
 * - add/edit/copy 的 result 恒为「活动 id」，不是提示语（前端要据此回填继续编辑）。
 */
enum Api {
  list = '/shop/promotion/list',
  queryById = '/shop/promotion/queryById',
  save = '/shop/promotion/add',
  edit = '/shop/promotion/edit',
  updateStatus = '/shop/promotion/updateStatus',
  updateStatusBatch = '/shop/promotion/updateStatusBatch',
  copy = '/shop/promotion/copy',
  deleteOne = '/shop/promotion/delete',
  deleteBatch = '/shop/promotion/deleteBatch',
  checkConflict = '/shop/promotion/checkConflict',
  exportXls = '/shop/promotion/exportXls',
  importExcel = '/shop/promotion/importExcel',
  /** ↓ 以下不是促销接口：复用既有的店铺 / 商品接口，避免后端为选商品再造一套 */
  storeAll = '/shop/store/all',
  productList = '/shop/product/list',
  productQueryById = '/shop/product/queryById',
  productSkuList = '/shop/product/skuList',
}

/**
 * 导出 api（需 shop-promotion:exportXls 权限）
 */
export const getExportUrl = Api.exportXls;

/**
 * 导入 api（需 shop-promotion:importExcel 权限）
 */
export const getImportUrl = Api.importExcel;

/**
 * 分页列表
 * @param params PromotionQuery + pageNo + pageSize
 */
export const list = (params) => defHttp.get({ url: Api.list, params });

/**
 * 根据 id 查询活动详情（PromotionDTO：主表字段 + translations + products）
 */
export const queryById = (params) => defHttp.get({ url: Api.queryById, params });

/**
 * 删除单个（主表软删，关联表与翻译表由后端清理）
 */
export const deleteOne = (params, handleSuccess) => {
  return defHttp.delete({ url: Api.deleteOne, params }, { joinParamsToUrl: true }).then(() => {
    handleSuccess();
  });
};

/**
 * 批量删除
 * @param params { ids }：selectedRowKeys 数组，axios 会序列化成 ids=a&ids=b，
 *               后端 @RequestParam String ids 取不到逗号串，因此这里显式 join(',')
 */
export const batchDelete = (params, handleSuccess, count?: number) => {
  const total = Number(count ?? (Array.isArray(params?.ids) ? params.ids.length : 0));
  createConfirm({
    iconType: 'warning',
    title: '确认删除',
    content: `将删除选中的 ${total} 个活动，及其多语言文案与关联商品；删除后不可恢复。`,
    okText: '确认删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: () => {
      const ids = Array.isArray(params?.ids) ? params.ids.join(',') : params?.ids;
      return defHttp.delete({ url: Api.deleteBatch, params: { ...params, ids } }, { joinParamsToUrl: true }).then(() => {
        handleSuccess();
      });
    },
  });
};

/**
 * 保存或者更新
 *
 * isTransformResponse: false —— 需要拿到原始 Result，因为契约约定 result 是活动 id，
 * 前端「保存并继续编辑」必须据此回填 id（不能把 result 当提示语直接 toast）。
 */
export const saveOrUpdate = (params, isUpdate) => {
  const url = isUpdate ? Api.edit : Api.save;
  return defHttp.post({ url: url, params }, { isTransformResponse: false });
};

/**
 * 上/下线快捷开关
 * @param params { id, status: 'Y' | 'N' }
 */
export const updateStatusBatch = (params: { ids: string; status: 'Y' | 'N' }) => {
  return defHttp.post({ url: Api.updateStatusBatch, params }, { joinParamsToUrl: true });
};

export const updatePromotionStatus = (params) => {
  return defHttp.post({ url: Api.updateStatus, params }, { joinParamsToUrl: true });
};

/**
 * 复制活动（后端：新 id、status 强制 N、标题加「(副本)」后缀）
 * @param params { id, newCode? }
 * @returns 原始 Result，result 为新活动 id
 */
export const copyPromotion = (params) => {
  return defHttp.post({ url: Api.copy, params }, { joinParamsToUrl: true, isTransformResponse: false });
};

/**
 * 活动冲突检测（同一商品在同一时间段内是否已被其它活动占用）
 * @param params { productIds: 'id1,id2', startTime, endTime, excludeId? }
 */
export const checkConflict = (params) => defHttp.get({ url: Api.checkConflict, params });

/**
 * 启用中的店铺（列表页「店铺」筛选 + 表单「所属店铺」选择共用）
 */
export const storeAll = () => defHttp.get({ url: Api.storeAll });

/**
 * 商品分页列表（选商品弹窗：按 storeId 过滤该店铺的商品）
 *
 * 入参对应后端的 ProductQuery（keyword / storeId / spuCode / status / categoryId 等），
 * 注意它**不接受 id 列表**，所以「按 id 批量补查」只能走 queryById（见下）。
 */
export const productList = (params) => defHttp.get({ url: Api.productList, params });

/**
 * 单个商品详情（回填「已关联商品」在店铺商品分页之外的名称/现价）
 */
export const productQueryById = (id: string) => defHttp.get({ url: Api.productQueryById, params: { id } });

/**
 * 某商品的 SKU 组合（关联到具体规格时展示规格名）
 */
export const productSkuList = (params) => defHttp.get({ url: Api.productSkuList, params });
