package org.jeecg.modules.shop.order.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * <p>
 * 金额计算工具 —— 本项目**唯一**允许做金额运算的入口。
 * </p>
 *
 * <h3>为什么不让各处直接 multiply/add</h3>
 * 文档里"金额精度：用 double 算钱，元/分转换错误，对账差 1 分"是真实高频事故。
 * 只有把所有金额运算收敛到一个类里，才能保证：
 * <ul>
 *   <li>一律 {@link BigDecimal}，绝不出现 {@code double}；</li>
 *   <li>一律 2 位小数、{@link RoundingMode#HALF_UP}（与财务口径一致，
 *       且与 MySQL {@code decimal(12,2)} 的舍入行为一致，避免"Java 算出来 1.005、
 *       存进库变成 1.00"这种库内外不一致）；</li>
 *   <li>null 一律当 0 —— 但**只在"必须容错"的累加场景**；
 *       参与下单的核心金额若为 null，调用方必须先校验，而不是靠这里兜底。</li>
 * </ul>
 *
 * <h3>舍入时机</h3>
 * 乘除法（折扣率、分摊）会产生多于 2 位的小数，必须在**每一步之后立即**收敛到 2 位。
 * 若把中间结果一路带着高精度往下算，最终结果会与"每步 2 位"的口径不一致，
 * 而在多行订单里这种差异会累积成可观的偏差。本类提供的 divide 系列默认每一步都收敛。
 *
 * @author Cikian
 * @since 2026-10-14
 */
public final class MoneyUtils {

    /** 金额小数位，与 DDL 的 decimal(12,2) 严格对齐 */
    public static final int SCALE = 2;

    /** 重量小数位，与 DDL 的 decimal(12,3) 对齐 */
    public static final int WEIGHT_SCALE = 3;

    private MoneyUtils() {
    }

    /** 归一到 2 位、HALF_UP；null → 0.00 */
    public static BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** 归一到 2 位、HALF_UP；null 保持 null（用于"可空金额"字段，如划线价） */
    public static BigDecimal scaleOrNull(BigDecimal value) {
        return value == null ? null : scale(value);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static boolean isZero(BigDecimal value) {
        return scale(value).compareTo(BigDecimal.ZERO) == 0;
    }

    public static boolean isPositive(BigDecimal value) {
        return scale(value).compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isNegative(BigDecimal value) {
        return scale(value).compareTo(BigDecimal.ZERO) < 0;
    }

    /** 是否相等（按分比较，避免 BigDecimal.equals 受 scale 影响：1.0 与 1.00 在 equals 下不相等） */
    public static boolean eq(BigDecimal a, BigDecimal b) {
        return scale(a).compareTo(scale(b)) == 0;
    }

    public static boolean gt(BigDecimal a, BigDecimal b) {
        return scale(a).compareTo(scale(b)) > 0;
    }

    public static boolean gte(BigDecimal a, BigDecimal b) {
        return scale(a).compareTo(scale(b)) >= 0;
    }

    public static boolean lt(BigDecimal a, BigDecimal b) {
        return scale(a).compareTo(scale(b)) < 0;
    }

    /** 求和，null 视为 0 */
    public static BigDecimal add(BigDecimal... values) {
        BigDecimal sum = BigDecimal.ZERO;
        if (values != null) {
            for (BigDecimal value : values) {
                if (value != null) {
                    sum = sum.add(value);
                }
            }
        }
        return scale(sum);
    }

    /** a - b，null 视为 0 */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        BigDecimal left = a == null ? BigDecimal.ZERO : a;
        BigDecimal right = b == null ? BigDecimal.ZERO : b;
        return scale(left.subtract(right));
    }

    /** 单价 × 数量，null 视为 0 */
    public static BigDecimal multiply(BigDecimal price, Integer quantity) {
        if (price == null || quantity == null || quantity == 0) {
            return zero();
        }
        return scale(price.multiply(BigDecimal.valueOf(quantity)));
    }

    /**
     * 按比例计算金额：{@code base × rate}，rate 为 0~1 的小数（如 0.15 表示 15%）。
     * 每一步立即收敛到 2 位。
     */
    public static BigDecimal multiply(BigDecimal base, BigDecimal rate) {
        if (base == null || rate == null) {
            return zero();
        }
        return scale(base.multiply(rate));
    }

    /**
     * 按金额比例把 {@code total} 分摊到 {@code weights} 上，保证**分摊结果之和恰好等于 total**。
     *
     * <p>这是优惠券/满减/运费在多行之间分摊的标准做法。朴素做法
     * {@code 每行 = total × 行占比} 会因为逐行独立舍入而导致"分摊之和 ≠ 优惠总额"，
     * 那几分钱最终会变成对账差异。这里用"最大余额法"：
     * 先按截断（DOWN）分配，再把差额优先补给"被舍掉最多"的行。
     *
     * @param total   待分摊总额（非负）
     * @param weights 各行的权重（通常为行金额），长度需与返回数组一致
     * @return 与 weights 等长的分摊金额数组，其和严格等于 {@code scale(total)}
     */
    public static BigDecimal[] allocate(BigDecimal total, BigDecimal[] weights) {
        BigDecimal pool = scale(total);
        int n = weights == null ? 0 : weights.length;
        BigDecimal[] result = new BigDecimal[n];
        if (n == 0) {
            return result;
        }
        if (pool.compareTo(BigDecimal.ZERO) == 0) {
            for (int i = 0; i < n; i++) {
                result[i] = zero();
            }
            return result;
        }

        BigDecimal weightSum = BigDecimal.ZERO;
        for (BigDecimal w : weights) {
            weightSum = weightSum.add(w == null ? BigDecimal.ZERO : w);
        }
        // 权重全为 0（例如所有商品都免费）：把总额全给第一行，避免除以 0 与"钱凭空消失"
        if (weightSum.compareTo(BigDecimal.ZERO) == 0) {
            for (int i = 0; i < n; i++) {
                result[i] = zero();
            }
            result[0] = pool;
            return result;
        }

        BigDecimal allocated = BigDecimal.ZERO;
        BigDecimal[] remainders = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            BigDecimal w = weights[i] == null ? BigDecimal.ZERO : weights[i];
            BigDecimal exact = pool.multiply(w).divide(weightSum, 6, RoundingMode.HALF_UP);
            BigDecimal floor = exact.setScale(SCALE, RoundingMode.DOWN);
            result[i] = floor;
            remainders[i] = exact.subtract(floor);
            allocated = allocated.add(floor);
        }

        // 差额（一定 ≥ 0 且 < n 分）按余数从大到小补，保证"分摊之和 == total"
        BigDecimal gap = pool.subtract(allocated);
        BigDecimal oneCent = BigDecimal.valueOf(0.01);
        while (gap.compareTo(BigDecimal.ZERO) > 0) {
            int best = -1;
            BigDecimal bestRemainder = BigDecimal.valueOf(-1);
            for (int i = 0; i < n; i++) {
                if (remainders[i].compareTo(bestRemainder) > 0) {
                    bestRemainder = remainders[i];
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            result[best] = result[best].add(oneCent);
            remainders[best] = BigDecimal.valueOf(-1);
            gap = gap.subtract(oneCent);
        }
        return result;
    }

    /**
     * 安全扣减：{@code base - amount}，结果不小于 0。
     *
     * <p>用于"优惠金额不能把商品金额减成负数"的兜底。出现负数说明促销配置或计算出了 bug，
     * 这里保守截断到 0 并让调用方记录告警，好过把负金额写进订单。
     */
    public static BigDecimal subtractNotBelowZero(BigDecimal base, BigDecimal amount) {
        BigDecimal result = subtract(base, amount);
        return result.compareTo(BigDecimal.ZERO) < 0 ? zero() : result;
    }
}
