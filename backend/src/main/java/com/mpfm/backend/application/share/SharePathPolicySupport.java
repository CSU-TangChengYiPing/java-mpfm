package com.mpfm.backend.application.share;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.share.ShareRolePolicyEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SharePathPolicySupport {
    private static final String PATH_PERSONAL = "./personal";
    private static final String PATH_PERSONAL_PREFIX = "./personal/";
    private static final String PATH_SHARED = "./shared";
    private static final String PATH_SHARED_PREFIX = "./shared/";

    private final ShareRepositories repositories;

    SharePathPolicySupport(ShareRepositories repositories) {
        this.repositories = repositories;
    }

    String normalizePolicyPath(String path) {
        if (path == null || path.isBlank() || ".".equals(path)) {
            return "/";
        }
        String value = path.trim().replace('\\', '/');
        String normalized = normalizeByScopePrefix(value);
        if (normalized != null) {
            return normalized;
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        return value;
    }

    void upsertSinglePolicy(UUID roleId, String pathPattern, boolean canVisible, boolean canRead, boolean canWrite) {
        repositories.shareRolePolicyRepository.deleteByRoleId(roleId);
        ShareRolePolicyEntity policy = new ShareRolePolicyEntity();
        policy.setId(UUID.randomUUID());
        policy.setRoleId(roleId);
        policy.setPathPattern(normalizePolicyPath(pathPattern));
        policy.setCanVisible(canVisible);
        policy.setCanRead(canRead);
        policy.setCanWrite(canWrite);
        policy.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        policy.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repositories.shareRolePolicyRepository.save(policy);
    }

    private String normalizeByScopePrefix(String value) {
        if (PATH_PERSONAL.equals(value) || PATH_SHARED.equals(value)) {
            return "/";
        }
        String personal = normalizePrefixedPath(value, PATH_PERSONAL_PREFIX);
        if (personal != null) {
            return personal;
        }
        return normalizePrefixedPath(value, PATH_SHARED_PREFIX);
    }

    private String normalizePrefixedPath(String value, String prefix) {
        if (!value.startsWith(prefix)) {
            return null;
        }
        String remain = value.substring(prefix.length());
        if (remain.isBlank()) {
            return "/";
        }
        if (!remain.startsWith("/")) {
            return "/" + remain;
        }
        return remain;
    }

    void validateReadableWritable(ShareApplicationService.PathPolicyCommand policy) {
        if (policy.canWrite() && !policy.canRead()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "write requires read");
        }
    }
}


