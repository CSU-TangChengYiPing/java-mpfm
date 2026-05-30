package com.mpfm.backend.infrastructure.persistence.entity.share.v5;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * v5 角色模板特权实体，定义目标节点级别的特权允许集。
 */
@Entity
@Table(name = "share_role_template_privileges_v5")
public class ShareRoleTemplatePrivilegeV5Entity {
    @Id
    private UUID id;
    @Column(name = "template_id", nullable = false)
    private UUID templateId;
    @Column(name = "mount_id", nullable = false)
    private UUID mountId;
    @Column(name = "target_path", nullable = false)
    private String targetPath;
    @Column(name = "allow_visible", nullable = false)
    private boolean allowVisible;
    @Column(name = "allow_read", nullable = false)
    private boolean allowRead;
    @Column(name = "allow_write", nullable = false)
    private boolean allowWrite;
    @Column(name = "version", nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }
    public UUID getMountId() { return mountId; }
    public void setMountId(UUID mountId) { this.mountId = mountId; }
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
    public boolean isAllowVisible() { return allowVisible; }
    public void setAllowVisible(boolean allowVisible) { this.allowVisible = allowVisible; }
    public boolean isAllowRead() { return allowRead; }
    public void setAllowRead(boolean allowRead) { this.allowRead = allowRead; }
    public boolean isAllowWrite() { return allowWrite; }
    public void setAllowWrite(boolean allowWrite) { this.allowWrite = allowWrite; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
