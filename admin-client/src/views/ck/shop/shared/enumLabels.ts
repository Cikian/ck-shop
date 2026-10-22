// 商城模块的枚举展示文案（清单 A22）。
//
// 背景：订单详情直接显示 `MOCK / WECHAT / ALIPAY`，加购统计显示
// `ADD / INCR / UPDATE / REMOVE / CLEAR` —— 运营看到的是裸枚举码，
// 要么猜、要么去翻代码。这里把"码 → 中文"的映射收敛到一处，
// 后端新增枚举值时只需在这里补一行。
//
// 约定：未知值**原样显示**，不要兜底成"未知"——排查问题时能看到真实值更重要。

/** 支付渠道：与 ck-shop 的 PayChannel 常量一一对应 */
export const PAY_CHANNEL_LABELS: Record<string, string> = {
  MOCK: '模拟支付',
  WECHAT: '微信支付',
  ALIPAY: '支付宝',
};

/** 支付方式（渠道内细分）；后端目前不强制枚举，未知值原样显示 */
export const PAY_TYPE_LABELS: Record<string, string> = {
  JSAPI: '公众号/小程序支付',
  NATIVE: '扫码支付',
  H5: 'H5 支付',
  APP: 'App 支付',
  BALANCE: '余额支付',
  CASH: '现金',
};

/** 加购行为：与后端 ShopCartEvent.TYPE_* 对应 */
export const CART_EVENT_LABELS: Record<string, string> = {
  ADD: '加入购物车',
  INCR: '再次加购',
  UPDATE: '修改数量',
  REMOVE: '删除单条',
  CLEAR: '清空购物车',
};

/** 下单来源：与后端 normalizeSource 的口径对应 */
export const ORDER_SOURCE_LABELS: Record<string, string> = {
  web: '网页端',
  h5: '移动端网页',
  api: '接口调用',
  mini: '小程序',
  app: 'App',
};

/**
 * 取枚举展示文案。
 * 先按原值匹配，再按大写/小写匹配，最后回退原值（未知值不隐藏）。
 */
export function enumLabel(map: Record<string, string>, value?: string | null): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const key = String(value);
  return map[key] ?? map[key.toUpperCase()] ?? map[key.toLowerCase()] ?? key;
}
