package com.mpfm.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * QoS 策略实体，定义结构化速率/并发参数与启停状态。
 */
@Entity
@Table(name = "qos_policies")
public class QosPolicyEntity {
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "max_upload_bps", nullable = false)
    private long maxUploadBps;

    @Column(name = "max_download_bps", nullable = false)
    private long maxDownloadBps;

    @Column(name = "max_concurrent_upload_tasks", nullable = false)
    private int maxConcurrentUploadTasks;

    @Column(name = "max_concurrent_download_tasks", nullable = false)
    private int maxConcurrentDownloadTasks;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getMaxUploadBps() { return maxUploadBps; }
    public void setMaxUploadBps(long maxUploadBps) { this.maxUploadBps = maxUploadBps; }
    public long getMaxDownloadBps() { return maxDownloadBps; }
    public void setMaxDownloadBps(long maxDownloadBps) { this.maxDownloadBps = maxDownloadBps; }
    public int getMaxConcurrentUploadTasks() { return maxConcurrentUploadTasks; }
    public void setMaxConcurrentUploadTasks(int maxConcurrentUploadTasks) { this.maxConcurrentUploadTasks = maxConcurrentUploadTasks; }
    public int getMaxConcurrentDownloadTasks() { return maxConcurrentDownloadTasks; }
    public void setMaxConcurrentDownloadTasks(int maxConcurrentDownloadTasks) {
        this.maxConcurrentDownloadTasks = maxConcurrentDownloadTasks;
    }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

