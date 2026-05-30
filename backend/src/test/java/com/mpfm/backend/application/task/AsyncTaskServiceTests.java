package com.mpfm.backend.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mpfm.backend.infrastructure.persistence.entity.AsyncTaskEntity;
import com.mpfm.backend.infrastructure.persistence.repository.AsyncTaskRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AsyncTaskServiceTests {

    private final AsyncTaskService service = createService();

    @Test
    void shouldCreateTaskWithPendingStatus() {
        AsyncTask task = service.create("file.delete", "u-1", "obj-1");
        assertThat(task.status()).isEqualTo(AsyncTaskStatus.PENDING);
    }

    @Test
    void shouldCancelOnlyPendingOrRunningTask() {
        AsyncTask task = service.create("file.delete", "u-1", "obj-1");
        service.markRunning(task.id());
        AsyncTask canceled = service.cancel(task.id(), "u-1");
        assertThat(canceled.status()).isEqualTo(AsyncTaskStatus.CANCELED);
    }

    @Test
    void shouldPersistPayloadJsonAsTaskPrivateState() {
        TransferTaskWriteScope.enter();
        AsyncTask task;
        try {
            task = service.create("batch_upload", "u-1", "obj-1");
        } finally {
            TransferTaskWriteScope.exit();
        }
        AsyncTask updated = service.updatePayloadJson(task.id(), "{\"uploadSessionId\":\"s1\",\"completedParts\":[1]}");
        assertThat(updated.payloadJson()).contains("uploadSessionId");
        AsyncTask loaded = service.get(task.id());
        assertThat(loaded.payloadJson()).contains("completedParts");
    }

    @Test
    void shouldRejectTransferTaskWriteOutsideRuntimeScope() {
        TransferTaskWriteScope.enter();
        AsyncTask task;
        try {
            task = service.create("batch_upload", "u-1", "obj-1");
            service.markRunning(task.id());
        } finally {
            TransferTaskWriteScope.exit();
        }
        assertThatThrownBy(() -> service.markSuccess(task.id()))
                .hasMessageContaining("runtime");
    }

    @Test
    void shouldRejectTransferTerminalWriteOutsideHandlerScope() {
        AsyncTask task;
        TransferTaskWriteScope.enter();
        try {
            task = service.create("batch_upload", "u-1", "obj-1");
            service.markRunning(task.id());
        } finally {
            TransferTaskWriteScope.exit();
        }
        TransferTaskWriteScope.enter();
        try {
            assertThatThrownBy(() -> service.markSuccess(task.id()))
                    .hasMessageContaining("runtime handler");
        } finally {
            TransferTaskWriteScope.exit();
        }
    }

    private AsyncTaskService createService() {
        AsyncTaskRepository repository = mock(AsyncTaskRepository.class);
        Map<UUID, AsyncTaskEntity> store = new LinkedHashMap<>();
        AsyncTaskPersistenceMapper mapper = new AsyncTaskPersistenceMapper();

        when(repository.save(any(AsyncTaskEntity.class))).thenAnswer(invocation -> {
            AsyncTaskEntity entity = invocation.getArgument(0);
            store.put(entity.getId(), entity);
            return entity;
        });
        when(repository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.ofNullable(store.get(id));
        });
        when(repository.findByOperatorOrderByUpdatedAtDesc(anyString())).thenAnswer(invocation -> {
            String operator = invocation.getArgument(0);
            return store.values().stream()
                    .filter(entity -> operator.equals(entity.getOperator()))
                    .sorted(Comparator.comparing(AsyncTaskEntity::getUpdatedAt).reversed())
                    .toList();
        });
        when(repository.deleteByOperatorAndStatusIn(anyString(), anyList())).thenAnswer(invocation -> {
            String operator = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            List<String> statuses = invocation.getArgument(1);
            List<UUID> deleting = store.values().stream()
                    .filter(entity -> operator.equals(entity.getOperator()) && statuses.contains(entity.getStatus()))
                    .map(AsyncTaskEntity::getId)
                    .toList();
            deleting.forEach(store::remove);
            return (long) deleting.size();
        });
        doAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            store.remove(id);
            return null;
        }).when(repository).deleteById(any(UUID.class));

        TransferTaskStreamService streamService = mock(TransferTaskStreamService.class);
        return new AsyncTaskService(repository, mapper, streamService);
    }
}

