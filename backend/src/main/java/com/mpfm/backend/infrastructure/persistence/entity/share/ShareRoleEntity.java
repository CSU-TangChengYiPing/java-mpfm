package com.mpfm.backend.infrastructure.persistence.entity.share;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 共享角色实体，映射 `share_roles` 表并记录角色名称、状态与角色到期时间。
 */
@Entity
@Table(name = "share_roles")
public class ShareRoleEntity {
    @Id
    private UUID id;
    @Column(name = "mount_id", nullable = false)
    private UUID mountId;
    @Column(name = "creator_user_id", nullable = false)
    private UUID creatorUserId;
    @Column(name = "name", nullable = false, length = 64)
    private String name;
    @Column(name = "is_system", nullable = false)
    private boolean system;
    @Column(name = "state", nullable = false, length = 16)
    private String state;
    @Column(name = "role_expires_at")
    private OffsetDateTime roleExpiresAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getMountId() { return mountId; }
    public void setMountId(UUID mountId) { this.mountId = mountId; }
    public UUID getCreatorUserId() { return creatorUserId; }
    public void setCreatorUserId(UUID creatorUserId) { this.creatorUserId = creatorUserId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isSystem() { return system; }
    public void setSystem(boolean system) { this.system = system; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public OffsetDateTime getRoleExpiresAt() { return roleExpiresAt; }
    public void setRoleExpiresAt(OffsetDateTime roleExpiresAt) { this.roleExpiresAt = roleExpiresAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}








