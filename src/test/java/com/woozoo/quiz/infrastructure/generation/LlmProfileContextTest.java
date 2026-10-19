package com.woozoo.quiz.infrastructure.generation;

import com.woozoo.quiz.TestcontainersConfiguration;
import com.woozoo.quiz.quiz.port.QuizGenerationPort;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// 서버는 llm 프로필로 뜬다. 설정이나 프롬프트 파일 경로가 틀리면 배포한 서버가 뜨지 않아, 여기서 먼저 띄워 본다.
// 주소와 키는 가짜라 게이트웨이는 부르지 않는다. 클라이언트는 만들 때 접속하지 않는다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "LLM_BASE_URL=http://localhost:1",
        "LLM_API_KEY=test-key"
})
@ActiveProfiles("llm")
class LlmProfileContextTest {

    @Autowired
    ApplicationContext context;

    @Test
    void llm_프로필에서는_LLM_어댑터만_생성기로_뜬다() {
        assertThat(context.getBeansOfType(QuizGenerationPort.class).values())
                .singleElement()
                .isInstanceOf(LlmQuizGenerationAdapter.class);
    }

    @Test
    void 채팅_모델은_OpenAI_호환_클라이언트다() {
        assertThat(context.getBean(ChatModel.class)).isInstanceOf(OpenAiChatModel.class);
    }
}
