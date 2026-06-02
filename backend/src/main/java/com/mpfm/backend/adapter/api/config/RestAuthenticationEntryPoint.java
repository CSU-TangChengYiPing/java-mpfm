package com.mpfm.backend.adapter.api.config;

import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.common.error.ErrorResponse;
import com.mpfm.backend.common.logging.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 认证失败入口处理器，负责返回统一未认证错误响应并记录认证失败审计事件。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityEventLogger securityEventLogger;

    public RestAuthenticationEntryPoint(SecurityEventLogger securityEventLogger) {
        this.securityEventLogger = securityEventLogger;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        securityEventLogger.authFailure(authException.getClass().getSimpleName(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        if (isWebDavDiscoveryRequest(request)) {
            // WebDAV 客户端依赖该头触发凭据协商，否则会持续匿名重试。
            response.setHeader("WWW-Authenticate", "Basic realm=\"mpfm-webdav\"");
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String requestId = request.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        ErrorResponse body = ErrorResponse.of(ErrorCode.AUTH_REQUIRED, "authentication required", requestId == null ? "" : requestId);
        response.getWriter().write("{\"error\":{\"code\":\"" + body.error().code() + "\",\"message\":\"" + body.error().message() + "\",\"requestId\":\"" + body.error().requestId() + "\"}}");
    }

    private boolean isWebDavDiscoveryRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri == null || requestUri.isBlank()) {
            return false;
        }
        return "/".equals(requestUri)
                || "/dav".equals(requestUri)
                || requestUri.startsWith("/dav/");
    }
}




