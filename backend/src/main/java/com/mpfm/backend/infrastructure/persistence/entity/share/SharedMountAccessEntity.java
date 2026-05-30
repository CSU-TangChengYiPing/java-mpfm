package com.mpfm.backend.infrastructure.persistence.entity.share;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 共享挂载访问实体，映射 `shared_mount_accesses` 表并记录用户-挂载-角色授权关系。
 */
@Entity
@Table(name = "shared_mount_accesses")
public class SharedMountAccessEntity {
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
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}








