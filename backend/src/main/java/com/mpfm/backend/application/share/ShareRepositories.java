package com.mpfm.backend.application.share;

import com.mpfm.backend.infrastructure.persistence.repository.MountRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import com.mpfm.backend.infrastructure.persistence.repository.AuditLogRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.ShareLinkRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.ShareRolePolicyRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.ShareRoleRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.v5.ShareRoleTemplateV5Repository;
import com.mpfm.backend.infrastructure.persistence.repository.share.SharedMountAccessRepository;
import org.springframework.stereotype.Component;

@Component
class ShareRepositories {
    final UserRepository userRepository;
    final MountRepository mountRepository;
    final ShareRoleRepository shareRoleRepository;
    final ShareRolePolicyRepository shareRolePolicyRepository;
    final ShareLinkRepository shareLinkRepository;
    final SharedMountAccessRepository sharedMountAccessRepository;
    final ShareRoleTemplateV5Repository shareRoleTemplateV5Repository;
    final AuditLogRepository auditLogRepository;

    ShareRepositories(UserRepository userRepository,
                      MountRepository mountRepository,
                      ShareRoleRepository shareRoleRepository,
                      ShareRolePolicyRepository shareRolePolicyRepository,
                      ShareLinkRepository shareLinkRepository,
                      SharedMountAccessRepository sharedMountAccessRepository,
                      ShareRoleTemplateV5Repository shareRoleTemplateV5Repository,
                      AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.mountRepository = mountRepository;
        this.shareRoleRepository = shareRoleRepository;
        this.shareRolePolicyRepository = shareRolePolicyRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.sharedMountAccessRepository = sharedMountAccessRepository;
        this.shareRoleTemplateV5Repository = shareRoleTemplateV5Repository;
        this.auditLogRepository = auditLogRepository;
    }
}

