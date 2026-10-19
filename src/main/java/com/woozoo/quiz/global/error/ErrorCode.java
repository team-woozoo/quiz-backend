package com.woozoo.quiz.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "업로드 가능한 파일 용량을 초과했습니다"),
    EXTRACTION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "텍스트 추출에 실패했습니다"),
    INVALID_FILE(HttpStatus.BAD_REQUEST, "PDF 파일만 올릴 수 있습니다"),
    TOO_MANY_PAGES(HttpStatus.UNPROCESSABLE_CONTENT, "페이지 수가 너무 많습니다"),
    INVALID_SUBMISSION(HttpStatus.BAD_REQUEST, "제출한 답안이 올바르지 않습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요"),
    MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "자료를 찾을 수 없습니다"),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "생성 작업을 찾을 수 없습니다"),
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "퀴즈를 찾을 수 없습니다"),
    NO_VALID_QUESTIONS(HttpStatus.UNPROCESSABLE_CONTENT, "자료에서 문제를 만들지 못했습니다"),
    GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "퀴즈 생성에 실패했습니다. 다시 시도해 주세요"),
    DAILY_GENERATION_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "일일 퀴즈 생성 한도를 초과했습니다. 내일 다시 시도해 주세요"),
    GENERATION_IN_PROGRESS(HttpStatus.CONFLICT, "이미 진행 중인 퀴즈 생성 작업이 있습니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
