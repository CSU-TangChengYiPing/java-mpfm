package com.mpfm.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 用户资料嵌入对象，承载展示名、联系方式、界面偏好与 QoS 档位字段。
 */
@Embeddable
public class UserProfileEmbeddable {

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "preferred_language", length = 16)
    private String preferredLanguage;

    @Column(name = "file_view_mode", length = 16)
    private String fileViewMode;

    @Column(name = "qos_profile", nullable = false, length = 64)
    private String qosProfile;

    @Column(name = "custom_upload_bps", nullable = false)
    private long customUploadBps;

    @Column(name = "custom_download_bps", nullable = false)
    private long customDownloadBps;

    @Column(name = "qos_custom_enabled", nullable = false)
    private boolean qosCustomEnabled;

    @Column(name = "upload_paused", nullable = false)
    private boolean uploadPaused;

    @Column(name = "download_paused", nullable = false)
    private boolean downloadPaused;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public String getFileViewMode() { return fileViewMode; }
    public void setFileViewMode(String fileViewMode) { this.fileViewMode = fileViewMode; }
    public String getQosProfile() { return qosProfile; }
    public void setQosProfile(String qosProfile) { this.qosProfile = qosProfile; }
    public long getCustomUploadBps() { return customUploadBps; }
    public void setCustomUploadBps(long customUploadBps) { this.customUploadBps = customUploadBps; }
    public long getCustomDownloadBps() { return customDownloadBps; }
    public void setCustomDownloadBps(long customDownloadBps) { this.customDownloadBps = customDownloadBps; }
    public boolean isQosCustomEnabled() { return qosCustomEnabled; }
    public void setQosCustomEnabled(boolean qosCustomEnabled) { this.qosCustomEnabled = qosCustomEnabled; }
    public boolean isUploadPaused() { return uploadPaused; }
    public void setUploadPaused(boolean uploadPaused) { this.uploadPaused = uploadPaused; }
    public boolean isDownloadPaused() { return downloadPaused; }
    public void setDownloadPaused(boolean downloadPaused) { this.downloadPaused = downloadPaused; }
}






