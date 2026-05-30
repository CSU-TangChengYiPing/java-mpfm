package com.mpfm.backend.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * HTTPS 强制跳转过滤器。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class HttpsEnforceFilter extends OncePerRequestFilter {

    private final boolean enabled;

    public HttpsEnforceFilter(@Value("${mpfm.security.https.force-redirect:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        boolean secure = request.isSecure() || "https".equalsIgnoreCase(forwardedProto);
        if (secure) {
            filterChain.doFilter(request, response);
            return;
        }

        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            host = request.getServerName();
        }
        String query = request.getQueryString();
        String target = "https://" + host + request.getRequestURI() + (query == null ? "" : "?" + query);
        response.setStatus(HttpServletResponse.SC_PERMANENT_REDIRECT);
        response.setHeader("Location", target);
    }
}






