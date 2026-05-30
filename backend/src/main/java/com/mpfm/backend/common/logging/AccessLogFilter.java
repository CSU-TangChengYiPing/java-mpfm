package com.mpfm.backend.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * AccessLogFilter 过滤器，负责请求链路治理与安全校验。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger("ACCESS");
    private static final int MAX_BODY_LOG_SIZE = 2048;
    private static final int MAX_REQUEST_CACHE_SIZE = 16 * 1024;
    private static final String BODY_OMITTED = "<omitted>";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, MAX_REQUEST_CACHE_SIZE);
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            long costMs = System.currentTimeMillis() - start;
            String query = request.getQueryString();
            String sanitizedQuery = query == null ? "" : SensitiveDataSanitizer.sanitizeText(query);
            String sanitizedBody = shouldOmitBody(request)
                    ? BODY_OMITTED
                    : sanitizeBody(wrappedRequest.getContentAsByteArray());

            if (LOG.isInfoEnabled()) {
                LOG.info("request method={} path={} status={} costMs={} query={} body={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        costMs,
                        clip(sanitizedQuery),
                        clip(sanitizedBody));
            }
        }
    }

    private String sanitizeBody(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        String payload = new String(body, StandardCharsets.UTF_8);
        return SensitiveDataSanitizer.sanitizeJson(payload);
    }

    static boolean shouldOmitBody(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("multipart/form-data")
                || normalized.startsWith("application/octet-stream");
    }

    private String clip(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_BODY_LOG_SIZE) {
            return text;
        }
        return text.substring(0, MAX_BODY_LOG_SIZE) + "...(truncated)";
    }
}





