package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.port.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Component
public class GeneratedQuizValidator {

    private static final int MAX_CONCEPT_NAME_LENGTH = 255;
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]");

    // 문제를 버린 이유. 프롬프트를 고칠 때 어느 규칙에서 많이 떨어지는지 보려고 센다
    enum Rejection {
        INVALID_CONCEPT,
        NO_KNOWN_CHUNK,
        NULL_QUESTION,
        OPTION_COUNT,
        MISSING_FIELD,
        ANSWER_COUNT,
        DUPLICATE_OPTION,
        SOURCE_NOT_IN_CONCEPT,
        QUOTE_NOT_IN_SOURCE
    }

    public GeneratedQuiz validate(QuizGenerationRequest request, GeneratedQuiz quiz) {
        // 원문은 청크마다 한 번만 정리해 둔다. 그래서 isQuoteInSource 에서는 인용문만 정리한다
        Map<Integer,String> normalizedContentBySeq = new HashMap<>();
        List<SourceChunk> chunks = request.chunks();
        for (SourceChunk chunk : chunks) {
            String normalizedContent = normalize(chunk.content());
            normalizedContentBySeq.put(chunk.seq(), normalizedContent);
        }
        List<GeneratedConcept> concepts = mergeSameName(quiz.concepts());

        List<GeneratedConcept> validConcepts = new ArrayList<>();
        // 이 컴포넌트는 여러 작업이 같이 쓰므로 개수는 필드가 아니라 호출마다 센다
        Map<Rejection, Integer> rejected = new EnumMap<>(Rejection.class);
        int remaining = request.questionCount();

        for (GeneratedConcept concept : concepts) {
            if (remaining == 0) {
                break;
            }
            if (!isValidConcept(concept)) {
                count(rejected, Rejection.INVALID_CONCEPT, concept.questions().size());
                continue;
            }
            // 보내지 않은 청크 번호는 빼 둔다. 그러면 문제의 근거가 이 목록에 있다는 것만으로 원문이 있다는 것까지 보장된다
            List<Integer> chunkSeqs = concept.chunkSeqs().stream()
                    .filter(normalizedContentBySeq::containsKey)
                    .toList();
            if (chunkSeqs.isEmpty()) {
                count(rejected, Rejection.NO_KNOWN_CHUNK, concept.questions().size());
                continue;
            }

            List<GeneratedQuestion> validQuestions = new ArrayList<>();
            for (GeneratedQuestion question : concept.questions()) {
                if (validQuestions.size() == remaining) {
                    break;
                }
                Optional<Rejection> rejection =
                        findRejection(question, request.format(), chunkSeqs, normalizedContentBySeq);
                if (rejection.isPresent()) {
                    count(rejected, rejection.get(), 1);
                } else {
                    validQuestions.add(question);
                }
            }
            // 문제가 없는 개념은 약점 분석에 빈 개념으로 남기만 해서 저장하지 않는다
            if (validQuestions.isEmpty()) {
                continue;
            }

            remaining -= validQuestions.size();
            validConcepts.add(new GeneratedConcept(
                    concept.name(), concept.description(), chunkSeqs, List.copyOf(validQuestions)));
        }
        logRejected(rejected);
        return new GeneratedQuiz(List.copyOf(validConcepts));
    }

    private void count(Map<Rejection, Integer> rejected, Rejection rejection, int questionCount) {
        if (questionCount > 0) {
            rejected.merge(rejection, questionCount, Integer::sum);
        }
    }

    // 강의 내용은 남기지 않고 이유별 개수만 남긴다. 요청보다 많이 와서 남긴 문제는 버린 것으로 세지 않는다
    private void logRejected(Map<Rejection, Integer> rejected) {
        if (rejected.isEmpty()) {
            return;
        }
        int total = rejected.values().stream().mapToInt(Integer::intValue).sum();
        log.info("생성 결과 검증에서 문제 {}개를 버렸다: {}", total, rejected);
    }

    // 이름 빈 값은 mergeSameName 에서 이미 걸렀다. 이름은 VARCHAR(255) 라 넘으면 저장이 실패한다
    private boolean isValidConcept(GeneratedConcept concept) {
        return concept.name().length() <= MAX_CONCEPT_NAME_LENGTH
                && !isBlank(concept.description());
    }

    // 처음 걸린 규칙 하나를 이유로 돌려주고, 모두 통과하면 비어 있다.
    // 앞의 규칙이 null 을 막아 주는 순서다. 바꾸면 뒤의 규칙에서 NPE 가 날 수 있다
    private Optional<Rejection> findRejection(GeneratedQuestion question, QuizFormat format, List<Integer> chunkSeqs,
                                              Map<Integer, String> normalizedContentBySeq) {
        if (question == null) {
            return Optional.of(Rejection.NULL_QUESTION);
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
        if (!isSourceInConcept(question.sourceChunkSeq(), chunkSeqs)) {
            return Optional.of(Rejection.SOURCE_NOT_IN_CONCEPT);
        }
        if (!isQuoteInSource(question.sourceQuote(), normalizedContentBySeq.get(question.sourceChunkSeq()))) {
            return Optional.of(Rejection.QUOTE_NOT_IN_SOURCE);
        }
        return Optional.empty();
    }

    // 강의 앞뒤에 같은 개념이 나오면 AI가 이름이 같은 개념을 둘로 나눠 줄 수 있다.
    // 하나를 버리면 그쪽 문제와 청크 연결이 같이 사라져서, 이름으로 묶어 하나로 합친다
    private List<GeneratedConcept> mergeSameName(List<GeneratedConcept> concepts) {
        Map<String, GeneratedConcept> mergedByName = new LinkedHashMap<>();

        for (GeneratedConcept concept : nullToEmpty(concepts)) {
            if (concept == null || isBlank(concept.name())) {
                continue;
            }
            String name = concept.name().strip();
            GeneratedConcept current = new GeneratedConcept(
                    name,
                    concept.description(),
                    nullToEmpty(concept.chunkSeqs()).stream().distinct().toList(),
                    nullToEmpty(concept.questions()));

            mergedByName.merge(name, current, this::merge);
        }
        return List.copyOf(mergedByName.values());
    }

    // 설명은 먼저 나온 것을 쓴다. 청크 번호는 concept_chunk 기본 키가 겹치지 않게 중복을 뺀다
    private GeneratedConcept merge(GeneratedConcept first, GeneratedConcept second) {
        List<Integer> chunkSeqs = Stream.concat(first.chunkSeqs().stream(), second.chunkSeqs().stream())
                .distinct()
                .toList();
        List<GeneratedQuestion> questions = Stream.concat(first.questions().stream(), second.questions().stream())
                .toList();

        return new GeneratedConcept(first.name(), first.description(), chunkSeqs, questions);
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

    private boolean isSourceInConcept(int sourceChunkSeq, List<Integer> conceptChunkSeqs) {
        return conceptChunkSeqs.contains(sourceChunkSeq);
    }

    // 기호만 있는 인용은 정리하면 빈 문자열이 되고, 빈 문자열은 어느 원문에나 들어 있다고 나와 막는다
    private boolean isQuoteInSource(String sourceQuote, String sourceContent) {
        String quote = normalize(sourceQuote);
        return !quote.isEmpty() && sourceContent.contains(quote);
    }

    // 원문과 인용을 글자와 숫자만 남겨 비교한다. 지어낸 문장은 글자가 원문에 이어져 있지 않아 그대로 걸러진다.
    // PDF 추출은 단어 중간에 줄바꿈이나 공백을 넣고(W orkbook), AI 는 목록의 두 줄을 이으며 글머리 기호를 '-' 로 바꾸곤 한다.
    // 공백과 기호까지 맞추면 원문에 있는 문장도 떨어져서, 모양이 같은 문자를 NFKC 로 맞춘 뒤 글자와 숫자가 아닌 것은 지운다
    private String normalize(String text) {
        String unified = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return NOT_LETTER_OR_DIGIT.matcher(unified).replaceAll("");
    }
}
