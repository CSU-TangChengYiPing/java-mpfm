package com.mpfm.backend.common.audit;

/**
 * AuditEvent 审计事件枚举，定义审计事件编码语义。
 */
public enum AuditEvent {
    /** 示例成功审计事件。 */
    DEMO_SUCCESS("demo_success", "demo", "low"),
    /** 示例失败审计事件。 */
    DEMO_FAILURE("demo_failure", "demo", "high");

    private final String action;
    private final String target;
    private final String riskLevel;

    AuditEvent(String action, String target, String riskLevel) {
        this.action = action;
        this.target = target;
        this.riskLevel = riskLevel;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getRiskLevel() {
        return riskLevel;
    }
}





