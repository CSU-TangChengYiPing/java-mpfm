package com.mpfm.backend.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * WebDAV Basic 认证过滤器：为 Windows/Finder 挂载场景补齐 Basic 凭证接入能力。
 */
public class WebDavBasicAuthenticationFilter extends OncePerRequestFilter {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BASIC_PREFIX = "Basic ";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DAV_ROOT = "/dav";

    private final WebDavUserCacheService userCacheService;
    private final PasswordEncoder passwordEncoder;

    public WebDavBasicAuthenticationFilter(WebDavUserCacheService userCacheService, PasswordEncoder passwordEncoder) {
        this.userCacheService = userCacheService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isDavRequest(request) || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            // Bearer 鉴权交由 JwtAuthenticationFilter 处理，避免 WebDAV Basic 挑战误拦截。
            filterChain.doFilter(request, response);
            return;
        }
        if (authorization == null || !authorization.startsWith(BASIC_PREFIX)) {
            challenge(response);
            return;
        }

        Optional<UsernamePasswordAuthenticationToken> authentication = buildAuthentication(authorization);
        if (authentication.isEmpty()) {
            challenge(response);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(authentication.get());
        filterChain.doFilter(request, response);
    }

    private boolean isDavRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && (DAV_ROOT.equals(uri) || uri.startsWith(DAV_ROOT + "/"));
    }

    private Optional<UsernamePasswordAuthenticationToken> buildAuthentication(String authorization) {
        String raw = authorization.substring(BASIC_PREFIX.length()).trim();
        if (raw.isBlank()) {
            return Optional.empty();
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        int separator = decoded.indexOf(':');
        if (separator <= 0) {
            return Optional.empty();
        }
        String username = decoded.substring(0, separator);
        String password = decoded.substring(separator + 1);
        WebDavUserCacheService.CachedUser user = userCacheService.findByUsername(username).orElse(null);
        if (user == null || user.disabled()) {
            return Optional.empty();
        }
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            return Optional.empty();
        }
        return Optional.of(new UsernamePasswordAuthenticationToken(
                username,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role().toUpperCase(Locale.ROOT)))));
    }

    private void challenge(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Basic realm=\"mpfm-webdav\"");
    }
}
