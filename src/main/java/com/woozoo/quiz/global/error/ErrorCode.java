package com.woozoo.quiz.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "업로드 가능한 파일 용량을 초과했습니다"),
    EXTRACTION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "텍스트 추출에 실패했습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
