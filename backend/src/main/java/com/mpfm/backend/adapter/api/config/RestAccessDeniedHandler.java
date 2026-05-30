package com.mpfm.backend.adapter.api.config;

import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.common.error.ErrorResponse;
import com.mpfm.backend.common.logging.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 鉴权拒绝处理器，负责返回统一无权限错误响应并记录权限拒绝审计事件。
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityEventLogger securityEventLogger;

    public RestAccessDeniedHandler(SecurityEventLogger securityEventLogger) {
        this.securityEventLogger = securityEventLogger;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        securityEventLogger.permissionDenied(request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String requestId = request.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        ErrorResponse body = ErrorResponse.of(ErrorCode.PERMISSION_DENIED, "permission denied", requestId == null ? "" : requestId);
        response.getWriter().write("{\"error\":{\"code\":\"" + body.error().code() + "\",\"message\":\"" + body.error().message() + "\",\"requestId\":\"" + body.error().requestId() + "\"}}");
    }
}




