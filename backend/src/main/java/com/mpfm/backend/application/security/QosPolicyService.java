package com.mpfm.backend.application.security;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.QosPolicyEntity;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.entity.UserTransferGovernanceEntity;
import com.mpfm.backend.infrastructure.persistence.repository.QosPolicyRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserTransferGovernanceRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * QoS 策略服务（极简版）：仅保留“策略名 + 上下行速率”与用户自定义限速。
 */
@Service
public class QosPolicyService {
    private static final String POLICY_DEFAULT = "default";
    private static final String POLICY_USER = "user";
    private static final String POLICY_ADMIN = "admin";
    private static final String POLICY_ROOT = "root";

    private final QosPolicyRepository qosPolicyRepository;
    private final UserRepository userRepository;
    private final UserTransferGovernanceRepository governanceRepository;

    public QosPolicyService(QosPolicyRepository qosPolicyRepository,
                            UserRepository userRepository,
                            UserTransferGovernanceRepository governanceRepository) {
        this.qosPolicyRepository = qosPolicyRepository;
        this.userRepository = userRepository;
        this.governanceRepository = governanceRepository;
    }

    @Transactional
    public void bootstrapDefaultsIfMissing() {
        seedIfMissing(POLICY_DEFAULT, "默认策略", 8L * 1024 * 1024, 8L * 1024 * 1024);
        seedIfMissing(POLICY_USER, "用户策略", 8L * 1024 * 1024, 8L * 1024 * 1024);
        seedIfMissing(POLICY_ADMIN, "管理员策略", 24L * 1024 * 1024, 24L * 1024 * 1024);
        seedIfMissing(POLICY_ROOT, "超级管理员策略", 64L * 1024 * 1024, 64L * 1024 * 1024);
    }

    private void seedIfMissing(String id, String name, long up, long down) {
        if (qosPolicyRepository.existsById(id)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        QosPolicyEntity entity = new QosPolicyEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setMaxUploadBps(up);
        entity.setMaxDownloadBps(down);
        entity.setMaxConcurrentUploadTasks(1);
        entity.setMaxConcurrentDownloadTasks(1);
        entity.setEnabled(true);
        entity.setUpdatedBy("system");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        qosPolicyRepository.save(entity);
    }

    public List<QosPolicy> listPolicies() {
        bootstrapDefaultsIfMissing();
        return qosPolicyRepository.findAll().stream()
                .map(this::toModel)
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
    }

    @Transactional
    public QosPolicy createPolicy(CreatePolicyCommand command) {
        validateNameAndRate(command.name(), command.maxUploadBps(), command.maxDownloadBps());
        bootstrapDefaultsIfMissing();
        String base = slug(command.name());
        String id = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        QosPolicyEntity entity = new QosPolicyEntity();
        entity.setId(id);
        entity.setName(command.name().trim());
        entity.setMaxUploadBps(command.maxUploadBps());
        entity.setMaxDownloadBps(command.maxDownloadBps());
        entity.setMaxConcurrentUploadTasks(1);
        entity.setMaxConcurrentDownloadTasks(1);
        entity.setEnabled(true);
        entity.setUpdatedBy(command.operator());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return toModel(qosPolicyRepository.save(entity));
    }

    @Transactional
    public QosPolicy updatePolicy(String policyId, UpdatePolicyCommand command) {
        validateNameAndRate(command.name(), command.maxUploadBps(), command.maxDownloadBps());
        bootstrapDefaultsIfMissing();
        QosPolicyEntity entity = qosPolicyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "qos policy not found"));
        entity.setName(command.name().trim());
        entity.setMaxUploadBps(command.maxUploadBps());
        entity.setMaxDownloadBps(command.maxDownloadBps());
        entity.setUpdatedBy(command.operator());
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return toModel(qosPolicyRepository.save(entity));
    }

    @Transactional
    public int batchDeletePolicies(List<String> policyIds) {
        if (policyIds == null || policyIds.isEmpty()) {
            return 0;
        }
        Set<String> reserved = Set.of(POLICY_DEFAULT, POLICY_USER, POLICY_ADMIN, POLICY_ROOT);
        int removed = 0;
        for (String id : policyIds) {
            if (id == null || id.isBlank() || reserved.contains(id.trim())) {
                continue;
            }
            if (qosPolicyRepository.existsById(id.trim())) {
                qosPolicyRepository.deleteById(id.trim());
                removed += 1;
            }
        }
        return removed;
    }

    @Transactional
    public void bindUserPolicy(String username, String policyId, String operator) {
        bootstrapDefaultsIfMissing();
        if (!qosPolicyRepository.existsById(policyId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "qos policy not found");
        }
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "user not found"));
        user.setQosProfile(policyId);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);
        UserTransferGovernanceEntity governance = governanceRepository.findById(username).orElseGet(() -> {
            UserTransferGovernanceEntity entity = new UserTransferGovernanceEntity();
            entity.setUsername(username);
            entity.setUploadPaused(false);
            entity.setDownloadPaused(false);
            return entity;
        });
        governance.setUpdatedBy(operator);
        governance.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        governanceRepository.save(governance);
    }

    @Transactional
    public UserCustomLimit updateUserCustomLimit(String username, long maxUploadBps, long maxDownloadBps, String operator) {
        if (maxUploadBps <= 0 || maxDownloadBps <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "qos rate must be positive");
        }
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "user not found"));
        user.setCustomUploadBps(maxUploadBps);
        user.setCustomDownloadBps(maxDownloadBps);
        user.setQosCustomEnabled(true);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);
        return new UserCustomLimit(username, maxUploadBps, maxDownloadBps, true);
    }

    public UserCustomLimit getUserCustomLimit(String username) {
        UserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !user.isQosCustomEnabled() || user.getCustomUploadBps() <= 0 || user.getCustomDownloadBps() <= 0) {
            return new UserCustomLimit(username, 0L, 0L, false);
        }
        return new UserCustomLimit(username, user.getCustomUploadBps(), user.getCustomDownloadBps(), true);
    }

    public QosPolicy effectivePolicy(String username) {
        bootstrapDefaultsIfMissing();
        UserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return requirePolicy(POLICY_DEFAULT);
        }
        if (user.isQosCustomEnabled() && user.getCustomUploadBps() > 0 && user.getCustomDownloadBps() > 0) {
            return new QosPolicy("custom:" + username, "用户自定义", user.getCustomUploadBps(), user.getCustomDownloadBps(), 1, 1, true, "system");
        }
        String policyId = user.getQosProfile();
        if (policyId != null && !policyId.isBlank() && qosPolicyRepository.existsById(policyId)) {
            return requirePolicy(policyId);
        }
        String fallback = switch (safeLower(user.getPlatformRole() == null ? null : user.getPlatformRole().name())) {
            case "root" -> POLICY_ROOT;
            case "admin" -> POLICY_ADMIN;
            default -> POLICY_USER;
        };
        return requirePolicy(fallback);
    }

    public QosPolicy effectivePolicy(String username, UUID mountId, String protocol) {
        return effectivePolicy(username);
    }

    private QosPolicy requirePolicy(String policyId) {
        Optional<QosPolicyEntity> found = qosPolicyRepository.findById(policyId);
        if (found.isPresent()) {
            return toModel(found.get());
        }
        return qosPolicyRepository.findById(POLICY_DEFAULT)
                .map(this::toModel)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "default qos policy missing"));
    }

    private QosPolicy toModel(QosPolicyEntity entity) {
        return new QosPolicy(
                entity.getId(),
                entity.getName(),
                entity.getMaxUploadBps(),
                entity.getMaxDownloadBps(),
                1,
                1,
                true,
                entity.getUpdatedBy()
        );
    }

    private void validateNameAndRate(String name, long up, long down) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "qos name required");
        }
        if (up <= 0 || down <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "qos rate must be positive");
        }
    }

    private String safeLower(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT);
    }

    private String slug(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        String trimmed = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return trimmed.isBlank() ? "policy" : trimmed;
    }

    public record QosPolicy(String id,
                            String name,
                            long maxUploadBps,
                            long maxDownloadBps,
                            int maxConcurrentUploadTasks,
                            int maxConcurrentDownloadTasks,
                            boolean enabled,
                            String operator) { }

    public record CreatePolicyCommand(String name, long maxUploadBps, long maxDownloadBps, String operator) { }

    public record UpdatePolicyCommand(String name, long maxUploadBps, long maxDownloadBps, String operator) { }

    public record UserCustomLimit(String username, long maxUploadBps, long maxDownloadBps, boolean customized) { }
}
