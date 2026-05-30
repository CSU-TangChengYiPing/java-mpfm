package com.mpfm.backend.adapter.api.exception;

import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.common.error.ErrorResponse;
import com.mpfm.backend.common.logging.RequestCorrelationFilter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器，负责统一错误码映射、HTTP 状态转换与安全审计埋点。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Map<ErrorCode, HttpStatus> BUSINESS_STATUS_MAPPING = buildBusinessStatusMapping();
    private final SecurityEventLogger securityEventLogger;
    private final MeterRegistry meterRegistry;

    public GlobalExceptionHandler(SecurityEventLogger securityEventLogger, @Nullable MeterRegistry meterRegistry) {
        this.securityEventLogger = securityEventLogger;
        this.meterRegistry = meterRegistry;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = mapBusinessStatus(ex.getCode());
        recordErrorMetric(ex.getCode().name(), status);
        if (ex.getCode() == ErrorCode.VERSION_CONFLICT
                || ex.getCode() == ErrorCode.CONFLICT
                || ex.getCode() == ErrorCode.INVALID_STATE_TRANSITION) {
            if (meterRegistry != null) {
                meterRegistry.counter("mpfm.business.conflicts", "code", ex.getCode().name()).increment();
            }
        }
        return ResponseEntity.status(status).body(ErrorResponse.of(ex.getCode(), ex.getMessage(), requestId(request)));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleValidation(Exception ex, HttpServletRequest request) {
        securityEventLogger.validationFailure(request.getRequestURI());
        recordErrorMetric(ErrorCode.VALIDATION_ERROR.name(), HttpStatus.BAD_REQUEST);
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, "invalid request", requestId(request)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        securityEventLogger.validationFailure(request.getRequestURI());
        recordErrorMetric(ErrorCode.VALIDATION_ERROR.name(), HttpStatus.BAD_REQUEST);
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, "upload file too large", requestId(request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        securityEventLogger.permissionDenied(request.getRequestURI());
        recordErrorMetric(ErrorCode.PERMISSION_DENIED.name(), HttpStatus.FORBIDDEN);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ErrorCode.PERMISSION_DENIED, "permission denied", requestId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleFallback(Exception ex, HttpServletRequest request) {
        Throwable root = unwrap(ex);
        if (isClientDisconnected(root)) {
            if (log.isWarnEnabled()) {
                log.warn("Client disconnected path={} method={} requestId={} message={}",
                        request.getRequestURI(),
                        request.getMethod(),
                        requestId(request),
                        root.toString());
            }
            return ResponseEntity.status(499).build();
        }
        if (root instanceof BusinessException business) {
            return handleBusiness(business, request);
        }
        if (root instanceof AccessDeniedException denied) {
            return handleForbidden(denied, request);
        }
        if (root instanceof MethodArgumentNotValidException
                || root instanceof BindException
                || root instanceof ConstraintViolationException
                || root instanceof IllegalArgumentException) {
            return handleValidation((Exception) root, request);
        }
        if (log.isErrorEnabled()) {
            log.error("Unhandled exception path={} method={} requestId={}",
                    request.getRequestURI(),
                    request.getMethod(),
                    requestId(request),
                    ex);
        }
        if (isStreamingRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "internal error", requestId(request)));
    }

    private boolean isClientDisconnected(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String name = current.getClass().getName();
            String message = String.valueOf(current.getMessage()).toLowerCase();
            if (name.contains("AsyncRequestNotUsableException")
                    || name.contains("ClientAbortException")
                    || (current instanceof IOException
                    && (message.contains("broken pipe")
                    || message.contains("connection reset")
                    || message.contains("disconnected client")))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isStreamingRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path != null && (path.startsWith("/api/v4/transfers/downloads/proxy")
                || path.startsWith("/api/v5/files/content")
                || path.startsWith("/api/v1/files/download"))) {
            return true;
        }
        return request.getHeader("Range") != null;
    }

    private void recordErrorMetric(String code, HttpStatus status) {
        if (meterRegistry != null) {
            meterRegistry.counter("mpfm.api.errors", "code", code, "status", String.valueOf(status.value())).increment();
        }
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        Set<Throwable> visited = new HashSet<>();
        while (current.getCause() != null && !visited.contains(current.getCause())) {
            visited.add(current);
            current = current.getCause();
        }
        return current;
    }

    private HttpStatus mapBusinessStatus(ErrorCode code) {
        return BUSINESS_STATUS_MAPPING.getOrDefault(code, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static Map<ErrorCode, HttpStatus> buildBusinessStatusMapping() {
        Map<ErrorCode, HttpStatus> mapping = new EnumMap<>(ErrorCode.class);
        mapping.put(ErrorCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        mapping.put(ErrorCode.AUTH_INVALID, HttpStatus.UNAUTHORIZED);
        mapping.put(ErrorCode.CAPTCHA_REQUIRED, HttpStatus.BAD_REQUEST);
        mapping.put(ErrorCode.CAPTCHA_INVALID, HttpStatus.BAD_REQUEST);
        mapping.put(ErrorCode.PERMISSION_DENIED, HttpStatus.FORBIDDEN);
        mapping.put(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST);
        mapping.put(ErrorCode.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST);
        mapping.put(ErrorCode.CONFLICT, HttpStatus.CONFLICT);
        mapping.put(ErrorCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        mapping.put(ErrorCode.INVALID_STATE_TRANSITION, HttpStatus.CONFLICT);
        mapping.put(ErrorCode.OWNER_IMMUTABLE, HttpStatus.CONFLICT);
        mapping.put(ErrorCode.CAPABILITY_NOT_SUPPORTED, HttpStatus.BAD_REQUEST);
        mapping.put(ErrorCode.CAPABILITY_RESTRICTED, HttpStatus.BAD_REQUEST);
        mapping.put(ErrorCode.LINK_INVALID, HttpStatus.BAD_REQUEST);
        mapping.put(ErrorCode.ROLE_EXPIRED, HttpStatus.FORBIDDEN);
        mapping.put(ErrorCode.ROLE_DISABLED, HttpStatus.FORBIDDEN);
        mapping.put(ErrorCode.LINK_EXPIRED, HttpStatus.FORBIDDEN);
        mapping.put(ErrorCode.LINK_EXHAUSTED, HttpStatus.FORBIDDEN);
        mapping.put(ErrorCode.LINK_REVOKED, HttpStatus.FORBIDDEN);
        mapping.put(ErrorCode.RANGE_INVALID, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        mapping.put(ErrorCode.TASK_NOT_FOUND, HttpStatus.NOT_FOUND);
        mapping.put(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
        mapping.put(ErrorCode.DEBUG_STREAM_SOURCE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        mapping.put(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        return Map.copyOf(mapping);
    }

    private String requestId(HttpServletRequest request) {
        String value = request.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        return value == null ? "" : value;
    }
}




