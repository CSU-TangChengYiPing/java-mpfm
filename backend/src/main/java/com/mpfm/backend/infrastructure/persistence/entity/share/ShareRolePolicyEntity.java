package com.mpfm.backend.infrastructure.persistence.entity.share;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 共享角色策略实体，映射 `share_role_policies` 表并保存路径模式权限位。
 */
@Entity
@Table(name = "share_role_policies")
public class ShareRolePolicyEntity {
    @Id
    private UUID id;
    @Column(name = "role_id", nullable = false)
    private UUID roleId;
    @Column(name = "path_pattern", nullable = false, length = 255)
    private String pathPattern;
    @Column(name = "can_visible", nullable = false)
    private boolean canVisible;
    @Column(name = "can_read", nullable = false)
    private boolean canRead;
    @Column(name = "can_write", nullable = false)
    private boolean canWrite;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getRoleId() { return roleId; }
    public void setRoleId(UUID roleId) { this.roleId = roleId; }
    public String getPathPattern() { return pathPattern; }
    public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }
    public boolean isCanVisible() { return canVisible; }
    public void setCanVisible(boolean canVisible) { this.canVisible = canVisible; }
    public boolean isCanRead() { return canRead; }
    public void setCanRead(boolean canRead) { this.canRead = canRead; }
    public boolean isCanWrite() { return canWrite; }
    public void setCanWrite(boolean canWrite) { this.canWrite = canWrite; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}








