package com.roambupt.service.search;

import com.roambupt.service.search.support.RrfFusion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionTest {

    @Test
    void 两路都出现的文档_排在前面() {
        List<Long> keyword = List.of(1L, 2L, 3L);
        List<Long> vector = List.of(2L, 4L);

        List<Long> fused = RrfFusion.fuse(keyword, vector, 60);

        assertThat(fused.get(0)).isEqualTo(2L);
        assertThat(fused).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void 空列表_返回另一路() {
        assertThat(RrfFusion.fuse(List.of(), List.of(9L, 8L), 60)).containsExactly(9L, 8L);
        assertThat(RrfFusion.fuse(List.of(9L), List.of(), 60)).containsExactly(9L);
        assertThat(RrfFusion.fuse(List.of(), List.of(), 60)).isEmpty();
    }

    @Test
    void 分数相同_保持先出现的顺序() {
        List<Long> fused = RrfFusion.fuse(List.of(1L), List.of(2L), 60);
        // 两路各出现一次、名次相同，先来的 1 排前面
        assertThat(fused).containsExactly(1L, 2L);
    }
}
