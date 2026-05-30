package com.mpfm.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 挂载实体，映射 `mounts` 表并保存归属、路径、状态、容量与软删除时间字段。
 */
@Entity
@Table(name = "mounts")
public class MountEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "virtual_path", nullable = false, length = 255)
    private String virtualPath;

    @Column(name = "physical_root", nullable = false, length = 512)
    private String physicalRoot;

    @Column(name = "state", nullable = false, length = 32)
    private String state;

    @Column(name = "shared_enabled", nullable = false)
    private boolean sharedEnabled;

    @Column(name = "capacity_bytes")
    private Long capacityBytes;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVirtualPath() { return virtualPath; }
    public void setVirtualPath(String virtualPath) { this.virtualPath = virtualPath; }
    public String getPhysicalRoot() { return physicalRoot; }
    public void setPhysicalRoot(String physicalRoot) { this.physicalRoot = physicalRoot; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public boolean isSharedEnabled() { return sharedEnabled; }
    public void setSharedEnabled(boolean sharedEnabled) { this.sharedEnabled = sharedEnabled; }
    public Long getCapacityBytes() { return capacityBytes; }
    public void setCapacityBytes(Long capacityBytes) { this.capacityBytes = capacityBytes; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}





