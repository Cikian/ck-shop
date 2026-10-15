// .probe/DumpShopSchema.java
//
// 从本机运行中的 MySQL 导出 ck_shop_* / sk_shop_* 的建表语句，生成
// ck-server/db/ck-shop-schema-baseline.sql（新环境可用的基线 schema）。
//
// 为什么要它：仓库里 34 张商城表中有 18 张**任何 SQL 脚本里都没有 CREATE TABLE**
// （产品/分类/标签/促销/优惠券/选项等），全新环境按现有脚本建不出库。
// 这里以**运行中的库**为准导出，属于权威口径。
//
// 口令只从 ck-server/.env 读取，不打印、不落盘。
// 运行：
//   java -cp <mysql-connector.jar> .probe/DumpShopSchema.java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DumpShopSchema {

    public static void main(String[] args) throws Exception {
        Path envPath = Paths.get("ck-server/.env");
        Map<String, String> env = readEnv(envPath);

        String host = env.getOrDefault("MYSQL_HOST", "127.0.0.1");
        String port = env.getOrDefault("MYSQL_PORT", "3306");
        String db = env.getOrDefault("MYSQL_DATABASE", "ck-shop");
        String user = env.getOrDefault("MYSQL_USERNAME", "root");
        String pass = env.getOrDefault("MYSQL_PASSWORD", "");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"
                + "&serverTimezone=Asia/Shanghai&tinyInt1isBit=false";

        List<String> ddl = new ArrayList<>();
        List<String> tables = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = ? AND (table_name LIKE 'ck\\_shop%' OR table_name LIKE 'sk\\_shop%') "
                            + "ORDER BY table_name")) {
                ps.setString(1, db);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tables.add(rs.getString(1));
                    }
                }
            }
            for (String t : tables) {
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + t + "`")) {
                    if (rs.next()) {
                        String create = rs.getString(2);
                        ddl.add(normalize(create));
                    }
                }
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("-- =============================================================================\n");
        out.append("-- ck-shop 商城表基线 schema（自动生成，请勿手工编辑）\n");
        out.append("--\n");
        out.append("-- 生成来源：运行中的 MySQL 库 `").append(db).append("` 的 information_schema（SHOW CREATE TABLE）\n");
        out.append("-- 生成时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        out.append("-- 覆盖范围：").append(tables.size()).append(" 张表（ck_shop_* 与 sk_shop_product_tag_relation）\n");
        out.append("--\n");
        out.append("-- 为什么需要这个文件：\n");
        out.append("--   仓库里此前只有部分表的 CREATE TABLE（订单/购物车/收藏等），而商品、分类、标签、\n");
        out.append("--   促销、优惠券、options 等 18 张表**只在运行库里存在**，任何脚本里都找不到建表语句 ——\n");
        out.append("--   结果就是「全新环境按仓库脚本建不出可用库」，只能靠人工从老库导出。\n");
        out.append("--\n");
        out.append("-- 使用方式：\n");
        out.append("--   1) 先导入 JeecgBoot 基础库脚本（ck-server/db/ck-server-mysql-5.7.sql），它会建 sys_* 等系统表；\n");
        out.append("--   2) 再执行本文件。所有语句都是 CREATE TABLE IF NOT EXISTS，可重复执行、不会覆盖已有数据；\n");
        out.append("--   3) 后续增量变更仍走 Flyway（flyway/sql/mysql/V*.sql），不要直接改本文件。\n");
        out.append("--\n");
        out.append("-- 与其它脚本的关系：\n");
        out.append("--   ck-shop-order.sql / ck-shop-cart.sql / ck-shop-favorite.sql 里同名表的建表语句与本文件等价，\n");
        out.append("--   本文件是它们的**超集**（补齐了缺失的 18 张表），新环境只需要执行本文件。\n");
        out.append("-- =============================================================================\n\n");

        for (String s : ddl) {
            out.append(s).append(";\n\n");
        }

        Path target = Paths.get("ck-server/db/ck-shop-schema-baseline.sql");
        Files.createDirectories(target.getParent());
        Files.write(target, out.toString().getBytes(StandardCharsets.UTF_8));

        System.out.println("tables=" + tables.size());
        System.out.println("bytes=" + out.length());
        System.out.println("DDL中的IF NOT EXISTS数量=" + countOccurrences(out.toString(), "CREATE TABLE IF NOT EXISTS"));
        System.out.println("target=" + target.toAbsolutePath());
        System.out.println("前5张表=" + tables.subList(0, Math.min(5, tables.size())));
    }

    /** CREATE TABLE -> CREATE TABLE IF NOT EXISTS，并去掉会随环境变化的 AUTO_INCREMENT 计数 */
    private static String normalize(String create) {
        String s = create.replaceFirst("(?i)^CREATE TABLE ", "CREATE TABLE IF NOT EXISTS ");
        s = s.replaceAll(" AUTO_INCREMENT=\\d+", "");
        return s;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static Map<String, String> readEnv(Path path) throws IOException {
        Map<String, String> map = new HashMap<>();
        if (!Files.exists(path)) {
            return map;
        }
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                continue;
            }
            int i = line.indexOf('=');
            map.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
        }
        return map;
    }
}
