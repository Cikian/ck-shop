import { defHttp } from '/@/utils/http/axios';

enum Api {
  /** 全量列表：GET /home/slide/all（按语言分组返回） */
  listAll = '/home/slide/all',
  /** 新增与编辑共用同一个地址，仅请求方法不同 */
  base = '/home/slide',
  queryById = '/home/slide/queryById',
  deleteOne = '/home/slide/delete',
  deleteBatch = '/home/slide/deleteBatch',
  saveSort = '/home/slide/sort',
}

/** 后端 Result 包装体（isTransformResponse: false 时返回的原生结构） */
export interface ApiResult<T = any> {
  success: boolean;
  code: number;
  message: string;
  result: T;
}

/** 一条轮播图（管理端全量结构） */
export interface SlideRecord {
  /** 文档主键 _id */
  id: string;
  /** 中文描述，用于后台识别 */
  desc?: string | null;
  /** 排序值，从 1 开始 */
  sort?: number | null;
  /** 关联商品编码 */
  goods?: string | null;
  /** 跳转地址（后端存储时会自动补全协议，管理端展示时去掉协议） */
  targetUrl?: string | null;
  /** 启用状态：Y 启用 / N 禁用 */
  enabled?: string | null;
  /** 各语言内容，形如 { zh: { title_zh, pic_url_zh }, en: {...} } */
  [langCode: string]: any;
}

/** 多语言列表项（ck_sys_language） */
export interface LanguageItem {
  id: string;
  /** 语言编码，同时作为表单里的语言 key，如 zh / en / fr */
  code: string;
  /** 语言代码，如 zh-CN */
  language: string;
  /** 语言名称，如 简体中文 */
  name: string;
  enabled: string;
  sort: number;
}

/** 列表接口：全量轮播图（按语言分组） */
export const list = () => defHttp.get<SlideRecord[]>({ url: Api.listAll });

/** 单条查询：返回已去掉协议的 targetUrl，便于编辑器回填 */
export const queryById = (id: string) => defHttp.get<SlideRecord>({ url: Api.queryById, params: { id } });

/**
 * 保存 / 更新轮播图
 * 不启用响应转换，交由调用方根据 success 决定提示文案
 */
export const saveOrUpdate = (params: SlideRecord, isUpdate: boolean): Promise<ApiResult> => {
  return isUpdate
    ? defHttp.put({ url: Api.base, params }, { isTransformResponse: false })
    : defHttp.post({ url: Api.base, params }, { isTransformResponse: false });
};

/** 删除单条轮播图 */
export const deleteOne = (id: string) => defHttp.delete({ url: Api.deleteOne, params: { id } }, { joinParamsToUrl: true });

/** 批量删除轮播图 */
export const deleteBatch = (ids: string[]) => defHttp.delete({ url: Api.deleteBatch, params: { ids: ids.join(',') } }, { joinParamsToUrl: true });

/** 保存排序（只更新 sort 字段，不会覆盖图片与标题） */
export const saveSort = (sortList: Array<{ id: string; sort: number }>): Promise<ApiResult> =>
  defHttp.put({ url: Api.saveSort, params: sortList }, { isTransformResponse: false });
