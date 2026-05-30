package com.mpfm.backend.common.security;

import com.mpfm.backend.common.error.ErrorCode;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import com.mpfm.backend.application.user.UserStatus;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JwtAuthenticationFilter 过滤器，负责请求链路治理与安全校验。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = 7;
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String DOWNLOAD_PROXY_PATH = "/api/v4/transfers/downloads/proxy";
    private static final String ACCESS_TOKEN_QUERY = "access_token";

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, UserRepository userRepository) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        String token = resolveToken(request, header);
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<JwtPrincipal> principal = parsePrincipal(token);
        if (principal.isEmpty()) {
            writeUnauthorized(response);
            return;
        }
        if (!ACCESS_TOKEN_TYPE.equals(principal.get().type())) {
            writeUnauthorized(response);
            return;
        }

        Optional<UsernamePasswordAuthenticationToken> authentication = buildAuthentication(principal.get());
        if (authentication.isEmpty()) {
            writeUnauthorized(response);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(authentication.get());
        filterChain.doFilter(request, response);
    }

    /**
     * 解析访问令牌：常规链路优先读取 Authorization；下载代理路径允许 query token 以支持媒体标签直连预览。
     */
    private String resolveToken(HttpServletRequest request, String header) {
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX_LENGTH);
        }
        String requestUri = request.getRequestURI();
        if (requestUri != null && requestUri.startsWith(DOWNLOAD_PROXY_PATH)) {
            String queryToken = request.getParameter(ACCESS_TOKEN_QUERY);
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken.trim();
            }
        }
        return null;
    }

    private Optional<JwtPrincipal> parsePrincipal(String token) {
        try {
            return Optional.of(jwtTokenService.parse(token));
        } catch (JwtException ex) {
            return Optional.empty();
        }
    }

    private Optional<UsernamePasswordAuthenticationToken> buildAuthentication(JwtPrincipal principal) {
        UserEntity user = userRepository.findByUsername(principal.subject()).orElse(null);
        if (!isUserAllowed(principal, user)) {
            return Optional.empty();
        }
        return Optional.of(new UsernamePasswordAuthenticationToken(
                principal.subject(),
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().toUpperCase(Locale.ROOT)))));
    }

    private boolean isUserAllowed(JwtPrincipal principal, UserEntity user) {
        if (user == null || user.getStatus() == UserStatus.DISABLED) {
            return false;
        }
        if (principal.credentialVersion() != user.getCredentialVersion()) {
            return false;
        }
        OffsetDateTime lockedUntil = user.getLockedUntil();
        return lockedUntil == null || !lockedUntil.isAfter(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter()
                .write("{\"error\":{\"code\":\"" + ErrorCode.AUTH_INVALID.name()
                        + "\",\"message\":\"invalid token\",\"requestId\":\"\"}}");
    }
}





