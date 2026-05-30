package com.mpfm.backend.application.security;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 安全治理服务，负责安全开关、证书状态与告警列表维护。
 */
@Service
public class SecurityGovernanceService {
    private static final String DEFAULT_POLICY_ID = "default";
    private final Map<String, String> policies = new ConcurrentHashMap<>();
    private final Map<String, String> certificates = new ConcurrentHashMap<>();
    private final List<SecurityModels.AlertItem> alerts = new ArrayList<>();

    public SecurityGovernanceService() {
        policies.put(DEFAULT_POLICY_ID, "enabled");
        certificates.put(DEFAULT_POLICY_ID, "active");
        alerts.add(new SecurityModels.AlertItem(UUID.randomUUID().toString(), "brute_force_login", "open", OffsetDateTime.now(ZoneOffset.UTC).toString()));
    }

    public String updateSecurityPolicy(String policyId, String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid security policy");
        }
        policies.put(policyId, value);
        return value;
    }

    public List<SecurityModels.NamedValue> listPolicies() {
        return policies.entrySet().stream().map(e -> new SecurityModels.NamedValue(e.getKey(), e.getValue())).toList();
    }

    public String updateCertificate(String certId, String state) {
        if (state == null || state.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid certificate state");
        }
        certificates.put(certId, state);
        return state;
    }

    public List<SecurityModels.NamedValue> listCertificates() {
        return certificates.entrySet().stream().map(e -> new SecurityModels.NamedValue(e.getKey(), e.getValue())).toList();
    }

    public String rotateCertificate(String certId) {
        certificates.put(certId, "rotated");
        return "rotated";
    }

    public List<SecurityModels.AlertItem> listAlerts() {
        return List.copyOf(alerts);
    }

    public SecurityModels.AlertItem ackAlert(String alertId) {
        return alerts.stream().filter(i -> i.alertId().equals(alertId)).findFirst().map(item -> {
            SecurityModels.AlertItem updated = new SecurityModels.AlertItem(item.alertId(), item.type(), "ack", item.createdAt());
            alerts.remove(item);
            alerts.add(updated);
            return updated;
        }).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "alert not found"));
    }
}

