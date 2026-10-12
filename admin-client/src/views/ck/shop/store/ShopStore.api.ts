import { defHttp } from '/@/utils/http/axios';

enum Api {
  list = '/shop/store/list',
  all = '/shop/store/all',
  add = '/shop/store/add',
  edit = '/shop/store/edit',
  delete = '/shop/store/delete',
  deleteBatch = '/shop/store/deleteBatch',
  queryById = '/shop/store/queryById',
  exportXls = '/shop/store/exportXls',
  importExcel = '/shop/store/importExcel',
}

/** 导出 api（需要 shop-store:exportXls 权限） */
export const getExportUrl = Api.exportXls;

/** 导入 api（需要 shop-store:importExcel 权限，后端会逐行走校验） */
export const getImportUrl = Api.importExcel;

/** 分页列表 */
export const list = (params) => defHttp.get({ url: Api.list, params });

/** 全部启用店铺（商品/分类表单的「所属店铺」选择共用；返回项含只读派生字段 departOrgCode） */
export const all = () => defHttp.get({ url: Api.all });

export const add = (params) => defHttp.post({ url: Api.add, params });

export const edit = (params) => defHttp.post({ url: Api.edit, params });

export const deleteOne = (params, handleSuccess) =>
  defHttp.delete({ url: Api.delete, params }, { joinParamsToUrl: true }).then(() => handleSuccess());

export const batchDelete = (params, handleSuccess) =>
  defHttp.delete({ url: Api.deleteBatch, params }, { joinParamsToUrl: true }).then(() => handleSuccess());

export const queryById = (params) => defHttp.get({ url: Api.queryById, params });
