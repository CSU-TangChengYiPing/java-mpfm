package com.mpfm.backend.common.audit;

import com.mpfm.backend.infrastructure.persistence.entity.AuditLogEntity;
import com.mpfm.backend.infrastructure.persistence.repository.AuditLogRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * AuditAspect 审计切面，负责拦截审计注解并记录审计事件。
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");
    private final AuditLogRepository auditLogRepository;
    private final SecurityEventLogger securityEventLogger;

    public AuditAspect(@Nullable AuditLogRepository auditLogRepository,
                       SecurityEventLogger securityEventLogger) {
        this.auditLogRepository = auditLogRepository;
        this.securityEventLogger = securityEventLogger;
    }

    @Around("@annotation(auditAction)")
    public Object around(ProceedingJoinPoint joinPoint, AuditAction auditAction) throws Throwable {
        long start = System.currentTimeMillis();
        String requestId = MDC.get("requestId");
        String traceId = MDC.get("traceId");
        String actor = resolveActor();
        String method = joinPoint.getSignature().toShortString();
        String action = auditAction.action().isBlank() ? auditAction.event().getAction() : auditAction.action();
        String target = "unknown".equals(auditAction.target()) ? auditAction.event().getTarget() : auditAction.target();
        String riskLevel = "normal".equals(auditAction.riskLevel()) ? auditAction.event().getRiskLevel() : auditAction.riskLevel();

        try {
            Object result = joinPoint.proceed();
            if (AUDIT_LOG.isInfoEnabled()) {
                AUDIT_LOG.info(
                        "action={} target={} risk={} actor={} method={} result=success costMs={} requestId={} traceId={}",
                        action,
                        target,
                        riskLevel,
                        actor,
                        method,
                        System.currentTimeMillis() - start,
                        requestId,
                        traceId);
            }
            persist(actor, action, target, "success", null);
            securityEventLogger.managementOperation(
                    new SecurityEventLogger.ManagementAuditEvent(action, target, "success", null));
            return result;
        } catch (Exception ex) {
            if (AUDIT_LOG.isWarnEnabled()) {
                AUDIT_LOG.warn(
                        "action={} target={} risk={} actor={} method={} result=failure error={} costMs={} requestId={} traceId={}",
                        action,
                        target,
                        riskLevel,
                        actor,
                        method,
                        ex.getClass().getSimpleName(),
                        System.currentTimeMillis() - start,
                        requestId,
                        traceId);
            }
            persist(actor, action, target, "failure", ex.getClass().getSimpleName());
            securityEventLogger.managementOperation(
                    new SecurityEventLogger.ManagementAuditEvent(action, target, "failure", ex.getClass().getSimpleName()));
            throw ex;
        } catch (Error ex) {
            throw ex;
        }
    }

    private String resolveActor() {
        String fromMdc = MDC.get("actor");
        return fromMdc == null || fromMdc.isBlank() ? "system" : fromMdc;
    }

    private void persist(String actor, String action, String target, String result, String errorCode) {
        if (auditLogRepository == null) {
            return;
        }
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setOperator(actor);
        entity.setAction(action);
        entity.setTarget(target);
        entity.setResult(result);
        entity.setErrorCode(errorCode);
        entity.setCreatedAt(OffsetDateTime.now());
        auditLogRepository.save(entity);
    }
}





