package com.mpfm.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 用户传输治理状态，记录上传/下载暂停开关与最后更新人。
 */
@Entity
@Table(name = "user_transfer_governance")
public class UserTransferGovernanceEntity {
    @Id
    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "upload_paused", nullable = false)
    private boolean uploadPaused;

    @Column(name = "download_paused", nullable = false)
    private boolean downloadPaused;

    @Column(name = "custom_upload_bps", nullable = false)
    private long customUploadBps;

    @Column(name = "custom_download_bps", nullable = false)
    private long customDownloadBps;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public boolean isUploadPaused() { return uploadPaused; }
    public void setUploadPaused(boolean uploadPaused) { this.uploadPaused = uploadPaused; }
    public boolean isDownloadPaused() { return downloadPaused; }
    public void setDownloadPaused(boolean downloadPaused) { this.downloadPaused = downloadPaused; }
    public long getCustomUploadBps() { return customUploadBps; }
    public void setCustomUploadBps(long customUploadBps) { this.customUploadBps = customUploadBps; }
    public long getCustomDownloadBps() { return customDownloadBps; }
    public void setCustomDownloadBps(long customDownloadBps) { this.customDownloadBps = customDownloadBps; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

