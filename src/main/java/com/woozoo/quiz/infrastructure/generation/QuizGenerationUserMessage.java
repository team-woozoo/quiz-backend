package com.woozoo.quiz.infrastructure.generation;

import com.woozoo.quiz.quiz.domain.CognitiveType;
import com.woozoo.quiz.quiz.port.ConceptFocus;
import com.woozoo.quiz.quiz.port.ExistingConcept;
import com.woozoo.quiz.quiz.port.QuizGenerationRequest;
import com.woozoo.quiz.quiz.port.SourceChunk;

import java.util.Map;
import java.util.stream.Collectors;

// 요청마다 LLM 에 보낼 user 메시지를 만든다. 규칙은 system 프롬프트(prompts/quiz-generation-system.md)에 있고,
// 여기에는 이번 요청의 조건, 기존 개념(약점 퀴즈면 겨냥한 개념과 이미 낸 문제), 강의 자료만 넣는다.
// 태그는 모델이 구획을 나눠 읽게 하는 표시일 뿐 XML 로 파싱하지 않는다. 그래서 원문을 이스케이프하지 않는다.
// 이스케이프하면 모델이 바뀐 글자를 인용해 원문 대조에서 떨어진다
final class QuizGenerationUserMessage {

    private QuizGenerationUserMessage() {
    }

    // 문자열을 직접 이어 붙여 만든다. 템플릿 변수로 넘기면 강의 자료 속 { } 를 변수 자리로 읽어 실패한다
    static String from(QuizGenerationRequest request) {
        StringBuilder message = new StringBuilder();
        appendConditions(message, request);
        if (request.focus() == null) {
            appendExistingConcepts(message, request);
        } else {
            appendFocus(message, request.focus());
        }
        appendMaterial(message, request);
        return message.toString();
    }

    // 값은 enum 이름 그대로 쓴다. system 프롬프트가 같은 이름으로 규칙을 설명한다
    private static void appendConditions(StringBuilder message, QuizGenerationRequest request) {
        message.append("<conditions>\n")
                .append("format: ").append(request.format().name()).append('\n')
                .append("difficulty: ").append(request.difficulty().name()).append('\n')
                .append("questionCount: ").append(request.questionCount()).append('\n');
        appendCognitiveTypes(message, request.cognitiveTypeCounts());
        message.append("</conditions>\n\n");
    }

    // system 프롬프트 뒤쪽에 개수를 적었을 때는 모델이 지키지 않았다. 답을 쓰기 직전에 보는 이 자리에 둔다
    private static void appendCognitiveTypes(StringBuilder message, Map<CognitiveType, Integer> counts) {
        if (counts.isEmpty()) {
            return;
        }
        message.append("cognitiveTypes: ")
                .append(counts.entrySet().stream()
                        .map(entry -> entry.getKey().name() + " " + entry.getValue())
                        .collect(Collectors.joining(", ")))
                .append('\n');
    }

    // 빈 태그만 두면 모델이 무엇을 기대하는지 헷갈릴 수 있어, 없으면 없다고 적는다
    private static void appendExistingConcepts(StringBuilder message, QuizGenerationRequest request) {
        message.append("<existing_concepts>\n");
        if (request.existingConcepts().isEmpty()) {
            message.append("없음\n");
        }
        for (ExistingConcept concept : request.existingConcepts()) {
            message.append("- ").append(concept.name()).append(": ").append(concept.description()).append('\n');
        }
        message.append("</existing_concepts>\n\n");
    }

    // 약점 퀴즈는 개념 하나로만 내고, 이미 낸 문제는 피할 목록으로 준다. 근거로 쓰지 않게 원문과 다른 태그에 둔다
    private static void appendFocus(StringBuilder message, ConceptFocus focus) {
        message.append("<focus_concept>\n")
                .append("name: ").append(focus.name()).append('\n')
                .append("description: ").append(focus.description()).append('\n')
                .append("</focus_concept>\n\n");
        message.append("<asked_questions>\n");
        if (focus.askedQuestions().isEmpty()) {
            message.append("없음\n");
        }
        for (String question : focus.askedQuestions()) {
            message.append("- ").append(question).append('\n');
        }
        message.append("</asked_questions>\n\n");
    }

    // 원문은 공백도 그대로 둔다. 검증기가 이 원문과 인용을 대조한다
    private static void appendMaterial(StringBuilder message, QuizGenerationRequest request) {
        message.append("<material>\n");
        for (SourceChunk chunk : request.chunks()) {
            message.append("<chunk seq=\"").append(chunk.seq()).append("\">\n")
                    .append(chunk.content()).append('\n')
                    .append("</chunk>\n");
        }
        message.append("</material>\n");
    }
}
