import { defHttp } from '/@/utils/http/axios';
import { useMessage } from '/@/hooks/web/useMessage';

const { createConfirm } = useMessage();

enum Api {
  list = '/shop/product/list',
  queryById = '/shop/product/queryById',
  skuList = '/shop/product/skuList',
  save = '/shop/product/add',
  edit = '/shop/product/edit',
  updateStatus = '/shop/product/updateStatus',
  deleteOne = '/shop/product/delete',
  deleteBatch = '/shop/product/deleteBatch',
  exportXls = '/shop/product/exportXls',
  importExcel = '/shop/product/importExcel',
}

/**
 * 导出api
 */
export const getExportUrl = Api.exportXls;

/**
 * 导入api
 */
export const getImportUrl = Api.importExcel;

/**
 * 商品分页列表
 */
export const list = (params) => defHttp.get({ url: Api.list, params });

/**
 * 根据id查询商品详情（含多语言、图集、规格、SKU组合）
 */
export const queryById = (params) => defHttp.get({ url: Api.queryById, params });

/**
 * 查询某商品的 SKU 组合（列表展开行）
 */
export const querySkuList = (params) => defHttp.get({ url: Api.skuList, params });

/**
 * 删除单个
 */
export const deleteOne = (params, handleSuccess) => {
  return defHttp.delete({ url: Api.deleteOne, params }, { joinParamsToUrl: true }).then(() => {
    handleSuccess();
  });
};

/**
 * 批量删除
 */
export const batchDelete = (params, handleSuccess) => {
  createConfirm({
    iconType: 'warning',
    title: '确认删除',
    content: '是否删除选中数据',
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return defHttp.delete({ url: Api.deleteBatch, data: params }, { joinParamsToUrl: true }).then(() => {
        handleSuccess();
      });
    },
  });
};

/**
 * 保存或者更新
 */
export const saveOrUpdate = (params, isUpdate) => {
  const url = isUpdate ? Api.edit : Api.save;
  return defHttp.post({ url: url, params }, { isTransformResponse: false });
};

/**
 * 上架 / 下架
 */
export const updateProductStatus = (params) => {
  return defHttp.post({ url: Api.updateStatus, params }, { joinParamsToUrl: true });
};

/**
 * 品牌下拉（全部启用品牌）
 */
export const brandList = (params) => defHttp.get({ url: '/shop/brand/list', params });

/**
 * 标签下拉（全部启用标签）
 */
export const tagAll = () => defHttp.get({ url: '/shop/tag/all' });

/**
 * 规格预设库（用于从预设导入规格）
 */
export const optionsAll = () => defHttp.get({ url: '/shop/options/all' });

/**
 * 分类下拉（全部启用分类）
 */
export const categoryList = (params) => defHttp.get({ url: '/shop/category/list', params });

/** 启用中的店铺（商品列表的店铺筛选、商品表单的销售渠道选择共用） */
export const storeAll = () => defHttp.get({ url: '/shop/store/all' });
