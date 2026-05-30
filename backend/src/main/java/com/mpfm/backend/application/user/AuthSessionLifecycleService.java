package com.mpfm.backend.application.user;

import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.common.security.JwtPrincipal;
import com.mpfm.backend.common.security.JwtTokenService;
import com.mpfm.backend.infrastructure.persistence.entity.AuthSessionEntity;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.repository.AuthSessionRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 认证会话生命周期服务，负责令牌签发、刷新、注销与会话列表管理。
 */
@Service
public class AuthSessionLifecycleService {

    private static final String SESSION_ACTIVE = "active";
    private static final String SESSION_REVOKED = "revoked";
    private static final String RESULT_SUCCESS = "success";
    private static final String MESSAGE_SESSION_NOT_FOUND = "session not found";

    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final JwtTokenService jwtTokenService;
    private final SecurityEventLogger securityEventLogger;

    public AuthSessionLifecycleService(UserRepository userRepository,
                                       AuthSessionRepository authSessionRepository,
                                       JwtTokenService jwtTokenService,
                                       SecurityEventLogger securityEventLogger) {
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.jwtTokenService = jwtTokenService;
        this.securityEventLogger = securityEventLogger;
    }

    public AuthApplicationService.AuthResult issueToken(UserEntity entity, AuthApplicationService.ClientContext clientContext) {
        String role = entity.getPlatformRole().name();
        String accessToken = jwtTokenService.issueAccessToken(entity.getUsername(), role, entity.getCredentialVersion());
        String refreshToken = jwtTokenService.issueRefreshToken(entity.getUsername(), role, entity.getCredentialVersion());

        AuthSessionEntity session = new AuthSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(entity.getId());
        session.setUsername(entity.getUsername());
        session.setRefreshHash(digest(refreshToken));
        session.setStatus(SESSION_ACTIVE);
        session.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        session.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(jwtTokenService.getRefreshExpireSeconds()));
        session.setClientIp(clientContext.clientIp());
        session.setUserAgent(clientContext.userAgent());
        authSessionRepository.save(session);

        return new AuthApplicationService.AuthResult(accessToken, refreshToken, session.getId(), entity.getUsername(), role);
    }

    public AuthApplicationService.AuthResult refresh(String refreshToken, UUID sessionId, AuthApplicationService.ClientContext clientContext) {
        JwtPrincipal principal = jwtTokenService.parse(refreshToken);
        if (!"refresh".equals(principal.type())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID, "invalid refresh token");
        }
        String refreshHash = digest(refreshToken);
        AuthSessionEntity session = authSessionRepository.findByIdAndStatus(sessionId, SESSION_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID, MESSAGE_SESSION_NOT_FOUND));
        if (!refreshHash.equals(session.getRefreshHash())) {
            securityEventLogger.managementOperation(
                    new SecurityEventLogger.ManagementAuditEvent("auth_refresh", principal.subject(), "failure", ErrorCode.AUTH_INVALID.name()));
            throw new BusinessException(ErrorCode.AUTH_INVALID, "refresh token not match session");
        }
        if (session.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            session.setStatus(SESSION_REVOKED);
            authSessionRepository.save(session);
            securityEventLogger.managementOperation(
                    new SecurityEventLogger.ManagementAuditEvent("auth_refresh", principal.subject(), "failure", ErrorCode.AUTH_INVALID.name()));
            throw new BusinessException(ErrorCode.AUTH_INVALID, "session expired");
        }
        UserEntity user = userRepository.findByUsername(principal.subject())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID, "user not found"));
        session.setStatus(SESSION_REVOKED);
        authSessionRepository.save(session);
        securityEventLogger.managementOperation(
                new SecurityEventLogger.ManagementAuditEvent("auth_refresh", user.getUsername(), RESULT_SUCCESS, null));
        return issueToken(user, clientContext);
    }

    public void logout(String refreshToken, UUID sessionId) {
        String refreshHash = digest(refreshToken);
        AuthSessionEntity session = authSessionRepository.findByIdAndStatus(sessionId, SESSION_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID, MESSAGE_SESSION_NOT_FOUND));
        if (!refreshHash.equals(session.getRefreshHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID, "refresh token not match session");
        }
        session.setStatus(SESSION_REVOKED);
        authSessionRepository.save(session);
        securityEventLogger.managementOperation(
                new SecurityEventLogger.ManagementAuditEvent("auth_logout", session.getUsername(), RESULT_SUCCESS, null));
    }

    public List<AuthApplicationService.UserSessionItem> sessions(String username) {
        return authSessionRepository.findByUsernameAndStatus(username, SESSION_ACTIVE).stream()
                .map(item -> new AuthApplicationService.UserSessionItem(
                        item.getId(),
                        item.getStatus(),
                        item.getExpiresAt().toString(),
                        item.getClientIp(),
                        item.getUserAgent(),
                        toDeviceLabel(item.getUserAgent())))
                .toList();
    }

    public void revokeSession(String username, UUID sessionId) {
        AuthSessionEntity session = authSessionRepository.findByIdAndUsernameAndStatus(sessionId, username, SESSION_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "session not found"));
        session.setStatus(SESSION_REVOKED);
        authSessionRepository.save(session);
        securityEventLogger.managementOperation(
                new SecurityEventLogger.ManagementAuditEvent("session_revoke", username, RESULT_SUCCESS, null));
    }

    private String digest(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "digest failed", ex);
        }
    }

    private String toDeviceLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac os")) {
            return "macOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
            return "iOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return "Other";
    }
}
