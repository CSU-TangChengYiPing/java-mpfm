package com.mpfm.backend.common.error;

/**
 * ErrorCode 错误码枚举，定义统一错误语义。
 */
public enum ErrorCode {
    /** 需要认证后才能访问。 */
    AUTH_REQUIRED,
    /** 认证信息无效或过期。 */
    AUTH_INVALID,
    /** 当前登录风控要求验证码。 */
    CAPTCHA_REQUIRED,
    /** 验证码无效、过期或重复使用。 */
    CAPTCHA_INVALID,
    /** 已认证但无权限执行当前操作。 */
    PERMISSION_DENIED,
    /** 请求参数或业务校验不通过。 */
    VALIDATION_ERROR,
    /** 请求参数不合法。 */
    INVALID_ARGUMENT,
    /** 资源状态冲突。 */
    CONFLICT,
    /** 版本号不匹配导致并发冲突。 */
    VERSION_CONFLICT,
    /** 状态机不允许当前状态迁移。 */
    INVALID_STATE_TRANSITION,
    /** 当前能力未被实现。 */
    CAPABILITY_NOT_SUPPORTED,
    /** 当前能力受策略限制。 */
    CAPABILITY_RESTRICTED,
    /** 角色已过期。 */
    ROLE_EXPIRED,
    /** 角色已被禁用。 */
    ROLE_DISABLED,
    /** 资源所有者字段不可修改。 */
    OWNER_IMMUTABLE,
    /** 链接已过期。 */
    LINK_EXPIRED,
    /** 链接次数已耗尽。 */
    LINK_EXHAUSTED,
    /** 链接已被撤销。 */
    LINK_REVOKED,
    /** 链接无效。 */
    LINK_INVALID,
    /** 请求范围参数不合法。 */
    RANGE_INVALID,
    /** 异步任务不存在。 */
    TASK_NOT_FOUND,
    /** 资源不存在。 */
    RESOURCE_NOT_FOUND,
    /** 调试日志流源暂不可用。 */
    DEBUG_STREAM_SOURCE_UNAVAILABLE,
    /** 服务内部异常。 */
    INTERNAL_ERROR
}





