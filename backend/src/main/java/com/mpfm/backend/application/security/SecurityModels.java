package com.mpfm.backend.application.security;

/**
 * 安全域模型集合，定义授权、限流与告警相关的数据结构。
 */
public class SecurityModels {

    /** 下载授权模型，包含授权标识、目标路径、有效时长与过期时间。 */
    public record DownloadGrant(String grantId, String path, int ttlSeconds, String expiresAt) { }
    /** 键值配置模型，用于表达策略/证书等配置项的标识和值。 */
    public record NamedValue(String id, String value) { }
    /** 安全告警模型，返回告警标识、类型、状态与创建时间。 */
    public record AlertItem(String alertId, String type, String status, String createdAt) { }
}




