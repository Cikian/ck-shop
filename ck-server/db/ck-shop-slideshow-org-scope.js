// =============================================================================
// 首页轮播图 · 分店铺管理（数据迁移）
//
// 背景：
//   改造前轮播图是**全局共享**的：slideshow 集合里只有一份数据，任何店铺的前台都读它。
//   改造后按 `sys_org_code` 隔离（店铺 ↔ 部门 1:1），与商品/分类同一套维度。
//   存量文档没有归属列，等于"哪个店铺都读不到"，因此统一归入默认店铺绑定的部门。
//
// 幂等：只处理"缺归属列 / 归属为空"的文档，可重复执行。
//
// 用法（mongosh）：
//   mongosh "mongodb://ck-saas:<pwd>@127.0.0.1:27017/ck-saas?authSource=ck-saas" ck-server/db/ck-shop-slideshow-org-scope.js
// 或在 DBX / mongosh 里直接执行下面的语句。
// =============================================================================

// default 店铺绑定的部门（数码部）。若默认店铺换了部门，这里要同步改。
const DEFAULT_ORG_CODE = 'A06A01';

const migrated = db.slideshow.updateMany(
  {
    $or: [
      { sys_org_code: { $exists: false } },
      { sys_org_code: null },
      { sys_org_code: '' },
    ],
  },
  { $set: { sys_org_code: DEFAULT_ORG_CODE } }
);
print('已补归属的轮播图条数: ' + migrated.modifiedCount);

// 查询模式固定为 (sys_org_code[, enabled])，建个复合索引；集合很小，纯属规范。
db.slideshow.createIndex({ sys_org_code: 1, enabled: 1 });

print('当前各店铺轮播图条数：');
printjson(db.slideshow.aggregate([
  { $group: { _id: '$sys_org_code', count: { $sum: 1 } } },
  { $sort: { _id: 1 } },
]).toArray());
