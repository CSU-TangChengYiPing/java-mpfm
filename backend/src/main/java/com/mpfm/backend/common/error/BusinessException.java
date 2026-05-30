package com.mpfm.backend.common.error;

/**
 * BusinessException 异常类型，负责业务错误上下文承载。
 */
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}





