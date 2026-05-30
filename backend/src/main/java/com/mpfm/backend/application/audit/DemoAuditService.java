package com.mpfm.backend.application.audit;

import com.mpfm.backend.common.audit.AuditAction;
import com.mpfm.backend.common.audit.AuditEvent;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * 演示审计服务，负责写入示例审计事件并提供查询辅助能力。
 */
@Service
public class DemoAuditService {

    @PreAuthorize("permitAll()")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS)
    public String success() {
        return "ok";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @AuditAction(event = AuditEvent.DEMO_FAILURE)
    public void failure() {
        throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "demo transition invalid");
    }
}




