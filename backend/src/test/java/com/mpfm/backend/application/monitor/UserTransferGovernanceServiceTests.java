package com.mpfm.backend.application.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.mpfm.backend.application.security.QosPolicyService;
import com.mpfm.backend.application.task.AsyncTaskService;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.infrastructure.persistence.entity.AsyncTaskEntity;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.repository.AsyncTaskRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTransferGovernanceServiceTests {
    @Mock
    private UserRepository userRepository;
    @Mock
    private AsyncTaskRepository asyncTaskRepository;
    @Mock
    private AsyncTaskService asyncTaskService;
    @Mock
    private QosPolicyService qosPolicyService;
    @InjectMocks
    private UserTransferGovernanceService userTransferGovernanceService;

    @Test
    void shouldBlockUploadWhenPaused() {
        UserEntity entity = new UserEntity();
        entity.setUsername("alice");
        entity.setUploadPaused(true);
        entity.setDownloadPaused(false);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(entity));
        assertThatThrownBy(() -> userTransferGovernanceService.ensureUploadAllowed("alice"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldKickOnlyUploadTasks() {
        AsyncTaskEntity uploadTask = new AsyncTaskEntity();
        uploadTask.setId(UUID.randomUUID());
        uploadTask.setAction("upload");
        uploadTask.setOperator("alice");
        uploadTask.setStatus("RUNNING");
        AsyncTaskEntity downloadTask = new AsyncTaskEntity();
        downloadTask.setId(UUID.randomUUID());
        downloadTask.setAction("download");
        downloadTask.setOperator("alice");
        downloadTask.setStatus("RUNNING");
        given(asyncTaskRepository.findByOperatorAndStatusIn("alice", List.of("PENDING", "RUNNING")))
                .willReturn(List.of(uploadTask, downloadTask));

        int kicked = userTransferGovernanceService.kickActiveTasks("alice", "upload");

        assertThat(kicked).isEqualTo(1);
    }
}

