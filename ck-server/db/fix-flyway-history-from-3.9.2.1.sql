-- ==========================================================================================
-- 一次性修复脚本（已在 2026-09-20 对本机 ck-shop 库执行完毕，请勿重复执行）
--
-- 现象：
--   后端启动时 Flyway 报
--   Duplicate entry '1890213291321749505' for key 'PRIMARY'  (V3.8.0_1__airag_add_menu.sql)
--   Migration of schema `ck-shop` to version "3.8.0.1 - airag add menu" failed!
--
-- 原因：
--   ck-shop 库是从一个已经升级到 3.9.2.1 的库导入的（数据、表结构都是 3.9.2.1 状态），
--   但 flyway_schema_history 没有一起导入，只剩 Flyway 自己写入的一条 BASELINE(1)。
--   Flyway 因此认为 3.8.0.1 之后的所有脚本都没执行过，从第一条开始重放，于是主键冲突。
--   而 FlywayConfig 捕获了 FlywayException 只打日志，所以后端其实能启动，
--   但库结构一直停留在 3.9.2.1，3.9.2.2 / 3.9.5.0 的字段和表始终缺失。
--
-- 处理办法：
--   1) 备份 flyway_schema_history、airag_app.share_token
--   2) 删除 success=0 的失败记录、删除遗留表 flyway_schema_history_1
--   3) 依据官方 db/ck-server-mysql-5.7.sql 中的 flyway_schema_history，
--      把 3.8.0.1 ~ 3.9.2.1 这 17 条记录补成“已执行”（checksum 与本仓库脚本逐条核对一致）
--   4) 删除曾经手工添加过的 airag_app.share_token 及其唯一索引（它属于 V3.9.5_0 的内容，
--      不删掉的话 V3.9.5_0 会报 Duplicate column name 'share_token'）
--   然后正常启动后端，Flyway 会自动应用 V3.9.2_2、V3.9.5_0、V3.9.5_1。
--
-- 注意：第 4 步的 DROP 语句只适用于“手工加过 share_token”的库；没有该列的环境请跳过。
-- ==========================================================================================

-- 1. 备份
DROP TABLE IF EXISTS `flyway_schema_history_bak`;
CREATE TABLE `flyway_schema_history_bak` AS SELECT * FROM `flyway_schema_history`;

DROP TABLE IF EXISTS `airag_app_share_token_bak`;
CREATE TABLE `airag_app_share_token_bak` AS SELECT id, share_token FROM `airag_app`;

-- 2. 清理失败记录与遗留表
DELETE FROM `flyway_schema_history` WHERE success = 0;
DROP TABLE IF EXISTS `flyway_schema_history_1`;

-- 3. 标记 3.8.0.1 ~ 3.9.2.1 为已执行（checksum 取自官方 base 脚本，与本地脚本一致）
INSERT INTO `flyway_schema_history` VALUES (2, '3.8.0.1', 'airag add menu', 'SQL', 'V3.8.0_1__airag_add_menu.sql', -177373739, 'root', '2025-04-03 10:54:32', 114, 1);
INSERT INTO `flyway_schema_history` VALUES (3, '3.8.0.2', 'airag init db', 'SQL', 'V3.8.0_2__airag_init_db.sql', 874980827, 'root', '2025-04-07 14:35:13', 60, 1);
INSERT INTO `flyway_schema_history` VALUES (4, '3.8.1.1', 'all upgrade', 'SQL', 'V3.8.1_1__all_upgrade.sql', 670374510, 'root', '2025-06-25 15:09:03', 25, 1);
INSERT INTO `flyway_schema_history` VALUES (5, '3.8.1.2', 'openapi', 'SQL', 'V3.8.1_2__openapi.sql', 453642872, 'root', '2025-07-02 10:11:50', 245, 1);
INSERT INTO `flyway_schema_history` VALUES (6, '3.8.2.1', 'all upgrade', 'SQL', 'V3.8.2_1__all_upgrade.sql', 1279027750, 'root', '2025-07-30 18:13:06', 23, 1);
INSERT INTO `flyway_schema_history` VALUES (7, '3.8.3.0', 'all upgrade', 'SQL', 'V3.8.3_0__all_upgrade.sql', 1420195670, 'root', '2025-09-13 17:06:41', 22, 1);
INSERT INTO `flyway_schema_history` VALUES (8, '3.8.3.1', 'upgrade jimubi', 'SQL', 'V3.8.3_1__upgrade_jimubi.sql', -1274458791, 'root', '2025-11-25 15:42:34', 13, 1);
INSERT INTO `flyway_schema_history` VALUES (9, '3.9.0.0', 'all upgrade', 'SQL', 'V3.9.0_0__all_upgrade.sql', -758666487, 'root', '2025-11-26 13:40:20', 48, 1);
INSERT INTO `flyway_schema_history` VALUES (10, '3.9.0.1', 'mcp demo', 'SQL', 'V3.9.0_1__mcp_demo.sql', -790563395, 'root', '2025-11-27 18:16:00', 18, 1);
INSERT INTO `flyway_schema_history` VALUES (11, '3.9.0.2', 'upd dep category', 'SQL', 'V3.9.0_2__upd_dep_category.sql', -71250240, 'root', '2025-11-27 18:45:48', 19, 1);
INSERT INTO `flyway_schema_history` VALUES (12, '3.9.0.3', 'add aiflow permission', 'SQL', 'V3.9.0_3__add_aiflow_permission.sql', 1502182637, 'root', '2025-12-01 15:13:59', 9, 1);
INSERT INTO `flyway_schema_history` VALUES (13, '3.9.0.4', 'add onlineuser perms', 'SQL', 'V3.9.0_4__add_onlineuser_perms.sql', -1048887238, 'root', '2026-01-22 09:46:14', 23, 1);
INSERT INTO `flyway_schema_history` VALUES (14, '3.9.1.0', 'all upgrade', 'SQL', 'V3.9.1_0__all_upgrade.sql', -498300865, 'root', '2026-01-28 15:19:13', 65, 1);
INSERT INTO `flyway_schema_history` VALUES (15, '3.9.1.1', 'add aiapp img gen', 'SQL', 'V3.9.1_1__add_aiapp_img_gen.sql', 1451785654, 'root', '2026-01-28 15:19:42', 21, 1);
INSERT INTO `flyway_schema_history` VALUES (16, '3.9.1.2', 'add aiwriteblog', 'SQL', 'V3.9.1_2__add_aiwriteblog.sql', -331573873, 'root', '2026-04-10 20:47:55', 20, 1);
INSERT INTO `flyway_schema_history` VALUES (17, '3.9.2.0', 'all upgrade', 'SQL', 'V3.9.2_0__all_upgrade.sql', -1769021348, 'root', '2026-05-11 16:21:41', 48, 1);
INSERT INTO `flyway_schema_history` VALUES (18, '3.9.2.1', 'upgrade jimureport', 'SQL', 'V3.9.2_1__upgrade_jimureport.sql', -1890687377, 'root', '2026-06-27 22:58:34', 45, 1);

-- 4. 移除手工添加的 share_token（只适用于手工加过该列的库）
ALTER TABLE `airag_app` DROP INDEX `idx_airag_app_share_token`;
ALTER TABLE `airag_app` DROP COLUMN `share_token`;

-- 5. 确认
SELECT installed_rank, version, description, success FROM `flyway_schema_history` ORDER BY installed_rank;
