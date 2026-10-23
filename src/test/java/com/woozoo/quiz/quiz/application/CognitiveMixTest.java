package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class CognitiveMixTest {

    @Test
    void 설정을_유형_순서대로_읽는다() {
        Map<CognitiveType, Integer> counts =
                CognitiveMix.parse("APPLY:3, REMEMBER:2,UNDERSTAND:2,EVALUATE:1,ANALYZE:2", 10);

        // 설정에 적은 순서와 상관없이 user 메시지에는 REMEMBER 부터 차례로 나간다
        assertThat(counts).containsExactly(
                entry(CognitiveType.REMEMBER, 2),
                entry(CognitiveType.UNDERSTAND, 2),
                entry(CognitiveType.APPLY, 3),
                entry(CognitiveType.ANALYZE, 2),
                entry(CognitiveType.EVALUATE, 1));
    }

    @Test
    void 적지_않은_유형은_빠진다() {
        assertThat(CognitiveMix.parse("REMEMBER:4,APPLY:6", 10))
                .containsOnlyKeys(CognitiveType.REMEMBER, CognitiveType.APPLY);
    }

    @Test
    void 합이_문제_수와_다르면_예외다() {
        assertThatThrownBy(() -> CognitiveMix.parse("REMEMBER:2,APPLY:3", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("합(5)");
    }

    @Test
    void 모르는_유형이나_잘못된_모양은_예외다() {
        assertThatThrownBy(() -> CognitiveMix.parse("CREATE:10", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CognitiveMix.parse("REMEMBER=10", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 같은_유형이_두_번이거나_개수가_0_이하면_예외다() {
        assertThatThrownBy(() -> CognitiveMix.parse("REMEMBER:5,REMEMBER:5", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CognitiveMix.parse("REMEMBER:10,APPLY:0", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
