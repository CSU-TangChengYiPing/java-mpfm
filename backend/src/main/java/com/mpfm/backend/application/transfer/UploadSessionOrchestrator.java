package com.mpfm.backend.application.transfer;

import com.mpfm.backend.application.monitor.UserTransferGovernanceService;
import com.mpfm.backend.application.monitor.TransferTelemetryService;
import com.mpfm.backend.application.task.AsyncTask;
import com.mpfm.backend.application.task.AsyncTaskService;
import com.mpfm.backend.application.task.TransferTaskRuntime;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 上传会话编排器：负责会话初始化、分片写入、完成合并与载荷持久化。 */
@Component
class UploadSessionOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(UploadSessionOrchestrator.class);
    private final UserTransferGovernanceService governanceService;
    private final TransferTelemetryService transferTelemetryService;
    private final AsyncTaskService asyncTaskService;
    private final TransferTaskRuntime transferTaskRuntime;
    private final TransferSessionStore sessionStore;
    private final UploadChunkEngine uploadChunkEngine;
    private final UploadSessionSupport uploadSessionSupport;

    UploadSessionOrchestrator(UserTransferGovernanceService governanceService,
                              TransferTelemetryService transferTelemetryService,
                              AsyncTaskService asyncTaskService,
                              TransferTaskRuntime transferTaskRuntime,
                              TransferSessionStore sessionStore,
                              UploadChunkEngine uploadChunkEngine,
                              UploadSessionSupport uploadSessionSupport) {
        this.governanceService = governanceService;
        this.transferTelemetryService = transferTelemetryService;
        this.asyncTaskService = asyncTaskService;
        this.transferTaskRuntime = transferTaskRuntime;
        this.sessionStore = sessionStore;
        this.uploadChunkEngine = uploadChunkEngine;
        this.uploadSessionSupport = uploadSessionSupport;
    }

    TransferChunkService.UploadSession uploadSingleChunkTask(String username, String virtualPath, String filename, byte[] content) {
        TransferChunkService.UploadSession session = initUploadInternal(
                username, virtualPath, filename, content == null ? 0L : content.length, content == null ? null : (long) content.length);
        TransferChunkService.UploadSession updated = uploadPartInternal(username, session.uploadId(), 1, content == null ? new byte[0] : content, null);
        return completeUploadInternal(username, updated.uploadId());
    }

    TransferChunkService.UploadSession getUploadSession(String username, String uploadId) {
        TransferChunkService.UploadSession session = sessionStore.loadUpload(uploadId);
        uploadSessionSupport.ensureOwner(username, session.operator());
        return session;
    }

    TransferChunkService.UploadSession createStreamUpload(String username,
                                                          String virtualPath,
                                                          String filename,
                                                          long totalBytes,
                                                          InputStream contentStream) {
        governanceService.ensureUploadAllowed(username);
        if (filename == null || filename.isBlank() || contentStream == null || totalBytes < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid stream upload request");
        }
        long chunkSize = uploadSessionSupport.normalizeChunkSize(null);
        int totalChunks = totalBytes == 0 ? 1 : (int) ((totalBytes + chunkSize - 1) / chunkSize);
        UUID uploadId = UUID.randomUUID();
        long totalStart = System.nanoTime();
        Path targetFile = uploadSessionSupport.resolveUploadTargetFile(username, virtualPath, filename);
        UploadSessionSupport.WriteResult writeResult = uploadSessionSupport.writeStreamToTargetFile(targetFile, contentStream, totalBytes);
        transferTelemetryService.recordLiveUpload(username, writeResult.writtenBytes());
        List<Integer> completedParts = buildCompletedParts(totalChunks);
        String now = Instant.now().toString();
        TransferChunkService.UploadSession completed = new TransferChunkService.UploadSession(
                uploadId.toString(),
                username,
                virtualPath,
                filename,
                totalBytes,
                chunkSize,
                totalChunks,
                "",
                "SUCCESS",
                now,
                now,
                targetFile.toString(),
                completedParts,
                "",
                "stream",
                ""
        );
        sessionStore.saveUpload(completed);
        long totalMs = (System.nanoTime() - totalStart) / 1_000_000L;
        log.info("upload_stream_commit username={} virtualPath={} filename={} bytes={} writeMs={} totalMs={}",
                username, virtualPath, filename, writeResult.writtenBytes(), writeResult.writeMs(), totalMs);
        return completed;
    }

    private List<Integer> buildCompletedParts(int totalChunks) {
        List<Integer> completedParts = new ArrayList<>();
        for (int i = 1; i <= totalChunks; i += 1) {
            completedParts.add(i);
        }
        return completedParts;
    }

    private TransferChunkService.UploadSession initUploadInternal(String username, String virtualPath, String filename, long totalBytes, Long chunkSizeBytes) {
        governanceService.ensureUploadAllowed(username);
        if (filename == null || filename.isBlank() || totalBytes < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid upload init request");
        }
        long chunkSize = uploadSessionSupport.normalizeChunkSize(chunkSizeBytes);
        int totalChunks = totalBytes == 0 ? 1 : (int) ((totalBytes + chunkSize - 1) / chunkSize);
        AsyncTask task = transferTaskRuntime.submit("batch_upload", username, virtualPath + "/" + filename);
        UUID uploadId = UUID.randomUUID();
        Path dataFile = sessionStore.resolveUploadDataFile(uploadId.toString());
        TransferChunkService.UploadSession session = uploadSessionSupport.buildRuntimeSession(
                uploadId, username, virtualPath, filename, totalBytes, chunkSize, totalChunks,
                task.id().toString(), dataFile.toString(), "");
        sessionStore.saveUpload(session);
        asyncTaskService.updatePayloadJson(UUID.fromString(session.taskId()), uploadSessionSupport.toUploadPayloadJson(session));
        return session;
    }

    private TransferChunkService.UploadSession uploadPartInternal(String username, String uploadId, int partNumber, byte[] content, String chunkSha256Hex) {
        governanceService.ensureUploadAllowed(username);
        TransferChunkService.UploadSession session = sessionStore.loadUpload(uploadId);
        uploadSessionSupport.ensureOwner(username, session.operator());
        uploadSessionSupport.ensureTaskWritable(session.taskId());
        if (!"RUNNING".equals(session.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "upload session is not running");
        }
        TransferChunkService.UploadSession updated = uploadChunkEngine.writePart(username, session, partNumber, content, chunkSha256Hex);
        sessionStore.saveUpload(updated);
        asyncTaskService.updatePayloadJson(UUID.fromString(updated.taskId()), uploadSessionSupport.toUploadPayloadJson(updated));
        return updated;
    }

    private TransferChunkService.UploadSession completeUploadInternal(String username, String uploadId) {
        TransferChunkService.UploadSession session = sessionStore.loadUpload(uploadId);
        uploadSessionSupport.ensureOwner(username, session.operator());
        uploadSessionSupport.ensureTaskWritable(session.taskId());
        TransferChunkService.UploadSession completed = uploadChunkEngine.complete(session);
        uploadSessionSupport.finalizeUploadFile(username, session);
        sessionStore.saveUpload(completed);
        asyncTaskService.updatePayloadJson(UUID.fromString(completed.taskId()), uploadSessionSupport.toUploadPayloadJson(completed));
        return completed;
    }
}
