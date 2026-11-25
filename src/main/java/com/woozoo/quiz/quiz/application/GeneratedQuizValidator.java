package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.port.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GeneratedQuizValidator {

    // 인용의 떨어진 조각을 잇는 표시다. 원문의 URL 이나 경로의 / 와 헷갈리지 않게 앞뒤 공백까지 본다
    private static final Pattern QUOTE_PART_SEPARATOR = Pattern.compile("\\s+/\\s+");
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]");

    // 문제를 버린 이유. 프롬프트를 고칠 때 어느 규칙에서 많이 떨어지는지 보려고 센다
    enum Rejection {
        NULL_QUESTION,
        UNKNOWN_CONCEPT,
        OPTION_COUNT,
        MISSING_FIELD,
        ANSWER_COUNT,
        DUPLICATE_OPTION,
        SOURCE_NOT_IN_CONCEPT,
        QUOTE_NOT_IN_SOURCE
    }

    // 생성기는 개념을 만들지 않고 요청에 보낸 개념을 번호로 가리킨다. 그래서 개념을 다듬을 일 없이 문제 하나하나만 본다.
    // 약점 퀴즈도 겨냥한 개념 하나를 1번으로 보내므로 같은 규칙으로 걸러진다
    public GeneratedQuiz validate(QuizGenerationRequest request, GeneratedQuiz quiz) {
        // 원문은 청크마다 한 번만 정리해 둔다. 그래서 isQuoteInSource 에서는 인용문만 정리한다
        Map<Integer, String> normalizedContentBySeq = new HashMap<>();
        for (SourceChunk chunk : request.chunks()) {
            normalizedContentBySeq.put(chunk.seq(), normalize(chunk.content()));
        }
        Map<Integer, AssignedConcept> conceptByNo = new HashMap<>();
        for (AssignedConcept concept : request.assignedConcepts()) {
            conceptByNo.put(concept.no(), concept);
        }

        // 이 컴포넌트는 여러 작업이 같이 쓰므로 개수는 필드가 아니라 호출마다 센다
        Map<Rejection, Integer> rejected = new EnumMap<>(Rejection.class);
        Map<CognitiveType, Integer> rejectedTypes = new EnumMap<>(CognitiveType.class);
        Map<CognitiveType, Integer> trimmedTypes = new EnumMap<>(CognitiveType.class);

        List<GeneratedQuestion> received = nullToEmpty(quiz.questions());
        List<GeneratedQuestion> validQuestions = new ArrayList<>();
        for (GeneratedQuestion question : received) {
            Optional<Rejection> rejection =
                    findRejection(question, request.format(), conceptByNo, normalizedContentBySeq);
            if (rejection.isPresent()) {
                rejected.merge(rejection.get(), 1, Integer::sum);
                countTypes(rejectedTypes, Collections.singletonList(question));
            } else {
                validQuestions.add(question);
            }
        }

        List<GeneratedQuestion> kept = trimToQuestionCount(validQuestions, request, trimmedTypes);
        logResult(received.size(), kept.size(), rejected, rejectedTypes, trimmedTypes);
        return new GeneratedQuiz(List.copyOf(kept));
    }

    // AI 는 요청보다 문제를 더 만들기도 한다. 앞에서부터 자르면 자료 뒤쪽에 몰리는 고차원 유형이 잘려서,
    // 목표 개수보다 많이 나온 유형의 문제를 뒤쪽부터 뺀다. 목표가 없는 유형은 목표가 0 이라 먼저 빠진다.
    // 넘친 유형이 없는데도 많으면(목표 합이 문제 수보다 작을 때) 뒤쪽부터 뺀다
    private List<GeneratedQuestion> trimToQuestionCount(List<GeneratedQuestion> questions, QuizGenerationRequest request,
                                                       Map<CognitiveType, Integer> trimmedTypes) {
        int excess = questions.size() - request.questionCount();
        if (excess <= 0) {
            return questions;
        }
        Map<CognitiveType, Integer> typeCounts = new EnumMap<>(CognitiveType.class);
        countTypes(typeCounts, questions);
        Map<CognitiveType, Integer> targetCounts = request.cognitiveTypeCounts();

        // 내용이 같은 문제가 둘 있어도 하나만 빠지게 같은 객체인지로 본다
        Set<GeneratedQuestion> trimmed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = questions.size() - 1; i >= 0 && trimmed.size() < excess; i--) {
            CognitiveType type = questions.get(i).cognitiveType();
            if (typeCounts.get(type) > targetCounts.getOrDefault(type, 0)) {
                trimmed.add(questions.get(i));
                typeCounts.merge(type, -1, Integer::sum);
            }
        }
        for (int i = questions.size() - 1; i >= 0 && trimmed.size() < excess; i--) {
            trimmed.add(questions.get(i));
        }
        countTypes(trimmedTypes, List.copyOf(trimmed));

        return questions.stream()
                .filter(question -> !trimmed.contains(question))
                .toList();
    }

    // 유형을 모르는 문제(null)는 세지 않는다
    private void countTypes(Map<CognitiveType, Integer> typeCounts, List<GeneratedQuestion> questions) {
        for (GeneratedQuestion question : questions) {
            if (question != null && question.cognitiveType() != null) {
                typeCounts.merge(question.cognitiveType(), 1, Integer::sum);
            }
        }
    }

    // 문제가 요청보다 적게 남았을 때, AI 가 적게 만든 것인지 검증에서 떨어진 것인지 가르려고 받은 수와 남긴 수를 같이 남긴다.
    // 어려운 유형일수록 검증에서 잘 떨어져, 어느 유형이 떨어졌는지도 같이 남긴다.
    // 강의 내용은 남기지 않고 개수만 남긴다. 요청보다 많이 와서 뺀 문제는 버린 것과 따로 센다
    private void logResult(int receivedCount, int keptCount, Map<Rejection, Integer> rejected,
                           Map<CognitiveType, Integer> rejectedTypes, Map<CognitiveType, Integer> trimmedTypes) {
        int rejectedCount = rejected.values().stream().mapToInt(Integer::intValue).sum();
        int trimmedCount = trimmedTypes.values().stream().mapToInt(Integer::intValue).sum();
        log.info("생성 결과 검증: 받은 문제 {}개, 남긴 문제 {}개, 버린 문제 {}개 {} 버린 유형 {}, 넘쳐서 뺀 문제 {}개 {}",
                receivedCount, keptCount, rejectedCount, rejected, rejectedTypes, trimmedCount, trimmedTypes);
    }

    // 처음 걸린 규칙 하나를 이유로 돌려주고, 모두 통과하면 비어 있다.
    // 앞의 규칙이 null 을 막아 주는 순서다. 바꾸면 뒤의 규칙에서 NPE 가 날 수 있다
    private Optional<Rejection> findRejection(GeneratedQuestion question, QuizFormat format,
                                              Map<Integer, AssignedConcept> conceptByNo,
                                              Map<Integer, String> normalizedContentBySeq) {
        if (question == null) {
            return Optional.of(Rejection.NULL_QUESTION);
        }
        // 보내지 않은 번호는 저장할 개념 id 가 없다
        AssignedConcept concept = conceptByNo.get(question.conceptNo());
        if (concept == null) {
            return Optional.of(Rejection.UNKNOWN_CONCEPT);
        }
        if (!hasExpectedOptionCount(question.options(), format)) {
            return Optional.of(Rejection.OPTION_COUNT);
        }
        if (!hasRequiredFields(question)) {
            return Optional.of(Rejection.MISSING_FIELD);
        }
        if (!hasExactlyOneAnswer(question.options())) {
            return Optional.of(Rejection.ANSWER_COUNT);
        }
        if (!hasDistinctOptions(question.options())) {
            return Optional.of(Rejection.DUPLICATE_OPTION);
        }
        if (!isSourceInConcept(question.sourceChunkSeq(), concept.chunkSeqs(), normalizedContentBySeq)) {
            return Optional.of(Rejection.SOURCE_NOT_IN_CONCEPT);
        }
        if (!isQuoteInSource(question.sourceQuote(), normalizedContentBySeq.get(question.sourceChunkSeq()))) {
            return Optional.of(Rejection.QUOTE_NOT_IN_SOURCE);
        }
        return Optional.empty();
    }

    private <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private boolean hasExpectedOptionCount(List<GeneratedOption> options, QuizFormat format) {
        return options != null && options.size() == format.optionCount();
    }

    // 정답이 둘이면 uq_question_option_one_correct 에 걸려 저장 전체가 실패한다
    private boolean hasExactlyOneAnswer(List<GeneratedOption> options) {
        return options.stream().filter(GeneratedOption::isCorrect).count() == 1;
    }

    private boolean hasDistinctOptions(List<GeneratedOption> options) {
        return options.stream().map(GeneratedOption::content).distinct().count() == options.size();
    }

    // 전부 NOT NULL 컬럼이다. 하나라도 비면 INSERT 가 실패한다
    private boolean hasRequiredFields(GeneratedQuestion question) {
        return !isBlank(question.content())
                && !isBlank(question.explanation())
                && !isBlank(question.sourceQuote())
                && question.cognitiveType() != null
                && question.options().stream()
                .noneMatch(option -> option == null || isBlank(option.content()));
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    // 근거 청크는 그 개념이 나오는 페이지여야 하고, 이번에 보낸 청크여야 원문과 인용을 대조할 수 있다
    private boolean isSourceInConcept(int sourceChunkSeq, List<Integer> conceptChunkSeqs,
                                      Map<Integer, String> normalizedContentBySeq) {
        return conceptChunkSeqs.contains(sourceChunkSeq) && normalizedContentBySeq.containsKey(sourceChunkSeq);
    }

    // 모델은 원문 중간을 건너뛸 때 " / " 로 잇거나, 빈 줄이나 "..." 줄을 넣거나, 표시 없이 줄만 바꾼다.
    // 그래서 인용을 " / " 와 줄로 나눠, 줄마다 원문에 그대로 있고 앞 줄보다 뒤에 나오는지 본다.
    // " / " 로 이은 조각 수는 제한하지 않는다. 줄 사이를 건너뛰는 것을 받는 이상, 같은 건너뛰기를 " / " 로 표시했다고 버릴 까닭이 없다.
    // 줄은 정리하기 전에 나눈다. 먼저 정리하면 건너뛴 자리의 앞뒤가 붙어 원문에서 찾을 수 없다.
    // 빈 줄, "...", "}" 처럼 정리하면 비는 줄은 건너뛴 자리로 보고 뺀다. " / " 조각에 남는 줄이 없으면 기호만 있는 조각이라 버린다
    private boolean isQuoteInSource(String sourceQuote, String sourceContent) {
        List<String> lines = new ArrayList<>();
        for (String part : QUOTE_PART_SEPARATOR.split(sourceQuote)) {
            List<String> partLines = part.lines()
                    .map(this::normalize)
                    .filter(line -> !line.isEmpty())
                    .toList();
            if (partLines.isEmpty()) {
                return false;
            }
            lines.addAll(partLines);
        }
        int from = 0;
        for (String line : lines) {
            int found = sourceContent.indexOf(line, from);
            if (found < 0) {
                return false;
            }
            from = found + line.length();
        }
        return true;
    }

    // 원문과 인용을 글자와 숫자만 남겨 비교한다. 지어낸 문장은 글자가 원문에 이어져 있지 않아 그대로 걸러진다.
    // PDF 추출은 단어 중간에 줄바꿈이나 공백을 넣고(W orkbook), AI 는 목록의 두 줄을 이으며 글머리 기호를 '-' 로 바꾸곤 한다.
    // 공백과 기호까지 맞추면 원문에 있는 문장도 떨어져서, 모양이 같은 문자를 NFKC 로 맞춘 뒤 글자와 숫자가 아닌 것은 지운다
    private String normalize(String text) {
        String unified = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return NOT_LETTER_OR_DIGIT.matcher(unified).replaceAll("");
    }
}
