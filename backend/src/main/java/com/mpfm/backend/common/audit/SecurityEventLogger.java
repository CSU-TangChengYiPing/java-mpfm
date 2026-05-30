package com.mpfm.backend.common.audit;

import com.mpfm.backend.infrastructure.persistence.entity.AuditLogEntity;
import com.mpfm.backend.infrastructure.persistence.repository.AuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 安全事件记录器，将认证/鉴权/校验失败事件同步写入安全日志、审计表与指标计数器。
 */
@Component
public class SecurityEventLogger {

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("SECURITY");

    private final AuditLogRepository auditLogRepository;
    private final MeterRegistry meterRegistry;

    public SecurityEventLogger(@Nullable AuditLogRepository auditLogRepository,
                               @Nullable MeterRegistry meterRegistry) {
        this.auditLogRepository = auditLogRepository;
        this.meterRegistry = meterRegistry;
    }

    public void authFailure(String reason, String path) {
        write("auth_failure", path, "failure", reason, "high");
    }

    public void permissionDenied(String path) {
        write("permission_denied", path, "failure", "PERMISSION_DENIED", "medium");
    }

    public void validationFailure(String path) {
        write("validation_failure", path, "failure", "VALIDATION_ERROR", "low");
    }

    public void managementOperation(ManagementAuditEvent event) {
        write(event.action(), event.target(), event.result(), event.errorCode(), "high");
    }

    private void write(String action, String target, String result, String errorCode, String risk) {
        String actor = MDC.get("actor") == null ? "system" : MDC.get("actor");
        String requestId = MDC.get("requestId") == null ? "" : MDC.get("requestId");
        String traceId = MDC.get("traceId") == null ? "" : MDC.get("traceId");

        SECURITY_LOG.warn("action={} target={} result={} errorCode={} risk={} actor={} requestId={} traceId={}",
                action, target, result, errorCode, risk, actor, requestId, traceId);

        if (auditLogRepository != null) {
            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(UUID.randomUUID());
            entity.setOperator(actor);
            entity.setAction(action);
            entity.setTarget(target == null ? "unknown" : target);
            entity.setResult(result);
            entity.setErrorCode(errorCode);
            entity.setCreatedAt(OffsetDateTime.now());
            auditLogRepository.save(entity);
        }

        if (meterRegistry != null) {
            meterRegistry.counter("mpfm.security.events", "action", action, "result", result).increment();
        }
    }

    /** 管理操作审计事件模型，承载动作、目标、结果与错误码上下文。 */
    public record ManagementAuditEvent(String action, String target, String result, String errorCode) { }
}





