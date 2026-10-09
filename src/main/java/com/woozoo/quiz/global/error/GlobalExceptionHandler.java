package com.woozoo.quiz.global.error;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Spring MVC 예외(404, 405, 413 등)는 부모가 원래 상태 그대로 처리하게 둔다. Exception 핸들러만 두면 이것들까지 500 이 된다
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        if (e.getCause() != null) {
            log.warn("업무 예외: {}", errorCode.name(), e);
        }

        return toProblemDetail(errorCode);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception e) {
        // 예외 메시지에 SQL 등 내부 정보가 담길 수 있어 로그에만 남기고, 응답은 고정 문구로 준다
        log.error("처리하지 못한 예외", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;

        return toProblemDetail(errorCode);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
                                                                       HttpHeaders headers, HttpStatusCode statusCode,
                                                                       WebRequest request) {
        // 부모는 body 를 null 로 넘기고 super 안에서 ProblemDetail 을 만든다. 그래서 super 를 먼저 부르고 결과에 code 를 붙인다
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);

        if (response != null && response.getBody() instanceof ProblemDetail problemDetail) {
            problemDetail.setProperty("code", fromStatus(statusCode).name());
        }
        return response;
    }

    private ProblemDetail toProblemDetail(ErrorCode errorCode) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                errorCode.getStatus(),
                errorCode.getMessage()
        );
        problemDetail.setProperty("code", errorCode.name());
        return problemDetail;
    }

    private ErrorCode fromStatus(HttpStatusCode statusCode) {
        // 413 도 4xx 라서 먼저 확인한다
        if (statusCode.value() == HttpStatus.CONTENT_TOO_LARGE.value()) {
            return ErrorCode.FILE_TOO_LARGE;
        }
        if (statusCode.is4xxClientError()) {
            return ErrorCode.INVALID_INPUT;
        }
        return ErrorCode.INTERNAL_ERROR;
    }
}
