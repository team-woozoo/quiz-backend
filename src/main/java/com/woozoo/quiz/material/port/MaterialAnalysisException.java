package com.woozoo.quiz.material.port;

public class MaterialAnalysisException extends RuntimeException {
    public MaterialAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }

    public MaterialAnalysisException(String message) { super(message); }
}
