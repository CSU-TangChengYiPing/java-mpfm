package com.mpfm.backend.infrastructure.persistence.entity.share;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 共享链接实体，映射 `share_links` 表并记录令牌状态、时间窗与使用次数。
 */
@Entity
@Table(name = "share_links")
public class ShareLinkEntity {
    @Id
    private UUID id;
    @Column(name = "mount_id", nullable = false)
    private UUID mountId;
    @Column(name = "role_id", nullable = false)
    private UUID roleId;
    @Column(name = "token", nullable = false, length = 128)
    private String token;
    @Column(name = "state", nullable = false, length = 16)
    private String state;
    @Column(name = "start_at")
    private OffsetDateTime startAt;
    @Column(name = "expire_at")
    private OffsetDateTime expireAt;
    @Column(name = "max_uses")
    private Integer maxUses;
    @Column(name = "used_count", nullable = false)
    private int usedCount;
    @Column(name = "role_start_at")
    private OffsetDateTime roleStartAt;
    @Column(name = "role_expire_at")
    private OffsetDateTime roleExpireAt;
    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;
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
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public OffsetDateTime getStartAt() { return startAt; }
    public void setStartAt(OffsetDateTime startAt) { this.startAt = startAt; }
    public OffsetDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(OffsetDateTime expireAt) { this.expireAt = expireAt; }
    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }
    public int getUsedCount() { return usedCount; }
    public void setUsedCount(int usedCount) { this.usedCount = usedCount; }
    public OffsetDateTime getRoleStartAt() { return roleStartAt; }
    public void setRoleStartAt(OffsetDateTime roleStartAt) { this.roleStartAt = roleStartAt; }
    public OffsetDateTime getRoleExpireAt() { return roleExpireAt; }
    public void setRoleExpireAt(OffsetDateTime roleExpireAt) { this.roleExpireAt = roleExpireAt; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}








