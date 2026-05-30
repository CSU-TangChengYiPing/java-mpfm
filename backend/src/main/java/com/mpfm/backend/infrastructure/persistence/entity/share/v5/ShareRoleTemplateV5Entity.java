package com.mpfm.backend.infrastructure.persistence.entity.share.v5;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * v5 角色模板实体，定义模板默认权限与生命周期状态。
 */
@Entity
@Table(name = "share_role_templates_v5")
public class ShareRoleTemplateV5Entity {
    @Id
    private UUID id;
    @Column(name = "mount_id", nullable = false)
    private UUID mountId;
    @Column(name = "role_id", nullable = false)
    private UUID roleId;
    @Column(name = "name", nullable = false, length = 64)
    private String name;
    @Column(name = "state", nullable = false, length = 16)
    private String state;
    @Column(name = "default_visible", nullable = false)
    private boolean defaultVisible;
    @Column(name = "default_read", nullable = false)
    private boolean defaultRead;
    @Column(name = "default_write", nullable = false)
    private boolean defaultWrite;
    @Column(name = "version", nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getMountId() { return mountId; }
    public void setMountId(UUID mountId) { this.mountId = mountId; }
    public UUID getRoleId() { return roleId; }
    public void setRoleId(UUID roleId) { this.roleId = roleId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public boolean isDefaultVisible() { return defaultVisible; }
    public void setDefaultVisible(boolean defaultVisible) { this.defaultVisible = defaultVisible; }
    public boolean isDefaultRead() { return defaultRead; }
    public void setDefaultRead(boolean defaultRead) { this.defaultRead = defaultRead; }
    public boolean isDefaultWrite() { return defaultWrite; }
    public void setDefaultWrite(boolean defaultWrite) { this.defaultWrite = defaultWrite; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
