package com.mpfm.backend.application.monitor;

import com.mpfm.backend.application.security.QosPolicyService;
import com.mpfm.backend.application.task.AsyncTaskService;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.AsyncTaskEntity;
import com.mpfm.backend.infrastructure.persistence.repository.AsyncTaskRepository;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户传输治理服务，提供暂停上传/下载、踢出活跃任务与限速策略绑定。
 */
@Service
public class UserTransferGovernanceService {
    private static final List<String> ACTIVE_STATUSES = List.of("PENDING", "RUNNING");
    private static final String DIRECTION_UPLOAD = "upload";
    private static final String DIRECTION_DOWNLOAD = "download";
    private static final String DIRECTION_ALL = "all";
    private final UserRepository userRepository;
    private final AsyncTaskRepository asyncTaskRepository;
    private final AsyncTaskService asyncTaskService;
    private final QosPolicyService qosPolicyService;

    public UserTransferGovernanceService(UserRepository userRepository,
                                         AsyncTaskRepository asyncTaskRepository,
                                         AsyncTaskService asyncTaskService,
                                         QosPolicyService qosPolicyService) {
        this.userRepository = userRepository;
        this.asyncTaskRepository = asyncTaskRepository;
        this.asyncTaskService = asyncTaskService;
        this.qosPolicyService = qosPolicyService;
    }

    public GovernanceState stateOf(String username) {
        UserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return new GovernanceState(username, false, false);
        }
        return new GovernanceState(username, user.isUploadPaused(), user.isDownloadPaused());
    }

    @Transactional
    public GovernanceState pauseUpload(String username, boolean paused, String operator) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "user not found"));
        user.setUploadPaused(paused);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);
        return new GovernanceState(username, user.isUploadPaused(), user.isDownloadPaused());
    }

    @Transactional
    public GovernanceState pauseDownload(String username, boolean paused, String operator) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "user not found"));
        user.setDownloadPaused(paused);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);
        return new GovernanceState(username, user.isUploadPaused(), user.isDownloadPaused());
    }

    @Transactional
    public int kickActiveTasks(String username, String direction) {
        String normalized = direction == null ? DIRECTION_ALL : direction.trim().toLowerCase(Locale.ROOT);
        List<AsyncTaskEntity> active = asyncTaskRepository.findByOperatorAndStatusIn(username, ACTIVE_STATUSES);
        List<AsyncTaskEntity> selected = active.stream()
                .filter(task -> matchDirection(task.getAction(), normalized))
                .toList();
        int kicked = 0;
        for (AsyncTaskEntity task : selected) {
            try {
                asyncTaskService.cancelForGovernance(task.getId());
                kicked += 1;
            } catch (BusinessException ignored) {
                // 任务状态变化属于并发竞争，不阻断治理动作整体执行。
            }
        }
        return kicked;
    }

    @Transactional
    public void bindQosLimit(String username, String policyId, String operator) {
        qosPolicyService.bindUserPolicy(username, policyId, operator);
    }

    public void ensureUploadAllowed(String username) {
        UserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user != null && user.isUploadPaused()) {
            throw new BusinessException(ErrorCode.CAPABILITY_RESTRICTED, "upload paused by governance");
        }
    }

    public void ensureDownloadAllowed(String username) {
        UserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user != null && user.isDownloadPaused()) {
            throw new BusinessException(ErrorCode.CAPABILITY_RESTRICTED, "download paused by governance");
        }
    }

    private boolean matchDirection(String action, String direction) {
        String normalized = action == null ? "" : action.toLowerCase(Locale.ROOT);
        if (DIRECTION_UPLOAD.equals(direction)) {
            return normalized.contains("upload") || normalized.contains("put_url");
        }
        if (DIRECTION_DOWNLOAD.equals(direction)) {
            return normalized.contains("download");
        }
        if (DIRECTION_ALL.equals(direction)) {
            return true;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "direction must be upload/download/all");
    }

    /** 用户治理状态。 */
    public record GovernanceState(String username, boolean uploadPaused, boolean downloadPaused) { }
}
