package com.mpfm.backend.application.user;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证应用服务，负责注册、登录、令牌刷新、会话查询与会话撤销流程编排。
 */
@Service
public class AuthApplicationService {

    private static final int LOGIN_FAILED_THRESHOLD = 5;
    private static final long LOGIN_LOCK_MINUTES = 15;
    private static final String RESULT_SUCCESS = "success";
    private static final int CAPTCHA_TRIGGER_FAILED_COUNT = 3;
    private static final String DEFAULT_QOS_PROFILE = "default";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;
    private final AuthSessionLifecycleService authSessionLifecycleService;
    private final SecurityEventLogger securityEventLogger;

    public AuthApplicationService(UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  CaptchaService captchaService,
                                  AuthSessionLifecycleService authSessionLifecycleService,
                                  SecurityEventLogger securityEventLogger) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.captchaService = captchaService;
        this.authSessionLifecycleService = authSessionLifecycleService;
        this.securityEventLogger = securityEventLogger;
    }

    public AuthResult register(RegisterCommand command) {
        return register(command, ClientContext.UNKNOWN);
    }

    public AuthResult register(RegisterCommand command, ClientContext clientContext) {
        if (!isBlank(command.captchaId()) || !isBlank(command.captchaAnswer())) {
            captchaService.verify(command.captchaId(), command.captchaAnswer());
        }
        if (userRepository.existsByUsername(command.username())) {
            throw new BusinessException(ErrorCode.CONFLICT, "username already exists");
        }
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername(command.username());
        entity.setPasswordHash(passwordEncoder.encode(command.password()));
        entity.setDisplayName(command.displayName());
        entity.setEmail(command.email());
        entity.setPhone(command.phone());
        entity.setPlatformRole(PlatformRole.USER);
        entity.setStatus(UserStatus.ACTIVE);
        entity.setPreferredLanguage("zh-CN");
        entity.setFileViewMode("list");
        entity.setQosProfile(DEFAULT_QOS_PROFILE);
        entity.setCredentialUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setCredentialVersion(1);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(entity);
        securityEventLogger.managementOperation(
                new SecurityEventLogger.ManagementAuditEvent("auth_register", entity.getUsername(), RESULT_SUCCESS, null));
        return authSessionLifecycleService.issueToken(entity, clientContext);
    }

    public AuthResult login(LoginCommand command) {
        return login(command, ClientContext.UNKNOWN);
    }

    public AuthResult login(LoginCommand command, ClientContext clientContext) {
        UserEntity entity = userRepository.findByUsername(command.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID, "invalid credentials"));
        if (entity.getLockedUntil() != null && entity.getLockedUntil().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException(ErrorCode.AUTH_INVALID, "account temporarily locked:" + entity.getLockedUntil().toEpochSecond());
        }
        if (entity.getFailedLoginCount() >= CAPTCHA_TRIGGER_FAILED_COUNT) {
            if (isBlank(command.captchaId()) || isBlank(command.captchaAnswer())) {
                throw new BusinessException(ErrorCode.CAPTCHA_REQUIRED, "captcha required");
            }
            captchaService.verify(command.captchaId(), command.captchaAnswer());
        }
        if (entity.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "account disabled");
        }
        if (!passwordEncoder.matches(command.password(), entity.getPasswordHash())) {
            int failedCount = entity.getFailedLoginCount() + 1;
            entity.setFailedLoginCount(failedCount);
            if (failedCount >= LOGIN_FAILED_THRESHOLD) {
                entity.setLockedUntil(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(LOGIN_LOCK_MINUTES));
                entity.setFailedLoginCount(0);
            }
            userRepository.save(entity);
            securityEventLogger.managementOperation(
                    new SecurityEventLogger.ManagementAuditEvent("auth_login", entity.getUsername(), "failure", ErrorCode.AUTH_INVALID.name()));
            throw new BusinessException(ErrorCode.AUTH_INVALID, "invalid credentials");
        }
        entity.setFailedLoginCount(0);
        entity.setLockedUntil(null);
        userRepository.save(entity);
        securityEventLogger.managementOperation(
                new SecurityEventLogger.ManagementAuditEvent("auth_login", entity.getUsername(), RESULT_SUCCESS, null));
        return authSessionLifecycleService.issueToken(entity, clientContext);
    }

    public CaptchaService.CaptchaIssue issueCaptcha(String scene) {
        return captchaService.issue(scene);
    }

    public AuthResult refresh(String refreshToken, UUID sessionId) {
        return refresh(refreshToken, sessionId, ClientContext.UNKNOWN);
    }

    public AuthResult refresh(String refreshToken, UUID sessionId, ClientContext clientContext) {
        return authSessionLifecycleService.refresh(refreshToken, sessionId, clientContext);
    }

    public void logout(String refreshToken, UUID sessionId) {
        authSessionLifecycleService.logout(refreshToken, sessionId);
    }

    public SessionInfo currentSession(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID, "user not found"));
        return new SessionInfo(user.getUsername(), user.getPlatformRole().name(), user.getStatus().name());
    }

    public List<UserSessionItem> sessions(String username) {
        return authSessionLifecycleService.sessions(username);
    }

    public void revokeSession(String username, UUID sessionId) {
        authSessionLifecycleService.revokeSession(username, sessionId);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 注册命令模型，承载注册时的账号资料与验证码参数。 */
    public record RegisterCommand(String username, String password, String displayName, String email, String phone,
                                  String captchaId, String captchaAnswer) { }
    /** 登录命令模型，承载登录凭证与验证码参数。 */
    public record LoginCommand(String username, String password, String captchaId, String captchaAnswer) { }
    /** 认证结果模型，返回访问令牌、刷新令牌与用户身份摘要。 */
    public record AuthResult(String accessToken, String refreshToken, UUID sessionId, String username, String role) { }
    /** 当前会话信息模型，返回用户名、角色与会话状态。 */
    public record SessionInfo(String username, String role, String status) { }
    /** 用户会话项模型，描述单个会话的标识、状态与过期时间。 */
    public record UserSessionItem(UUID sessionId, String status, String expiresAt, String clientIp, String userAgent, String deviceLabel) { }
    /** 客户端上下文模型，承载会话创建时的来源 IP 与设备 UA 信息。 */
    public record ClientContext(String clientIp, String userAgent) {
        public static final ClientContext UNKNOWN = new ClientContext("unknown", "unknown");
    }
}




