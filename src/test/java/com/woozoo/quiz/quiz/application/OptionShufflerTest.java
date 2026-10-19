package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.port.GeneratedOption;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 시드를 고정한 Random 을 넣어 매번 같은 결과로 본다
class OptionShufflerTest {

    // AI 가 흔히 주는 모양처럼 정답이 늘 첫 보기다
    private static final List<GeneratedOption> ANSWER_FIRST = List.of(
            new GeneratedOption("정답", true),
            new GeneratedOption("오답 1", false),
            new GeneratedOption("오답 2", false),
            new GeneratedOption("오답 3", false));

    private final OptionShuffler shuffler = new OptionShuffler(new Random(42));

    @Test
    void 객관식은_여러_번_섞으면_정답이_모든_자리에_한_번씩은_온다() {
        Set<Integer> answerPositions = new HashSet<>();

        for (int i = 0; i < 50; i++) {
            List<GeneratedOption> shuffled = shuffler.shuffle(ANSWER_FIRST, QuizFormat.MULTIPLE_CHOICE);
            answerPositions.add(indexOfAnswer(shuffled));
        }

        assertThat(answerPositions).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void 섞어도_보기_내용과_정답은_그대로다() {
        List<GeneratedOption> shuffled = shuffler.shuffle(ANSWER_FIRST, QuizFormat.MULTIPLE_CHOICE);

        assertThat(shuffled).containsExactlyInAnyOrderElementsOf(ANSWER_FIRST);
    }

    @Test
    void 받은_리스트는_바꾸지_않는다() {
        shuffler.shuffle(ANSWER_FIRST, QuizFormat.MULTIPLE_CHOICE);

        assertThat(ANSWER_FIRST.getFirst().content()).isEqualTo("정답");
    }

    // 앱은 OX 를 O, X 순서로 보여 준다
    @Test
    void OX_는_순서를_바꾸지_않는다() {
        List<GeneratedOption> ox = List.of(new GeneratedOption("O", false), new GeneratedOption("X", true));

        for (int i = 0; i < 10; i++) {
            assertThat(shuffler.shuffle(ox, QuizFormat.OX)).containsExactlyElementsOf(ox);
        }
    }

    private int indexOfAnswer(List<GeneratedOption> options) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).isCorrect()) {
                return i;
            }
        }
        throw new AssertionError("정답이 없다");
    }
}
