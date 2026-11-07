package com.woozoo.quiz.infrastructure.validation;

import com.woozoo.quiz.quiz.port.GeneratedOption;
import com.woozoo.quiz.quiz.port.QuestionToJudgeType;

import java.util.List;

// 판정 요청마다 LLM 에 보낼 user 메시지를 만든다. 규칙은 system 프롬프트(prompts/question-type-judge-system.md)에 있고,
// 여기에는 문제만 넣는다. 태그는 구획을 나눠 읽게 하는 표시일 뿐이라 원문을 이스케이프하지 않는다
final class QuestionTypeJudgeUserMessage {

    private QuestionTypeJudgeUserMessage() {
    }

    static String from(List<QuestionToJudgeType> questions) {
        StringBuilder message = new StringBuilder();
        for (QuestionToJudgeType question : questions) {
            message.append("<question index=\"").append(question.index()).append("\">\n")
                    .append("content: ").append(question.content()).append('\n')
                    .append("options:\n");
            for (GeneratedOption option : question.options()) {
                message.append(option.isCorrect() ? "- (정답) " : "- ").append(option.content()).append('\n');
            }
            message.append("explanation: ").append(question.explanation()).append('\n')
                    .append("source_quote: ").append(question.sourceQuote()).append('\n')
                    .append("source_chunk:\n").append(question.sourceChunk()).append('\n')
                    .append("</question>\n\n");
        }
        return message.toString();
    }
}
