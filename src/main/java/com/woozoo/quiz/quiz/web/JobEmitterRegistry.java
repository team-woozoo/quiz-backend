package com.woozoo.quiz.quiz.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class JobEmitterRegistry {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Duration timeout;

    public JobEmitterRegistry(@Value("${quiz.generation.sse-timeout}") Duration timeout) {
        this.timeout = timeout;
    }

    public SseEmitter connect(long jobId){
        SseEmitter emitter = new SseEmitter(timeout.toMillis());

        emitter.onCompletion(() -> remove(jobId, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError((e) -> remove(jobId, emitter));

        register(jobId, emitter);
        return emitter;
    }

    public Optional<SseEmitter> find(long jobId) {
        return Optional.ofNullable(emitters.get(jobId));
    }

    public void send(SseEmitter emitter, GenerationJobResponse jobResponse) {
        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name("status")
                .data(jobResponse, MediaType.APPLICATION_JSON);
        try {
            emitter.send(event);

            if(jobResponse.status().isFinished()) {
                emitter.complete();
                // 반환 전에 닫힌 emitter 는 콜백이 등록되지 않아, 닫는 곳에서 직접 뺀다
                emitters.remove(jobResponse.jobId(), emitter);
            }
        } catch(IOException | IllegalStateException e) {
            log.debug("앱 연결이 끊겨 보내지 못했다", e);
        }
    }

    private void register(long jobId, SseEmitter emitter) {
        SseEmitter old = emitters.put(jobId, emitter);
        if(old != null){
            old.complete();
        }
    }

    private void remove(long jobId, SseEmitter emitter) {
        emitters.remove(jobId, emitter);
    }
}
