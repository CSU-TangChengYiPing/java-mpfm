package com.mpfm.backend.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mpfm.backend.infrastructure.persistence.entity.AsyncTaskEntity;
import com.mpfm.backend.infrastructure.persistence.repository.AsyncTaskRepository;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TransferTaskRuntimeTests {

    @Test
    void shouldRunTaskToSuccess() {
        RuntimeFixture fixture = createFixture();
        fixture.registry.register(new TransferTaskTypeConfig("runtime_test_success", 1, 0, context -> {
            context.ensureNotCanceled();
            Thread.sleep(30L);
        }));
        AsyncTask task = fixture.runtime.submit("runtime_test_success", "u-1", "obj-1");
        AsyncTask done = fixture.await(task.id(), Set.of(AsyncTaskStatus.SUCCESS), Duration.ofSeconds(2));
        assertThat(done.status()).isEqualTo(AsyncTaskStatus.SUCCESS);
    }

    @Test
    void shouldRetryAfterFailureThenSucceed() {
        RuntimeFixture fixture = createFixture();
        AtomicInteger counter = new AtomicInteger(0);
        fixture.registry.register(new TransferTaskTypeConfig("runtime_test_retry", 1, 2, context -> {
            if (counter.incrementAndGet() == 1) {
                throw new IllegalStateException("first failure");
            }
        }));
        AsyncTask task = fixture.runtime.submit("runtime_test_retry", "u-1", "obj-1");
        AsyncTask done = fixture.await(task.id(), Set.of(AsyncTaskStatus.SUCCESS), Duration.ofSeconds(4));
        assertThat(done.status()).isEqualTo(AsyncTaskStatus.SUCCESS);
        assertThat(counter.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldPauseAndResumeTask() {
        RuntimeFixture fixture = createFixture();
        fixture.registry.register(new TransferTaskTypeConfig("runtime_test_pause", 1, 0, context -> {
            long deadline = System.currentTimeMillis() + 1200L;
            while (System.currentTimeMillis() < deadline) {
                context.ensureNotCanceled();
                if (context.isPaused()) {
                    return;
                }
                Thread.sleep(40L);
            }
        }));
        AsyncTask task = fixture.runtime.submit("runtime_test_pause", "u-1", "obj-1");
        fixture.await(task.id(), Set.of(AsyncTaskStatus.RUNNING), Duration.ofSeconds(2));
        AsyncTask paused = fixture.runtime.pause(task.id(), "u-1");
        assertThat(paused.status()).isEqualTo(AsyncTaskStatus.PAUSED);
        AsyncTask resumed = fixture.runtime.resume(task.id(), "u-1");
        assertThat(resumed.status()).isIn(AsyncTaskStatus.RESUMING, AsyncTaskStatus.RUNNING, AsyncTaskStatus.SUCCESS);
        AsyncTask done = fixture.await(task.id(), Set.of(AsyncTaskStatus.SUCCESS), Duration.ofSeconds(4));
        assertThat(done.status()).isEqualTo(AsyncTaskStatus.SUCCESS);
    }

    @Test
    void shouldCancelRunningTask() {
        RuntimeFixture fixture = createFixture();
        fixture.registry.register(new TransferTaskTypeConfig("runtime_test_cancel", 1, 0, context -> {
            long deadline = System.currentTimeMillis() + 3000L;
            while (System.currentTimeMillis() < deadline) {
                context.ensureNotCanceled();
                Thread.sleep(30L);
            }
        }));
        AsyncTask task = fixture.runtime.submit("runtime_test_cancel", "u-1", "obj-1");
        fixture.await(task.id(), Set.of(AsyncTaskStatus.RUNNING), Duration.ofSeconds(2));
        AsyncTask canceled = fixture.runtime.cancel(task.id(), "u-1");
        assertThat(canceled.status()).isEqualTo(AsyncTaskStatus.CANCELED);
    }

    private RuntimeFixture createFixture() {
        AsyncTaskRepository repository = mock(AsyncTaskRepository.class);
        Map<UUID, AsyncTaskEntity> store = new LinkedHashMap<>();
        AsyncTaskPersistenceMapper mapper = new AsyncTaskPersistenceMapper();

        when(repository.save(any(AsyncTaskEntity.class))).thenAnswer(invocation -> {
            AsyncTaskEntity entity = invocation.getArgument(0);
            store.put(entity.getId(), entity);
            return entity;
        });
        when(repository.findById(any(UUID.class))).thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
        when(repository.findByOperatorOrderByUpdatedAtDesc(anyString())).thenAnswer(invocation -> store.values().stream()
                .filter(entity -> invocation.getArgument(0).equals(entity.getOperator()))
                .sorted(Comparator.comparing(AsyncTaskEntity::getUpdatedAt).reversed())
                .toList());
        when(repository.findByStatusIn(anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> statuses = invocation.getArgument(0);
            return store.values().stream().filter(entity -> statuses.contains(entity.getStatus())).toList();
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
            store.remove(invocation.getArgument(0));
            return null;
        }).when(repository).deleteById(any(UUID.class));

        TransferTaskStreamService streamService = mock(TransferTaskStreamService.class);
        AsyncTaskService service = new AsyncTaskService(repository, mapper, streamService);
        TransferTaskRegistry registry = new TransferTaskRegistry();
        TransferRetryPolicy retryPolicy = new TransferRetryPolicy();
        TransferTaskRuntime runtime = new TransferTaskRuntime(service, registry, retryPolicy, new TransferTaskAggregateMapper());
        return new RuntimeFixture(service, runtime, registry);
    }

    private record RuntimeFixture(AsyncTaskService service, TransferTaskRuntime runtime, TransferTaskRegistry registry) {
        AsyncTask await(UUID taskId, Set<AsyncTaskStatus> statuses, Duration timeout) {
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                AsyncTask current = service.get(taskId);
                if (statuses.contains(current.status())) {
                    return current;
                }
                try {
                    Thread.sleep(25L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return service.get(taskId);
        }
    }
}
