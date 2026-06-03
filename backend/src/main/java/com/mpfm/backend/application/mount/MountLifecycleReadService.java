package com.mpfm.backend.application.mount;

import com.mpfm.backend.application.user.PlatformRole;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.repository.MountRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.v5.SharedMountAccessV5Repository;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 挂载读取协作器，负责权限判定与展示模型转换。 */
@Component
class MountLifecycleReadService {
    static final String STATE_ENABLED = "enabled";
    static final String STATE_SOFT_DELETED = "soft_deleted";

    private final MountRepository mountRepository;
    private final UserRepository userRepository;
    private final SharedMountAccessV5Repository sharedMountAccessV5Repository;

    MountLifecycleReadService(MountRepository mountRepository,
                             UserRepository userRepository,
                             SharedMountAccessV5Repository sharedMountAccessV5Repository) {
        this.mountRepository = mountRepository;
        this.userRepository = userRepository;
        this.sharedMountAccessV5Repository = sharedMountAccessV5Repository;
    }

    List<MountApplicationService.MountResult> listMyMounts(String username) {
        UserEntity user = loadUser(username);
        List<MountEntity> mounts = user.getPlatformRole() == PlatformRole.USER
                ? mountRepository.findByOwnerIdAndStateNot(user.getId(), STATE_SOFT_DELETED)
                : mountRepository.findByStateNot(STATE_SOFT_DELETED);
        if (user.getPlatformRole() != PlatformRole.USER) {
            return mounts.stream().map(mount -> toResult(mount, user)).toList();
        }
        Map<UUID, MountEntity> merged = new LinkedHashMap<>();
        for (MountEntity mount : mounts) {
            merged.put(mount.getId(), mount);
        }
        sharedMountAccessV5Repository.findByUserIdAndActiveTrue(user.getId()).forEach(access ->
                mountRepository.findById(access.getMountId()).ifPresent(mount -> {
                    if (!STATE_SOFT_DELETED.equals(mount.getState()) && mount.isSharedEnabled()) {
                        merged.putIfAbsent(mount.getId(), mount);
                    }
                }));
        return merged.values().stream().map(mount -> toResult(mount, user)).toList();
    }

    MountApplicationService.MountResult getMyMount(String username, UUID mountId) {
        UserEntity user = loadUser(username);
        return toResult(requireOwnedOrAdminMount(username, mountId), user);
    }

    MountEntity requireOwnedEnabledMount(String username, UUID mountId) {
        MountEntity mount = requireOwnedOrAdminMount(username, mountId);
        if (!STATE_ENABLED.equals(mount.getState())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "mount disabled");
        }
        return mount;
    }

    MountEntity requireOwnedOrAdminMount(String username, UUID mountId) {
        UserEntity user = loadUser(username);
        MountEntity mount = mountRepository.findById(mountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
        if (user.getPlatformRole() == PlatformRole.USER && !mount.getOwnerId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "mount access denied");
        }
        return mount;
    }

    UserEntity loadUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID, "user not found"));
    }

    MountApplicationService.MountResult toResult(MountEntity mount, UserEntity currentUser) {
        String virtualPath = mount.getVirtualPath() == null || mount.getVirtualPath().isBlank()
                ? "./personal/" + mount.getName()
                : mount.getVirtualPath();
        UserEntity owner = userRepository.findById(mount.getOwnerId()).orElse(null);
        String ownerUser = owner == null ? "" : owner.getUsername();
        String ownerDisplayName = owner == null
                ? ""
                : (owner.getDisplayName() == null || owner.getDisplayName().isBlank() ? owner.getUsername() : owner.getDisplayName());
        boolean canManage = currentUser.getPlatformRole() != PlatformRole.USER || mount.getOwnerId().equals(currentUser.getId());
        return new MountApplicationService.MountResult(
                mount.getId(), mount.getType(), mount.getName(), mount.getPhysicalRoot(),
                virtualPath, mount.getState(), mount.isSharedEnabled(), ownerUser, ownerDisplayName, canManage);
    }
}
