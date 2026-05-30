package com.mpfm.backend.common.error;

/**
 * ErrorResponse 错误响应模型，统一异常返回结构。
 */
public record ErrorResponse(ErrorBody error) {

    public static ErrorResponse of(ErrorCode code, String message, String requestId) {
        return new ErrorResponse(new ErrorBody(code.name(), message, requestId));
    }

    /** 错误体模型，承载错误码、错误消息与请求链路标识。 */
    public record ErrorBody(String code, String message, String requestId) {
    }
}





