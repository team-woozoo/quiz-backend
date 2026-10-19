package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.port.GeneratedOption;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Component
public class OptionShuffler {

    private final Random random;

    public OptionShuffler() {          // Spring 이 쓰는 생성자
        this(new Random());
    }

    OptionShuffler(Random random) {    // 테스트가 시드를 고정해 넣는 생성자
        this.random = random;
    }

    // 객관식 보기를 섞은 새 리스트를 준다. OX 는 O, X 순서를 그대로 둔다
    List<GeneratedOption> shuffle(List<GeneratedOption> options, QuizFormat format) {
        if (format != QuizFormat.MULTIPLE_CHOICE) {
            return options;
        }
        List<GeneratedOption> shuffled = new ArrayList<>(options);
        Collections.shuffle(shuffled, random);
        return shuffled;
    }
}

