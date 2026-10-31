package org.jeecg.modules.shop.analytics.geo;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Subdivision;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.shop.analytics.config.AnalyticsProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

/**
 * <p>
 * GeoLite2 City（离线 mmdb）解析器。
 * </p>
 *
 * <p>启动时把 {@code GeoLite2-City.mmdb} 加载进内存，采集写入时即时解析出
 * country/province/city；mmdb 文件**不进 Git**（走部署注入）。缺失/加载失败时
 * 降级为 null（不阻塞启动、不影响采集），原始 IP 仍照常落库（本期不脱敏）。
 *
 * @author Cikian
 * @version 1.0
 * @since 2026-10-30
 */
@Slf4j
@Component
public class GeoResolver {

    private final AnalyticsProperties properties;

    private volatile DatabaseReader reader;

    public GeoResolver(AnalyticsProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        String path = StringUtils.trimToNull(properties.getGeoMmdbPath());
        if (path == null) {
            log.warn("[analytics] 未配置 ck.shop.analytics.geo-mmdb-path，地域解析降级为 null");
            return;
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            log.warn("[analytics] GeoLite2 mmdb 文件不存在：{}，地域解析降级为 null", path);
            return;
        }
        try {
            this.reader = new DatabaseReader.Builder(file).build();
            log.info("[analytics] GeoLite2-City.mmdb 已加载：{}", path);
        } catch (IOException e) {
            log.warn("[analytics] GeoLite2 加载失败，地域解析降级为 null：{}", e.getMessage());
            this.reader = null;
        }
    }

    /**
     * 解析 IP → 地域；解析失败/未加载/非法 IP 一律返回 null（调用方降级，不阻塞采集）。
     */
    public GeoResult resolve(String ip) {
        if (StringUtils.isBlank(ip)) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            // 本地/内网地址：mmdb 永远解析不出国家（127.0.0.1、::1、10.x、192.168.x…），
            // 直接返回「本地/内网」标记，否则地域分布页在本地开发/内网访问时整页 "-"，看起来像坏了。
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                GeoResult local = new GeoResult();
                local.setCountryCode("LOCAL");
                local.setCountryName("本地/内网");
                return local;
            }
            if (reader == null) {
                return null;
            }
            CityResponse response = reader.city(address);
            Country country = response.getCountry();
            Subdivision subdivision = response.getMostSpecificSubdivision();
            City city = response.getCity();

            GeoResult result = new GeoResult();
            result.setCountryCode(country == null ? null : country.getIsoCode());
            result.setCountryName(country == null ? null : country.getName());
            result.setProvinceCode(subdivision == null ? null : subdivision.getIsoCode());
            result.setProvinceName(subdivision == null ? null : subdivision.getName());
            result.setCityName(city == null ? null : city.getName());
            return result;
        } catch (Exception e) {
            // 非法 IP、mmdb 查询异常等：按 null 处理，绝不阻塞采集链路
            return null;
        }
    }

    @Data
    public static class GeoResult {
        private String countryCode;
        private String countryName;
        private String provinceCode;
        private String provinceName;
        private String cityName;
    }
}
