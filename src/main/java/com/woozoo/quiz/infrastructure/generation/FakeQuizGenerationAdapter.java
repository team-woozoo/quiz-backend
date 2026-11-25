package com.woozoo.quiz.infrastructure.generation;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.port.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

// 로컬과 테스트의 기본 생성기다. AI 를 부르지 않아 비용이 들지 않는다
@Component
@ConditionalOnProperty(name = "quiz.generation.generator", havingValue = "fake")
public class FakeQuizGenerationAdapter implements QuizGenerationPort {

    private static final int MAX_QUOTE_LENGTH = 100;

    private final Duration delay;

    // 결과를 바로 주면 앱에서 생성 중 화면이 순식간에 지나가 확인할 수 없다. 실제 AI 처럼 기다렸다가 준다
    public FakeQuizGenerationAdapter(@Value("${quiz.generation.fake-delay:0s}") Duration delay) {
        this.delay = delay;
    }

    @Override
    public GeneratedQuiz generateQuiz(QuizGenerationRequest request) {
        waitForDelay();
        List<SourceChunk> chunks = request.chunks();

        Map<Integer, String> quotes = new LinkedHashMap<>();

        for (SourceChunk chunk : chunks) {
            int seq = chunk.seq();
            String quote = extractQuote(chunk.content());
            quotes.put(seq, quote);
        }

        // 실제 생성기처럼 받은 개념의 번호로 가리키고, 근거 청크는 그 개념의 페이지에서 고른다.
        // 약점 퀴즈는 겨냥한 개념 하나가 보낸 페이지를 모두 가져 모든 문제가 1번이 된다.
        // 한 청크가 여러 개념에 들어 있으면 앞 번호를 쓰고, 개념에 속하지 않은 청크(표지 등)로는 문제를 내지 않는다
        Map<Integer, Integer> conceptNoBySeq = new HashMap<>();
        for (AssignedConcept concept : request.assignedConcepts()) {
            for (int seq : concept.chunkSeqs()) {
                conceptNoBySeq.putIfAbsent(seq, concept.no());
            }
        }

        List<GeneratedQuestion> questions = new ArrayList<>();
        for (SourceChunk chunk : chunks) {
            if (questions.size() >= request.questionCount()) {
                break;
            }
            Integer conceptNo = conceptNoBySeq.get(chunk.seq());
            if (conceptNo == null) {
                continue;
            }
            questions.add(switch (request.format()) {
                case OX -> oxQuestion(conceptNo, chunk, quotes.get(chunk.seq()));
                case MULTIPLE_CHOICE -> multipleChoiceQuestion(conceptNo, chunk, quotes);
            });
        }
        return new GeneratedQuiz(questions);
    }

    private void waitForDelay() {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QuizGenerationException("가짜 생성기가 기다리는 중에 중단됐다", e);
        }
    }

    private GeneratedQuestion oxQuestion(int conceptNo, SourceChunk chunk, String quote) {

        return new GeneratedQuestion(conceptNo,
                "다음 내용은 강의자료와 맞다: " + quote,
                quote,
                CognitiveType.REMEMBER,
                "강의 자료에 그대로 나오는 문장이다",
                List.of(new GeneratedOption("O", true),
                        new GeneratedOption("X", false)),
                chunk.seq());
    }

    private GeneratedQuestion multipleChoiceQuestion(int conceptNo, SourceChunk chunk, Map<Integer, String> quotes) {
        int optionCount = QuizFormat.MULTIPLE_CHOICE.optionCount();
        String answer = quotes.get(chunk.seq());

        List<String> wrongs = new ArrayList<>();

        // 슬라이드마다 제목이 반복되면 다른 청크의 인용문이 정답과 같을 수 있다. 그대로 쓰면 정답이 둘이 된다
        for (String wrong : quotes.values()) {
            if (answer.equals(wrong) || wrongs.contains(wrong)) {
                continue;
            }
            wrongs.add(wrong);

            if (wrongs.size() >= optionCount - 1) break;
        }

        while (wrongs.size() < optionCount - 1) {
            wrongs.add("강의자료에 없는 내용입니다 " + (wrongs.size() + 1));
        }

        List<GeneratedOption> options = new ArrayList<>();

        for (String wrong : wrongs) {
            options.add(new GeneratedOption(wrong, false));
        }

        // 정답이 늘 같은 자리에 있으면 앱에서 채점 화면을 확인하기 어려워 seq 로 자리를 돌린다
        int answerIndex = chunk.seq() % optionCount;

        options.add(answerIndex, new GeneratedOption(answer, true));

        return new GeneratedQuestion(
                conceptNo,
                "다음 중 강의자료에 나오는 문장은?",
                answer,
                CognitiveType.REMEMBER,
                "강의자료에 그대로 나오는 문장이다.",
                options,
                chunk.seq());
    }

    // 원문을 그대로 잘라 써서 인용문 검사를 늘 통과한다. 첫 줄은 대개 슬라이드 제목이라 짧다
    private String extractQuote(String content) {
        String firstLine = content.split("\\r?\\n")[0].strip();

        if (firstLine.length() > MAX_QUOTE_LENGTH) {
            return firstLine.substring(0, MAX_QUOTE_LENGTH).strip();
        }

        return firstLine;
    }
}