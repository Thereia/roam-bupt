package com.roambupt.service.space.traffic;

/**
 * 拥挤度 → 权重放大系数。纯函数。
 *
 * <pre>
 * u = count / (max + 1)           // 用 max+1 做分母，最忙的边也不会到 1
 * factor = 1 + k * u / (1 - u)    // 漏桶式放大
 * </pre>
 */
public final class CongestionModel {

    private CongestionModel() {
    }

    public static double factor(long count, long max, double k) {
        if (count <= 0) {
            return 1.0;
        }
        double u = (double) count / (Math.max(max, count) + 1);
        return 1.0 + k * u / (1.0 - u);
    }
}
