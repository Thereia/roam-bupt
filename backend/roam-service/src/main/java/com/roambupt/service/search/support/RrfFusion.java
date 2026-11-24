package com.roambupt.service.search.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF（Reciprocal Rank Fusion）：把两路召回按排名融合。
 *
 * <p>score(d) = Σ 1 / (k + rank)，k 常取 60。只依赖排名，不要求两路分数可比。</p>
 */
public final class RrfFusion {

    private RrfFusion() {
    }

    public static List<Long> fuse(List<Long> first, List<Long> second, int k) {
        return fuse(k, new double[]{1.0, 1.0}, first, second);
    }

    /** 多路加权融合：score(d) = Σ w_i / (k + rank_i)。权重靠评测集定，不拍脑袋。 */
    @SafeVarargs
    public static List<Long> fuse(int k, double[] weights, List<Long>... ranked) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        for (int i = 0; i < ranked.length; i++) {
            double weight = weights != null && i < weights.length ? weights[i] : 1.0;
            accumulate(scores, ranked[i], k, weight);
        }
        List<Map.Entry<Long, Double>> entries = new ArrayList<>(scores.entrySet());
        // 分数相同保持先出现的顺序，保证结果稳定
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<Long> fused = new ArrayList<>(entries.size());
        for (Map.Entry<Long, Double> entry : entries) {
            fused.add(entry.getKey());
        }
        return fused;
    }

    private static void accumulate(Map<Long, Double> scores, List<Long> ranked, int k, double weight) {
        if (ranked == null) {
            return;
        }
        for (int i = 0; i < ranked.size(); i++) {
            Long id = ranked.get(i);
            if (id == null) {
                continue;
            }
            scores.merge(id, weight / (k + i + 1), Double::sum);
        }
    }
}
