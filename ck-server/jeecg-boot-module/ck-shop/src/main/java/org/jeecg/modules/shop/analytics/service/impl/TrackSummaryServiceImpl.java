package org.jeecg.modules.shop.analytics.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.shop.analytics.entity.TrackSummary;
import org.jeecg.modules.shop.analytics.mapper.TrackSummaryMapper;
import org.jeecg.modules.shop.analytics.service.ITrackSummaryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>
 * 用户行为汇总 Service 实现（聚合 + 漏斗回填）。
 * </p>
 *
 * <h3>幂等</h3>
 * 聚合统一用 {@code INSERT ... ON DUPLICATE KEY UPDATE}（按 {@code uk_summary} 覆盖，不累加），
 * 因此重复跑同一个时间桶结果一致。
 *
 * <h3>漏斗回填口径（与订单统计 GMV/实收严格一致）</h3>
 * <ul>
 *   <li>加购：{@code ck_shop_cart_event} 中 {@code ADD/INCR}，按下单（加购）时间；</li>
 *   <li>下单量 / 下单额：{@code ck_shop_order} 按 {@code create_time}；
 *       下单额 = 全部订单 {@code pay_amount} 之和（含未支付）；</li>
 *   <li>支付量 / 实收额：{@code ck_shop_order} 按 {@code pay_time}，{@code pay_status<>'UNPAID'}；
 *       实收额 = 已支付订单 {@code paid_amount} 之和（与订单统计实收口径一致）。</li>
 * </ul>
 * 商品维度（product）按下单/支付时间从订单明细 {@code ck_shop_order_item} 回填，下单量/支付量
 * 用去重订单数，金额用本行成交额 {@code actual_amount}（与订单统计热销排行口径一致）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Slf4j
@Service
public class TrackSummaryServiceImpl extends ServiceImpl<TrackSummaryMapper, TrackSummary>
        implements ITrackSummaryService {

    @Override
    public void aggregateRecent() {
        LocalDateTime now = LocalDateTime.now();

        // 日粒度：今天整天（幂等覆盖当天桶）
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        aggregateWindow("day", dayStart, dayEnd);

        // 小时粒度：最近 2 小时（覆盖当前小时 + 上一小时，防迟到事件漏掉）
        LocalDateTime hourEnd = now.withMinute(0).withSecond(0).withNano(0).plusHours(1);
        LocalDateTime hourStart = hourEnd.minusHours(2);
        aggregateWindow("hour", hourStart, hourEnd);
    }

    private void aggregateWindow(String granularity, LocalDateTime start, LocalDateTime end) {
        // 两种视角：all=true 产出 org_code='ALL' 全局行（超管视角）；false 产出 per-org 行（店铺视角）
        for (boolean all : new boolean[]{true, false}) {
            String org = orgExpr("e", "org_code", all);

            // 维度：global / store / product / region / source / device / page
            aggregate(granularity, start, end, "global", "page_view", org, "'ALL'", "'global'");
            aggregate(granularity, start, end, "store", "page_view", org, "'ALL'", coUnknown("e", "org_code"));
            aggregate(granularity, start, end, "product", "product_view", org,
                    coUnknown("e", "product_id"), coUnknown("e", "product_id"));
            // region 三级下钻：country / province / city
            aggregate(granularity, start, end, "region", "page_view", org, "'ALL'", regionCountry());
            aggregate(granularity, start, end, "region", "page_view", org, "'ALL'", regionProvince());
            aggregate(granularity, start, end, "region", "page_view", org, "'ALL'", regionCity());
            // source
            aggregate(granularity, start, end, "source", "page_view", org, "'ALL'", sourceExpr());
            // device 四子维度
            aggregate(granularity, start, end, "device", "page_view", org, "'ALL'", prefixed("device", "device_type"));
            aggregate(granularity, start, end, "device", "page_view", org, "'ALL'", prefixed("os", "os"));
            aggregate(granularity, start, end, "device", "page_view", org, "'ALL'", prefixed("browser", "browser"));
            aggregate(granularity, start, end, "device", "page_view", org, "'ALL'", prefixed("lang", "lang"));
            // page
            aggregate(granularity, start, end, "page", "page_view", org, "'ALL'", coUnknown("e", "page_path"));

            // 漏斗回填：global + product（/trend 与 /rank/product 需要）
            backfillFunnel(granularity, start, end, all);
        }
    }

    private void aggregate(String granularity, LocalDateTime start, LocalDateTime end,
                           String dimType, String pvEvent, String org, String product, String dimValue) {
        String bucket = bucketOf(granularity, "e", "event_time");
        try {
            baseMapper.aggregateTrack(dimType, granularity, pvEvent, start, end, org, product, dimValue, bucket);
        } catch (Exception e) {
            log.warn("[analytics] 聚合失败 dim={} granularity={}：{}", dimType, granularity, e.getMessage());
        }
    }

    private void backfillFunnel(String granularity, LocalDateTime start, LocalDateTime end, boolean all) {
        String ceOrg = orgExpr("ce", "org_code", all);
        String oOrg = orgExpr("o", "store_org_code", all);
        String iOrg = orgExpr("i", "store_org_code", all);

        String ceBucket = bucketOf(granularity, "ce", "create_time");
        String oCreateBucket = bucketOf(granularity, "o", "create_time");
        String oPayBucket = bucketOf(granularity, "o", "pay_time");

        try {
            baseMapper.backfillAddCart(granularity, start, end, ceOrg, "'ALL'", "global", "'global'", ceBucket);
            baseMapper.backfillAddCart(granularity, start, end, ceOrg,
                    coUnknown("ce", "product_id"), "product", coUnknown("ce", "product_id"), ceBucket);
            baseMapper.backfillOrderGlobal(granularity, start, end, oOrg, oCreateBucket);
            baseMapper.backfillPaidGlobal(granularity, start, end, oOrg, oPayBucket);
            baseMapper.backfillOrderProduct(granularity, start, end, iOrg, oCreateBucket);
            baseMapper.backfillPaidProduct(granularity, start, end, iOrg, oPayBucket);
        } catch (Exception e) {
            log.warn("[analytics] 漏斗回填失败 granularity={}：{}", granularity, e.getMessage());
        }
    }

    // ==================================================================
    // SQL 片段（内部白名单生成，非用户输入）
    // ==================================================================

    private String orgExpr(String alias, String column, boolean all) {
        return all ? "'ALL'" : co(alias, column);
    }

    private String bucketOf(String granularity, String alias, String column) {
        if ("hour".equals(granularity)) {
            return "DATE_FORMAT(" + alias + "." + column + ", '%Y-%m-%d %H:00:00')";
        }
        return "DATE_FORMAT(" + alias + "." + column + ", '%Y-%m-%d 00:00:00')";
    }

    private String co(String alias, String column) {
        return "COALESCE(NULLIF(" + alias + "." + column + ",''), '')";
    }

    private String coUnknown(String alias, String column) {
        return "COALESCE(NULLIF(" + alias + "." + column + ",''), 'unknown')";
    }

    private String prefixed(String prefix, String column) {
        return "CONCAT('" + prefix + ":', " + coUnknown("e", column) + ")";
    }

    private String regionCountry() {
        return "CONCAT('c:', " + coUnknown("e", "country_code") + ", '|', " + co("e", "country_name") + ")";
    }

    private String regionProvince() {
        return "CONCAT('p:', " + coUnknown("e", "country_code") + ", '|', " + co("e", "country_name")
                + ", '|', " + co("e", "province_name") + ")";
    }

    private String regionCity() {
        return "CONCAT('t:', " + coUnknown("e", "country_code") + ", '|', " + co("e", "country_name")
                + ", '|', " + co("e", "province_name") + ", '|', " + co("e", "city_name") + ")";
    }

    private String sourceExpr() {
        return "CASE "
                + "WHEN NULLIF(e.utm_source,'') IS NOT NULL THEN 'utm' "
                + "WHEN e.referrer IS NULL OR e.referrer = '' THEN 'direct' "
                + "WHEN e.referrer LIKE '%google.%' OR e.referrer LIKE '%bing.%' OR e.referrer LIKE '%baidu.%' "
                + "  OR e.referrer LIKE '%yahoo.%' OR e.referrer LIKE '%duckduckgo.%' OR e.referrer LIKE '%yandex.%' THEN 'search' "
                + "ELSE 'referral' END";
    }
}
