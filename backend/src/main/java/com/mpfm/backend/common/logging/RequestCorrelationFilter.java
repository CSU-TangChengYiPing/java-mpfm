package com.mpfm.backend.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * RequestCorrelationFilter 过滤器，负责请求链路治理与安全校验。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    /** 请求链路ID响应头名称。 */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final int TRACEPARENT_PART_SIZE = 4;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = normalize(request.getHeader(REQUEST_ID_HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        String traceId = extractTraceId(request.getHeader(TRACEPARENT_HEADER));
        if (traceId == null) {
            traceId = requestId;
        }

        MDC.put("requestId", requestId);
        MDC.put("traceId", traceId);
        MDC.put("method", request.getMethod());
        MDC.put("path", request.getRequestURI());

        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String normalize(String input) {
        if (input == null) {
            return null;
        }
        String value = input.replaceAll("[\\r\\n]", "").trim();
        return value.isEmpty() ? null : value;
    }

    private String extractTraceId(String traceparent) {
        String value = normalize(traceparent);
        if (value == null) {
            return null;
        }
        String[] parts = value.split("-");
        if (parts.length < TRACEPARENT_PART_SIZE) {
            return null;
        }
        return parts[1];
    }
}





