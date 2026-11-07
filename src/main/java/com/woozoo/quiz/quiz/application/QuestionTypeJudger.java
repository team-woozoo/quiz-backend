package com.woozoo.quiz.quiz.application;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.port.GeneratedConcept;
import com.woozoo.quiz.quiz.port.GeneratedQuestion;
import com.woozoo.quiz.quiz.port.GeneratedQuiz;
import com.woozoo.quiz.quiz.port.JudgedQuestionType;
import com.woozoo.quiz.quiz.port.QuestionToJudgeType;
import com.woozoo.quiz.quiz.port.QuestionTypeJudgeException;
import com.woozoo.quiz.quiz.port.QuestionTypeJudgePort;
import com.woozoo.quiz.quiz.port.SourceChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

// 생성기가 붙인 유형을 다른 모델의 판정과 견준다. 유형이 틀리면 약점 분석이 틀린 진단을 내려서,
// 두 모델이 같은 유형을 붙인 문제만 남긴다
@Component
@Slf4j
public class QuestionTypeJudger {

    private final QuestionTypeJudgePort questionTypeJudgePort;

    public QuestionTypeJudger(QuestionTypeJudgePort questionTypeJudgePort) {
        this.questionTypeJudgePort = questionTypeJudgePort;
    }

    // 판정 호출이 실패하면 받은 퀴즈를 그대로 돌려준다. 문제는 규칙 검증을 통과했고 생성 비용도 이미 썼으니,
    // 유형을 확인하지 못했다고 작업까지 실패시키지 않는다
    public GeneratedQuiz keepMatched(List<SourceChunk> chunks, GeneratedQuiz quiz) {
        List<QuestionToJudgeType> questions = toQuestions(chunks, quiz);
        if (questions.isEmpty()) {
            return quiz;
        }
        try {
            return filterMatched(quiz, questionTypeJudgePort.judge(questions));
        } catch (QuestionTypeJudgeException e) {
            log.warn("유형 판정에 실패해 생성된 유형으로 저장한다", e);
            return quiz;
        }
    }

    // 저장 전이라 문제에 id 가 없고, 개념과 청크는 문제 여럿이 같이 쓴다. 그래서 임시 번호를 붙여 보낸다.
    // 번호는 개념, 문제 순서로 세고 filterMatched 도 같은 순서로 세어 같은 문제를 가리키게 한다
    private List<QuestionToJudgeType> toQuestions(List<SourceChunk> chunks, GeneratedQuiz quiz) {
        Map<Integer, String> chunksBySeq = chunks.stream()
                .collect(Collectors.toMap(SourceChunk::seq, SourceChunk::content));

        List<QuestionToJudgeType> questions = new ArrayList<>();

        int index = 0;
        for (GeneratedConcept concept : quiz.concepts()) {
            for (GeneratedQuestion question : concept.questions()) {
                String chunk = chunksBySeq.get(question.sourceChunkSeq());
                questions.add(new QuestionToJudgeType(index++, question.content(), question.sourceQuote(),
                        question.explanation(), question.options(), chunk));
            }
        }
        return questions;
    }

    // 모델은 판정을 빠뜨리거나 순서를 바꿀 수 있어, 응답의 위치가 아니라 번호로 문제를 찾는다.
    // 판정이 없는 문제는 일치를 확인할 수 없어 뺀다. 문제가 다 빠진 개념은 저장하지 않는다.
    // 일치만 남기는 정책이 맞는지 판단하려고 생성 유형과 판정 유형의 조합별 개수를 남긴다. 강의 내용은 남기지 않는다.
    // 어느 쪽 유형이 맞는지 사람이 보려면 문제와 이유가 필요해, 강의 내용이 든 불일치 내용은 debug 로만 남긴다
    private GeneratedQuiz filterMatched(GeneratedQuiz quiz, List<JudgedQuestionType> judgedQuestions) {
        Map<Integer, JudgedQuestionType> judgedByIndex = judgedQuestions.stream()
                .collect(Collectors.toMap(JudgedQuestionType::index, Function.identity(),
                        (first, second) -> first));

        List<GeneratedConcept> matchedConcepts = new ArrayList<>();
        Map<String, Integer> typePairs = new TreeMap<>();
        int matchedCount = 0;

        int index = 0;
        for (GeneratedConcept concept : quiz.concepts()) {
            List<GeneratedQuestion> matchedQuestions = new ArrayList<>();
            for (GeneratedQuestion question : concept.questions()) {
                JudgedQuestionType judged = judgedByIndex.get(index++);
                CognitiveType type = judged == null ? null : judged.cognitiveType();
                typePairs.merge(question.cognitiveType() + "->" + type, 1, Integer::sum);
                if (type == question.cognitiveType()) {
                    matchedQuestions.add(question);
                    matchedCount++;
                } else {
                    log.debug("유형 불일치: {} -> {}\n문제: {}\n보기: {}\n인용: {}\n이유: {}",
                            question.cognitiveType(), type, question.content(), question.options(),
                            question.sourceQuote(), judged == null ? null : judged.reason());
                }
            }
            if (!matchedQuestions.isEmpty()) {
                matchedConcepts.add(new GeneratedConcept(concept.name(), concept.description(),
                        concept.chunkSeqs(), matchedQuestions));
            }
        }
        log.info("유형 판정 결과: 보낸 문제 {}개, 남긴 문제 {}개, 생성 유형->판정 유형 {}", index, matchedCount, typePairs);
        return new GeneratedQuiz(matchedConcepts);
    }
}
