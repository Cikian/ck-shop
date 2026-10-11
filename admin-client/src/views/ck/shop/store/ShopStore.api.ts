import { defHttp } from '/@/utils/http/axios';

enum Api {
  list = '/shop/store/list',
  all = '/shop/store/all',
  add = '/shop/store/add',
  edit = '/shop/store/edit',
  delete = '/shop/store/delete',
  deleteBatch = '/shop/store/deleteBatch',
  queryById = '/shop/store/queryById',
}

/** 分页列表 */
export const list = (params) => defHttp.get({ url: Api.list, params });

/** 全部启用店铺（商品表单「销售渠道」选择用） */
export const all = () => defHttp.get({ url: Api.all });

export const add = (params) => defHttp.post({ url: Api.add, params });

export const edit = (params) => defHttp.post({ url: Api.edit, params });

export const deleteOne = (params, handleSuccess) =>
  defHttp.delete({ url: Api.delete, params }, { joinParamsToUrl: true }).then(() => handleSuccess());

export const batchDelete = (params, handleSuccess) =>
  defHttp.delete({ url: Api.deleteBatch, params }, { joinParamsToUrl: true }).then(() => handleSuccess());

export const queryById = (params) => defHttp.get({ url: Api.queryById, params });
