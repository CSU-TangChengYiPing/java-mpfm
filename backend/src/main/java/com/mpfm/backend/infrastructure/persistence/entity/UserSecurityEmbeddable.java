package com.mpfm.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.OffsetDateTime;

/**
 * 用户安全嵌入对象，承载密码哈希、失败计数、锁定时间与凭证版本字段。
 */
@Embeddable
public class UserSecurityEmbeddable {

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "credential_updated_at")
    private OffsetDateTime credentialUpdatedAt;

    @Column(name = "credential_version", nullable = false)
    private int credentialVersion;

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public void setFailedLoginCount(int failedLoginCount) { this.failedLoginCount = failedLoginCount; }
    public OffsetDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(OffsetDateTime lockedUntil) { this.lockedUntil = lockedUntil; }
    public OffsetDateTime getCredentialUpdatedAt() { return credentialUpdatedAt; }
    public void setCredentialUpdatedAt(OffsetDateTime credentialUpdatedAt) { this.credentialUpdatedAt = credentialUpdatedAt; }
    public int getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(int credentialVersion) { this.credentialVersion = credentialVersion; }
}






