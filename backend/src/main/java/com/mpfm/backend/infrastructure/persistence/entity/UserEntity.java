package com.mpfm.backend.infrastructure.persistence.entity;

import com.mpfm.backend.application.user.PlatformRole;
import com.mpfm.backend.application.user.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 用户实体，映射 `users` 表并聚合资料、安全与审计三个嵌入对象字段。
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 64)
    private String username;

    @Embedded
    private final UserProfileEmbeddable profile = new UserProfileEmbeddable();

    @Embedded
    private final UserSecurityEmbeddable security = new UserSecurityEmbeddable();

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false, length = 16)
    private PlatformRole platformRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status;

    @Embedded
    private final UserAuditEmbeddable audit = new UserAuditEmbeddable();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return security.getPasswordHash(); }
    public void setPasswordHash(String passwordHash) { security.setPasswordHash(passwordHash); }
    public String getDisplayName() { return profile.getDisplayName(); }
    public void setDisplayName(String displayName) { profile.setDisplayName(displayName); }
    public String getEmail() { return profile.getEmail(); }
    public void setEmail(String email) { profile.setEmail(email); }
    public String getPhone() { return profile.getPhone(); }
    public void setPhone(String phone) { profile.setPhone(phone); }
    public String getAvatarUrl() { return profile.getAvatarUrl(); }
    public void setAvatarUrl(String avatarUrl) { profile.setAvatarUrl(avatarUrl); }
    public String getPreferredLanguage() { return profile.getPreferredLanguage(); }
    public void setPreferredLanguage(String preferredLanguage) { profile.setPreferredLanguage(preferredLanguage); }
    public String getFileViewMode() { return profile.getFileViewMode(); }
    public void setFileViewMode(String fileViewMode) { profile.setFileViewMode(fileViewMode); }
    public String getQosProfile() { return profile.getQosProfile(); }
    public void setQosProfile(String qosProfile) { profile.setQosProfile(qosProfile); }
    public long getCustomUploadBps() { return profile.getCustomUploadBps(); }
    public void setCustomUploadBps(long customUploadBps) { profile.setCustomUploadBps(customUploadBps); }
    public long getCustomDownloadBps() { return profile.getCustomDownloadBps(); }
    public void setCustomDownloadBps(long customDownloadBps) { profile.setCustomDownloadBps(customDownloadBps); }
    public boolean isQosCustomEnabled() { return profile.isQosCustomEnabled(); }
    public void setQosCustomEnabled(boolean qosCustomEnabled) { profile.setQosCustomEnabled(qosCustomEnabled); }
    public boolean isUploadPaused() { return profile.isUploadPaused(); }
    public void setUploadPaused(boolean uploadPaused) { profile.setUploadPaused(uploadPaused); }
    public boolean isDownloadPaused() { return profile.isDownloadPaused(); }
    public void setDownloadPaused(boolean downloadPaused) { profile.setDownloadPaused(downloadPaused); }
    public int getFailedLoginCount() { return security.getFailedLoginCount(); }
    public void setFailedLoginCount(int failedLoginCount) { security.setFailedLoginCount(failedLoginCount); }
    public OffsetDateTime getLockedUntil() { return security.getLockedUntil(); }
    public void setLockedUntil(OffsetDateTime lockedUntil) { security.setLockedUntil(lockedUntil); }
    public OffsetDateTime getCredentialUpdatedAt() { return security.getCredentialUpdatedAt(); }
    public void setCredentialUpdatedAt(OffsetDateTime credentialUpdatedAt) { security.setCredentialUpdatedAt(credentialUpdatedAt); }
    public int getCredentialVersion() { return security.getCredentialVersion(); }
    public void setCredentialVersion(int credentialVersion) { security.setCredentialVersion(credentialVersion); }
    public PlatformRole getPlatformRole() { return platformRole; }
    public void setPlatformRole(PlatformRole platformRole) { this.platformRole = platformRole; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return audit.getCreatedAt(); }
    public void setCreatedAt(OffsetDateTime createdAt) { audit.setCreatedAt(createdAt); }
    public OffsetDateTime getUpdatedAt() { return audit.getUpdatedAt(); }
    public void setUpdatedAt(OffsetDateTime updatedAt) { audit.setUpdatedAt(updatedAt); }
}





