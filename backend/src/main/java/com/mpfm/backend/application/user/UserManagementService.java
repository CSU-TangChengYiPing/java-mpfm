package com.mpfm.backend.application.user;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户管理应用服务，负责个人资料维护与管理员用户管理流程编排。
 */
@Service
public class UserManagementService {
    private static final String MESSAGE_USER_NOT_FOUND = "user not found";
    private static final String DEFAULT_QOS_PROFILE = "default";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityEventLogger securityEventLogger;
    private final AvatarStorageService avatarStorageService;

    public UserManagementService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 SecurityEventLogger securityEventLogger,
                                 AvatarStorageService avatarStorageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityEventLogger = securityEventLogger;
        this.avatarStorageService = avatarStorageService;
    }

    public MeResult me(String username) {
        UserEntity user = requireByUsername(username);
        return toMe(user);
    }

    public MeResult updateProfile(String username, String displayName, String email, String phone) {
        UserEntity user = requireByUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);
        return toMe(user);
    }

    public MeResult updateAvatar(String username, String avatarUrl) {
        UserEntity user = requireByUsername(username);
        user.setAvatarUrl(avatarStorageService.store(user.getId().toString(), avatarUrl));
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);
        return toMe(user);
    }

    public MeResult updatePreferences(String username, String language, String fileViewMode) {
        UserEntity user = requireByUsername(username);
        user.setPreferredLanguage(language);
        user.setFileViewMode(fileViewMode);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);
        return toMe(user);
    }

    public int changeCredential(String username, String oldPassword, String newPassword) {
        UserEntity user = requireByUsername(username);
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID, "old credential invalid");
        }
        validatePasswordStrength(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setCredentialUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        user.setCredentialVersion(user.getCredentialVersion() + 1);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);
        int revoked = 0;
        securityEventLogger.managementOperation(
                new SecurityEventLogger.ManagementAuditEvent("user_change_credential", user.getUsername(), "success", null));
        return revoked;
    }

    public List<UserSummary> search(String operator, String username, String displayName, String status) {
        UserEntity op = requireByUsername(operator);
        UserStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            parsedStatus = UserStatus.valueOf(status.toUpperCase(Locale.ROOT));
        }
        if (op.getPlatformRole() == PlatformRole.USER) {
            return userRepository.search(username, displayName, parsedStatus).stream()
                    .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                    .map(this::toSummary)
                    .toList();
        }
        return userRepository.search(username, displayName, parsedStatus).stream().map(this::toSummary).toList();
    }

    public UserSummary adminCreateUser(String operator, String username, String password, String displayName,
                                       String email, String phone, PlatformRole role, String qosProfile) {
        UserEntity op = requireByUsername(operator);
        if (role == PlatformRole.ROOT) {
            throw new BusinessException(ErrorCode.OWNER_IMMUTABLE, "root can only be initialized by configuration");
        }
        requireCanManageRole(op, role);
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.CONFLICT, "username already exists");
        }
        validatePasswordStrength(password);
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername(username);
        entity.setPasswordHash(passwordEncoder.encode(password));
        entity.setDisplayName(displayName);
        entity.setEmail(email);
        entity.setPhone(phone);
        entity.setPlatformRole(role);
        entity.setStatus(UserStatus.ACTIVE);
        entity.setPreferredLanguage("zh-CN");
        entity.setFileViewMode("list");
        entity.setQosProfile(normalizeQosProfile(qosProfile));
        entity.setUploadPaused(false);
        entity.setDownloadPaused(false);
        entity.setQosCustomEnabled(false);
        entity.setCredentialUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setCredentialVersion(1);
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(entity);
        return toSummary(entity);
    }

    public UserSummary adminUpdateUser(String operator, UUID userId, String displayName, String email, String phone,
                                       PlatformRole role, UserStatus status, String qosProfile,
                                       Long customUploadBps, Long customDownloadBps,
                                       Boolean qosCustomEnabled,
                                       Boolean uploadPaused, Boolean downloadPaused) {
        UserEntity op = requireByUsername(operator);
        UserEntity target = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, MESSAGE_USER_NOT_FOUND));
        if (role == PlatformRole.ROOT && target.getPlatformRole() != PlatformRole.ROOT) {
            throw new BusinessException(ErrorCode.OWNER_IMMUTABLE, "root can only be initialized by configuration");
        }
        requireCanManageRole(op, target.getPlatformRole());
        requireCanManageRole(op, role);
        if (target.getPlatformRole() == PlatformRole.ROOT && role != PlatformRole.ROOT) {
            throw new BusinessException(ErrorCode.OWNER_IMMUTABLE, "root role immutable");
        }
        if (target.getPlatformRole() == PlatformRole.ROOT && status != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.OWNER_IMMUTABLE, "root user can not be disabled");
        }
        target.setDisplayName(displayName);
        target.setEmail(email);
        target.setPhone(phone);
        target.setPlatformRole(role);
        target.setStatus(status);
        target.setQosProfile(normalizeQosProfile(qosProfile));
        if (customUploadBps != null && customDownloadBps != null && customUploadBps > 0 && customDownloadBps > 0) {
            target.setCustomUploadBps(customUploadBps);
            target.setCustomDownloadBps(customDownloadBps);
        }
        if (qosCustomEnabled != null) {
            target.setQosCustomEnabled(qosCustomEnabled);
        }
        if (uploadPaused != null) {
            target.setUploadPaused(uploadPaused);
        }
        if (downloadPaused != null) {
            target.setDownloadPaused(downloadPaused);
        }
        target.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(target);
        return toSummary(target);
    }

    public UserSummary adminDisableUser(String operator, UUID userId) {
        UserEntity op = requireByUsername(operator);
        UserEntity target = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, MESSAGE_USER_NOT_FOUND));
        requireCanManageRole(op, target.getPlatformRole());
        if (target.getPlatformRole() == PlatformRole.ROOT) {
            throw new BusinessException(ErrorCode.OWNER_IMMUTABLE, "root user can not be disabled");
        }
        target.setStatus(UserStatus.DISABLED);
        target.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(target);
        securityEventLogger.managementOperation(
                new SecurityEventLogger.ManagementAuditEvent("admin_disable_user", target.getUsername(), "success", null));
        return toSummary(target);
    }

    public void adminResetCredential(String operator, UUID userId, String newPassword) {
        UserEntity op = requireByUsername(operator);
        UserEntity target = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, MESSAGE_USER_NOT_FOUND));
        requireCanManageRole(op, target.getPlatformRole());
        validatePasswordStrength(newPassword);
        target.setPasswordHash(passwordEncoder.encode(newPassword));
        target.setCredentialUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        target.setCredentialVersion(target.getCredentialVersion() + 1);
        target.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(target);
        securityEventLogger.managementOperation(
                new SecurityEventLogger.ManagementAuditEvent("admin_reset_credential", target.getUsername(), "success", null));
    }

    private void requireCanManageRole(UserEntity operator, PlatformRole role) {
        PlatformRole opRole = operator.getPlatformRole();
        if (opRole == PlatformRole.ROOT) {
            return;
        }
        if (opRole == PlatformRole.ADMIN && role == PlatformRole.USER) {
            return;
        }
        throw new BusinessException(ErrorCode.PERMISSION_DENIED, "insufficient role permission");
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "password strength invalid");
        }
    }

    private UserEntity requireByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, MESSAGE_USER_NOT_FOUND));
    }

    private MeResult toMe(UserEntity user) {
        String signedAvatarUrl = avatarStorageService.signAvatarUrl(user.getId().toString(), user.getAvatarUrl());
        return new MeResult(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                signedAvatarUrl,
                user.getPreferredLanguage(),
                user.getFileViewMode(),
                user.getQosProfile(),
                user.getPlatformRole().name(),
                user.getStatus().name()
        );
    }

    private UserSummary toSummary(UserEntity user) {
        String signedAvatarUrl = avatarStorageService.signAvatarUrl(user.getId().toString(), user.getAvatarUrl());
        return new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                signedAvatarUrl,
                user.getPlatformRole().name(),
                user.getStatus().name(),
                user.getQosProfile(),
                user.getCustomUploadBps(),
                user.getCustomDownloadBps(),
                user.isQosCustomEnabled(),
                user.isUploadPaused(),
                user.isDownloadPaused()
        );
    }

    private String normalizeQosProfile(String qosProfile) {
        if (qosProfile == null || qosProfile.isBlank()) {
            return DEFAULT_QOS_PROFILE;
        }
        return qosProfile.trim();
    }

    /** 当前用户详情模型，承载个人资料、偏好、角色与状态字段。 */
    public record MeResult(UUID userId, String username, String displayName, String email, String phone,
                           String avatarUrl, String language, String fileViewMode, String qosProfile, String role, String status) {
    }

    /** 用户摘要模型，提供管理列表与检索场景的最小字段集。 */
    public record UserSummary(UUID userId, String username, String displayName, String avatarUrl, String role, String status, String qosProfile,
                              long customUploadBps, long customDownloadBps, boolean qosCustomEnabled,
                              boolean uploadPaused, boolean downloadPaused) {
    }
}




