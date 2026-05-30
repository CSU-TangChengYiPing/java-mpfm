package com.mpfm.backend.common.concurrency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 写操作并发前置校验过滤器，仅对已实现并发语义的资源接口强制 `If-Match` 头。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class IfMatchHeaderEnforcer extends OncePerRequestFilter {

    private static final Set<String> GUARDED_METHODS = Set.of("PUT", "PATCH", "DELETE", "POST");
    private static final String FILE_API_PREFIX = "/api/v1/files";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isGuarded(request) && missingIfMatch(request)) {
            response.setStatus(400);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":{\"code\":\"VALIDATION_ERROR\",\"message\":\"If-Match header is required for write operations\",\"requestId\":\"\"}}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isGuarded(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // 当前仅文件写接口实现了稳定的 ETag/If-Match 并发语义，避免全量拦截误伤业务接口。
        if (!GUARDED_METHODS.contains(request.getMethod()) || !uri.startsWith(FILE_API_PREFIX)) {
            return false;
        }
        return !uri.contains("/batch-upload")
                && !uri.contains("/batch-download")
                && !uri.contains("/upload/")
                && !uri.contains("/archive/");
    }

    private boolean missingIfMatch(HttpServletRequest request) {
        String value = request.getHeader("If-Match");
        return value == null || value.isBlank();
    }
}





