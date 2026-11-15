package com.woozoo.quiz.infrastructure.generation;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;

// 응답이 비었거나 JSON 이 깨졌을 때 예외 대신 null 을 돌려준다.
// 예외가 나면 ChatClient 가 응답을 버려서, 사용량과 끝난 이유를 남길 수 없다. 실패 처리는 어댑터가 한다.
// 스키마와 형식 안내는 그대로 넘겨, response_format 에 같은 JSON 스키마가 실린다
final class LenientOutputConverter<T> implements StructuredOutputConverter<T> {

    private final BeanOutputConverter<T> delegate;

    LenientOutputConverter(Class<T> type) {
        this.delegate = new BeanOutputConverter<>(type);
    }

    @Override
    public T convert(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return delegate.convert(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public String getFormat() {
        return delegate.getFormat();
    }

    @Override
    public String getJsonSchema() {
        return delegate.getJsonSchema();
    }
}
