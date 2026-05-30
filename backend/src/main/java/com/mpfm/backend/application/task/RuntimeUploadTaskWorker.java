package com.mpfm.backend.application.task;

import com.mpfm.backend.application.transfer.TransferChunkService;
import com.mpfm.backend.application.transfer.TransferSessionStore;
import com.mpfm.backend.application.transfer.UploadChunkEngine;
import com.mpfm.backend.application.transfer.UploadSessionSupport;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** 运行时上传任务执行器：负责上传分片推进、进度同步与完成收敛。 */
@Component
class RuntimeUploadTaskWorker {
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String UPLOAD_MODE_DIRECT = "direct";
    private static final int SINGLE_CHUNK_COUNT = 1;

    private final TransferSessionStore sessionStore;
    private final UploadChunkEngine uploadChunkEngine;
    private final UploadSessionSupport uploadSessionSupport;
    private final AsyncTaskService asyncTaskService;

    RuntimeUploadTaskWorker(TransferSessionStore sessionStore,
                            UploadChunkEngine uploadChunkEngine,
                            UploadSessionSupport uploadSessionSupport,
                            AsyncTaskService asyncTaskService) {
        this.sessionStore = sessionStore;
        this.uploadChunkEngine = uploadChunkEngine;
        this.uploadSessionSupport = uploadSessionSupport;
        this.asyncTaskService = asyncTaskService;
    }

    void handleBatchUpload(TransferTaskContext context, TransferChunkService.UploadSession initial) throws Exception {
        while (true) {
            context.ensureNotCanceled();
            if (context.isPaused()) {
                return;
            }
            TransferChunkService.UploadSession session = sessionStore.loadUpload(initial.uploadId());
            syncUploadProgress(session);
            if (isDirectMode(session)) {
                if (handleDirectUploadState(session)) {
                    TimeUnit.MILLISECONDS.sleep(200L);
                    continue;
                }
                return;
            }
            if (STATUS_SUCCESS.equals(session.status())) {
                return;
            }
            if (shouldCompleteRuntimeUpload(session)) {
                completeRuntimeUpload(session);
                return;
            }
            if (STATUS_RUNNING.equals(session.status())) {
                try {
                    processRuntimeUploadChunk(session);
                } catch (BusinessException ex) {
                    if (isBandwidthBudgetExceeded(ex)) {
                        TimeUnit.MILLISECONDS.sleep(250L);
                        continue;
                    }
                    throw ex;
                }
                continue;
            }
            ensureUploadSessionActive(session);
            TimeUnit.MILLISECONDS.sleep(200L);
        }
    }

    private boolean isDirectMode(TransferChunkService.UploadSession session) {
        return UPLOAD_MODE_DIRECT.equalsIgnoreCase(session.uploadMode());
    }

    private boolean handleDirectUploadState(TransferChunkService.UploadSession session) {
        UUID taskId = UUID.fromString(session.taskId());
        if (session.completedParts().size() >= session.totalChunks()) {
            completeDirectUpload(session);
            return false;
        }
        if (STATUS_SUCCESS.equals(session.status())) {
            completeDirectUpload(session);
            return false;
        }
        if (STATUS_FAILED.equals(session.status())) {
            asyncTaskService.markFailed(taskId, ErrorCode.INTERNAL_ERROR.name());
            return false;
        }
        if (STATUS_CANCELED.equals(session.status())) {
            asyncTaskService.updateStatus(taskId, AsyncTaskStatus.CANCELED);
            return false;
        }
        return true;
    }

    private void completeDirectUpload(TransferChunkService.UploadSession session) {
        if (session.dataFilePath() == null || session.dataFilePath().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "direct upload data file missing");
        }
        uploadSessionSupport.finalizeUploadFile(session.operator(), session);
        asyncTaskService.markSuccess(UUID.fromString(session.taskId()));
    }

    private boolean shouldCompleteRuntimeUpload(TransferChunkService.UploadSession session) {
        return STATUS_RUNNING.equals(session.status())
                && session.completedParts().size() >= session.totalChunks();
    }

    private void processRuntimeUploadChunk(TransferChunkService.UploadSession session) {
        int nextPart = findNextPendingPart(session.totalChunks(), session.completedParts());
        if (nextPart < 0) {
            return;
        }
        byte[] chunk = readSourceChunk(session, nextPart);
        TransferChunkService.UploadSession updated = uploadChunkEngine.writePart(
                session.operator(), session, nextPart, chunk, null);
        sessionStore.saveUpload(updated);
        syncUploadProgress(updated);
    }

    private boolean isBandwidthBudgetExceeded(BusinessException ex) {
        return ex != null && ex.getCode() == ErrorCode.CAPABILITY_RESTRICTED;
    }

    private int findNextPendingPart(int totalChunks, List<Integer> completedParts) {
        for (int i = 1; i <= totalChunks; i += 1) {
            if (!completedParts.contains(i)) {
                return i;
            }
        }
        return -1;
    }

    private byte[] readSourceChunk(TransferChunkService.UploadSession session, int partNumber) {
        String sourcePath = session.sourceFilePath();
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "runtime upload source missing");
        }
        Path source = Path.of(sourcePath);
        if (!Files.exists(source)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "runtime upload source not found");
        }
        long expectedSize = expectedChunkSize(session.totalBytes(), session.chunkSizeBytes(), session.totalChunks(), partNumber);
        try (RandomAccessFile raf = new RandomAccessFile(source.toFile(), "r")) {
            long offset = (long) (partNumber - 1) * session.chunkSizeBytes();
            raf.seek(offset);
            byte[] buffer = new byte[(int) expectedSize];
            raf.readFully(buffer);
            return buffer;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read runtime upload source failed", ex);
        }
    }

    private long expectedChunkSize(long totalBytes, long chunkSize, int totalChunks, int partNumber) {
        if (totalChunks <= SINGLE_CHUNK_COUNT) {
            return totalBytes;
        }
        if (partNumber < totalChunks) {
            return chunkSize;
        }
        long remain = totalBytes - (long) (totalChunks - 1) * chunkSize;
        return Math.max(0L, remain);
    }

    private void completeRuntimeUpload(TransferChunkService.UploadSession session) {
        TransferChunkService.UploadSession completed = uploadChunkEngine.complete(session);
        uploadSessionSupport.finalizeUploadFile(session.operator(), completed);
        sessionStore.saveUpload(completed);
        cleanupRuntimeSourceFile(session.sourceFilePath());
        asyncTaskService.markSuccess(UUID.fromString(session.taskId()));
    }

    private void cleanupRuntimeSourceFile(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(sourcePath));
        } catch (IOException ignored) {
            // 保持任务主链路可用，清理失败由后续会话清理流程兜底。
        }
    }

    private void ensureUploadSessionActive(TransferChunkService.UploadSession session) {
        if (STATUS_FAILED.equals(session.status()) || STATUS_CANCELED.equals(session.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "upload session terminated");
        }
    }

    private void syncUploadProgress(TransferChunkService.UploadSession session) {
        int completed = session.completedParts().size();
        List<String> states = new ArrayList<>();
        for (int i = 1; i <= session.totalChunks(); i += 1) {
            states.add(session.completedParts().contains(i) ? "DONE" : "PENDING");
        }
        long transferred = Math.min(session.totalBytes(), (long) completed * session.chunkSizeBytes());
        asyncTaskService.updateChunkProgress(
                UUID.fromString(session.taskId()),
                transferred,
                session.totalBytes(),
                session.chunkSizeBytes(),
                session.totalChunks(),
                completed,
                0,
                states,
                List.of()
        );
    }
}
