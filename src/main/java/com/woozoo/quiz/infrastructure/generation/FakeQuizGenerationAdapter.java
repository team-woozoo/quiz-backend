package com.woozoo.quiz.infrastructure.generation;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.port.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

@Component
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

        int count = Math.min(request.questionCount(), chunks.size());
        List<GeneratedConcept> concepts = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            SourceChunk chunk = chunks.get(i);
            GeneratedQuestion question = switch (request.format()) {
                case OX -> oxQuestion(chunk, quotes.get(chunk.seq()));
                case MULTIPLE_CHOICE -> multipleChoiceQuestion(chunk, quotes);
            };
            concepts.add(new GeneratedConcept(
                    chunk.seq() + "번 청크 내용",
                    quotes.get(chunk.seq()),
                    List.of(chunk.seq()),
                    List.of(question)));
        }
        return new GeneratedQuiz(concepts);
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

    private GeneratedQuestion oxQuestion(SourceChunk chunk, String quote) {

        return new GeneratedQuestion("다음 내용은 강의자료와 맞다: " + quote,
                quote,
                CognitiveType.REMEMBER,
                "강의 자료에 그대로 나오는 문장이다",
                List.of(new GeneratedOption("O", true),
                        new GeneratedOption("X", false)),
                chunk.seq());
    }

    private GeneratedQuestion multipleChoiceQuestion(SourceChunk chunk, Map<Integer, String> quotes) {
        int optionCount = QuizFormat.MULTIPLE_CHOICE.optionCount();
        String answer = quotes.get(chunk.seq());

        List<String> wrongs = new ArrayList<>();

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

        int answerIndex = chunk.seq() % optionCount;

        options.add(answerIndex, new GeneratedOption(answer, true));

        return new GeneratedQuestion(
                "다음 중 강의자료에 나오는 문장은?",
                answer,
                CognitiveType.REMEMBER,
                "강의자료에 그대로 나오는 문장이다.",
                options,
                chunk.seq());
    }

    private String extractQuote(String content) {
        String firstLine = content.split("\\r?\\n")[0].strip();

        if (firstLine.length() > MAX_QUOTE_LENGTH) {
            return firstLine.substring(0, MAX_QUOTE_LENGTH).strip();
        }

        return firstLine;
    }
}