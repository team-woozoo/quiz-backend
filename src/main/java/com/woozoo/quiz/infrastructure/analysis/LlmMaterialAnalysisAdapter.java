package com.woozoo.quiz.infrastructure.analysis;

import com.woozoo.quiz.concept.domain.ConceptTag;
import com.woozoo.quiz.material.domain.MaterialChunk;
import com.woozoo.quiz.material.port.MaterialAnalysis;
import com.woozoo.quiz.material.port.MaterialAnalysisException;
import com.woozoo.quiz.material.port.MaterialAnalysisPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 업로드한 자료를 학교 LLM 게이트웨이로 분석한다. 업로드 요청이 이 호출을 기다리므로 생성과 다른 모델, 출력 상한,
// 타임아웃을 쓴다. 응답이 끝까지 왔고 읽을 수 있는지만 보고, 개념이 맞는지(없는 seq, 같은 이름 등)는 서비스가 고른다
@Component
@ConditionalOnProperty(name = "quiz.material.analyzer", havingValue = "llm")
@Slf4j
public class LlmMaterialAnalysisAdapter implements MaterialAnalysisPort {

    private final ChatClient chatClient;
    private final int maxTokens;


    public LlmMaterialAnalysisAdapter(ChatClient.Builder clientBuilder,
                                      @Value("classpath:prompts/material-analysis-system.md")
                                      Resource systemPrompt,
                                      @Value("${quiz.material.analysis-model}")
                                      String model,
                                      @Value("${quiz.material.analysis-timeout}")
                                      Duration timeout,
                                      @Value("${quiz.material.analysis-max-tokens}")
                                      int maxTokens) {
        // 공통 설정은 생성용이라 모델, 출력 상한, 온도를 모두 덮어쓴다. 개념 이름은 이후 퀴즈와 학습 기록의 기준이 되어
        // 같은 자료에 흔들리지 않게 온도 0 으로 둔다. 호출 옵션에는 Spring AI 기본값 60초의 타임아웃이 따로 붙어
        // 클라이언트 설정보다 앞서므로 여기서 직접 정한다
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .model(model)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .temperature(0.0);
        this.chatClient = clientBuilder
                .defaultSystem(systemPrompt)
                .defaultOptions(options)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
        this.maxTokens = maxTokens;
    }

    @Override
    public MaterialAnalysis analyze(List<MaterialChunk> materialChunks) {
        ResponseEntity<ChatResponse, MaterialAnalysis> result;
        long startTime = System.nanoTime();
        String userMessage = MaterialAnalysisUserMessage.from(materialChunks);

        try {
            result = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .responseEntity(MaterialAnalysis.class);
        } catch (RuntimeException e) {
            // 게이트웨이 오류, 타임아웃, 깨진 JSON 처럼 응답을 받지 못한 경우다. 타임아웃인지는 걸린 시간으로 가늠한다
            long failedAfterMs = Duration.ofNanos(System.nanoTime() - startTime).toMillis();
            throw new MaterialAnalysisException("LLM 호출이나 응답 해석에 실패했다 ( %dms )".formatted(failedAfterMs), e);
        }

        long elapsedMs = Duration.ofNanos(System.nanoTime() - startTime).toMillis();
        Usage usage = result.response().getMetadata().getUsage();
        int outputTokens = usage.getCompletionTokens();
        MaterialAnalysis analyzed = result.entity();

        // Claude 는 출력 상한에 걸려도 게이트웨이가 {} 와 stop 을 돌려줘, 끝난 이유가 아니라 내용과 출력 토큰으로 본다.
        // {} 가 오면 suitable 은 false 로 채워지므로 concepts 가 null 인지 함께 봐야 잘린 응답이 "학습 자료 아님"으로 읽히지 않는다.
        // 개념 목록이 비어 있는 것은 부적합 자료의 정상 응답일 수 있어 여기서 막지 않는다
        boolean hitMaxTokens = outputTokens >= maxTokens;
        boolean noContent = analyzed == null || analyzed.concepts() == null;

        if (hitMaxTokens || noContent) {
            log.warn("LLM 응답을 쓸 수 없다({}): {}ms, 끝난 이유 {}, 입력 토큰 {}, 출력 토큰 {}/{}",
                    noContent ? "내용 없음" : "출력 상한 도달", elapsedMs, finishReasonOf(result.response()),
                    usage.getPromptTokens(), outputTokens, maxTokens);
            throw new MaterialAnalysisException("LLM 응답을 쓸 수 없다");
        }

        // 태그가 부풀려지는지(코드 자료에 FACTORS 가 붙는지 등) 운영에서 보려고 남긴다. 로그 때문에 정상 응답이
        // 실패하지 않게 태그가 없는 개념은 건너뛴다. 판단 이유는 자료 내용을 요약하므로 남기지 않는다
        Map<ConceptTag, Long> tagCounts = analyzed.concepts().stream()
                .flatMap(concept -> concept.tags() == null ? Stream.empty() : concept.tags().stream())
                .collect(Collectors.groupingBy(
                        tag -> tag,
                        () -> new EnumMap<>(ConceptTag.class),
                        Collectors.counting()
                ));

        log.info("자료 분석 완료: {}ms, 입력 토큰 {}, 출력 토큰 {}, 청크 {}개, 학습 자료 {}, 개념 {}개, 태그 {}",
                elapsedMs, usage.getPromptTokens(), outputTokens, materialChunks.size(),
                analyzed.suitable(), analyzed.concepts().size(), tagCounts);


        return analyzed;
    }

    private static String finishReasonOf(ChatResponse response) {
        return response.getResult() == null ? null : response.getResult().getMetadata().getFinishReason();
    }
}
