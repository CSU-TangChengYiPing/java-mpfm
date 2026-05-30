package com.mpfm.backend.adapter.api.admin;

import com.mpfm.backend.application.security.SecurityModels;
import com.mpfm.backend.application.security.SecurityGovernanceService;
import com.mpfm.backend.common.audit.AuditAction;
import com.mpfm.backend.common.audit.AuditEvent;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端安全策略控制器，负责限流策略、安全策略、证书与告警状态维护接口。
 */
@RestController
@RequestMapping("/api/v1/admin/security")
@PreAuthorize("hasRole('ADMIN') or hasRole('ROOT')")
public class AdminSecurityController {
    private final SecurityGovernanceService securityGovernanceService;

    public AdminSecurityController(SecurityGovernanceService securityGovernanceService) {
        this.securityGovernanceService = securityGovernanceService;
    }

    @PutMapping("/policies/{policyId}")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "security_policy_update", target = "security_policy", riskLevel = "high")
    public SecurityModels.NamedValue updatePolicy(@PathVariable String policyId, @RequestBody ValueRequest request) {
        return new SecurityModels.NamedValue(policyId, securityGovernanceService.updateSecurityPolicy(policyId, request.value()));
    }

    @GetMapping("/policies")
    public List<SecurityModels.NamedValue> listPolicies() {
        return securityGovernanceService.listPolicies();
    }

    @PutMapping("/certificates/{certId}")
    public SecurityModels.NamedValue updateCert(@PathVariable String certId, @RequestBody ValueRequest request) {
        return new SecurityModels.NamedValue(certId, securityGovernanceService.updateCertificate(certId, request.value()));
    }

    @GetMapping("/certificates")
    public List<SecurityModels.NamedValue> listCerts() {
        return securityGovernanceService.listCertificates();
    }

    @PostMapping("/certificates/{certId}/rotate")
    public SecurityModels.NamedValue rotateCert(@PathVariable String certId) {
        return new SecurityModels.NamedValue(certId, securityGovernanceService.rotateCertificate(certId));
    }

    @GetMapping("/alerts")
    public List<SecurityModels.AlertItem> alerts() {
        return securityGovernanceService.listAlerts();
    }

    @PostMapping("/alerts/{alertId}/ack")
    public SecurityModels.AlertItem ack(@PathVariable String alertId) {
        return securityGovernanceService.ackAlert(alertId);
    }

    /** 通用值更新请求，`value` 为目标策略/证书的新配置值。 */
    public record ValueRequest(@NotBlank String value) { }
}




