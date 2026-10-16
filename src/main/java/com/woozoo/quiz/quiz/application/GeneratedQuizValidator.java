package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.quiz.domain.QuizFormat;
import com.woozoo.quiz.quiz.port.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class GeneratedQuizValidator {

    private static final int MAX_CONCEPT_NAME_LENGTH = 255;

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
        int remaining = request.questionCount();

        for (GeneratedConcept concept : concepts) {
            if (remaining == 0) {
                break;
            }
            if (!isValidConcept(concept)) {
                continue;
            }
            // 보내지 않은 청크 번호는 빼 둔다. 그러면 문제의 근거가 이 목록에 있다는 것만으로 원문이 있다는 것까지 보장된다
            List<Integer> chunkSeqs = concept.chunkSeqs().stream()
                    .filter(normalizedContentBySeq::containsKey)
                    .toList();
            if (chunkSeqs.isEmpty()) {
                continue;
            }

            List<GeneratedQuestion> validQuestions = new ArrayList<>();
            for (GeneratedQuestion question : concept.questions()) {
                if (validQuestions.size() == remaining) {
                    break;
                }
                if (isValid(question, request.format(), chunkSeqs, normalizedContentBySeq)) {
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
        return new GeneratedQuiz(List.copyOf(validConcepts));
    }

    // 이름 빈 값은 mergeSameName 에서 이미 걸렀다. 이름은 VARCHAR(255) 라 넘으면 저장이 실패한다
    private boolean isValidConcept(GeneratedConcept concept) {
        return concept.name().length() <= MAX_CONCEPT_NAME_LENGTH
                && !isBlank(concept.description());
    }

    // 앞의 규칙이 null 을 막아 주는 순서다. 바꾸면 뒤의 규칙에서 NPE 가 날 수 있다
    private boolean isValid(GeneratedQuestion question, QuizFormat format, List<Integer> chunkSeqs,
                            Map<Integer, String> normalizedContentBySeq) {
        return question != null
                && hasExpectedOptionCount(question.options(), format)
                && hasRequiredFields(question)
                && hasExactlyOneAnswer(question.options())
                && hasDistinctOptions(question.options())
                && isSourceInConcept(question.sourceChunkSeq(), chunkSeqs)
                && isQuoteInSource(question.sourceQuote(), normalizedContentBySeq.get(question.sourceChunkSeq()));
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

    private boolean isQuoteInSource(String sourceQuote, String sourceContent) {
        return sourceContent.contains(normalize(sourceQuote));
    }

    // PDF 원문은 문장 중간에 줄바꿈이 있고 AI 는 그걸 공백으로 바꿔 인용하곤 해서, 양쪽 공백을 한 칸으로 맞춘 뒤 비교한다
    private String normalize(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }
}
