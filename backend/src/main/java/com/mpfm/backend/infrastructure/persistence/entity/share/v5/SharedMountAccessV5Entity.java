package com.mpfm.backend.infrastructure.persistence.entity.share.v5;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * v5 用户共享授权实体，记录用户在挂载下被授予的角色集合。
 */
@Entity
@Table(name = "shared_mount_accesses_v5")
public class SharedMountAccessV5Entity {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "mount_id", nullable = false)
    private UUID mountId;
    @Column(name = "role_id", nullable = false)
    private UUID roleId;
    @Column(name = "active", nullable = false)
    private boolean active;
    @Column(name = "granted_by_link_id", nullable = false)
    private UUID grantedByLinkId;
    @Column(name = "granted_at", nullable = false)
    private OffsetDateTime grantedAt;
    @Column(name = "role_start_at")
    private OffsetDateTime roleStartAt;
    @Column(name = "role_expire_at")
    private OffsetDateTime roleExpireAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getMountId() { return mountId; }
    public void setMountId(UUID mountId) { this.mountId = mountId; }
    public UUID getRoleId() { return roleId; }
    public void setRoleId(UUID roleId) { this.roleId = roleId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public UUID getGrantedByLinkId() { return grantedByLinkId; }
    public void setGrantedByLinkId(UUID grantedByLinkId) { this.grantedByLinkId = grantedByLinkId; }
    public OffsetDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(OffsetDateTime grantedAt) { this.grantedAt = grantedAt; }
    public OffsetDateTime getRoleStartAt() { return roleStartAt; }
    public void setRoleStartAt(OffsetDateTime roleStartAt) { this.roleStartAt = roleStartAt; }
    public OffsetDateTime getRoleExpireAt() { return roleExpireAt; }
    public void setRoleExpireAt(OffsetDateTime roleExpireAt) { this.roleExpireAt = roleExpireAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

