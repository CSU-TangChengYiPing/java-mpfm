package com.mpfm.backend.application.transfer;

import com.mpfm.backend.application.file.NamespaceResolver;
import com.mpfm.backend.application.task.AsyncTask;
import com.mpfm.backend.application.task.AsyncTaskStatus;
import com.mpfm.backend.application.task.AsyncTaskService;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/** 上传会话辅助器：处理会话构造、载荷序列化与落盘收尾等通用逻辑。 */
@Component
public class UploadSessionSupport {
    private static final Logger log = LoggerFactory.getLogger(UploadSessionSupport.class);
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String UPLOAD_MODE_RUNTIME = "runtime";
    private static final long DEFAULT_CHUNK_SIZE = 4L * 1024L * 1024L;
    private static final long MAX_CHUNK_SIZE = 64L * 1024L * 1024L;

    private final AsyncTaskService asyncTaskService;
    private final NamespaceResolver namespaceResolver;
    private final ObjectMapper objectMapper;

    UploadSessionSupport(AsyncTaskService asyncTaskService,
                         NamespaceResolver namespaceResolver,
                         ObjectMapper objectMapper) {
        this.asyncTaskService = asyncTaskService;
        this.namespaceResolver = namespaceResolver;
        this.objectMapper = objectMapper;
    }

    long normalizeChunkSize(Long raw) {
        long chunk = raw == null || raw <= 0 ? DEFAULT_CHUNK_SIZE : raw;
        if (chunk > MAX_CHUNK_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "chunk size too large");
        }
        return chunk;
    }

    void ensureOwner(String username, String owner) {
        if (!username.equals(owner)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "session owner mismatch");
        }
    }

    void ensureTaskWritable(String taskId) {
        AsyncTask task = asyncTaskService.get(UUID.fromString(taskId));
        if (task.status() == AsyncTaskStatus.PAUSED
                || task.status() == AsyncTaskStatus.PAUSING
                || task.status() == AsyncTaskStatus.CANCELED
                || task.status() == AsyncTaskStatus.CANCELING
                || task.status() == AsyncTaskStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "task is not writable in current state");
        }
    }

    TransferChunkService.UploadSession buildRuntimeSession(UUID uploadId,
                                                           String username,
                                                           String virtualPath,
                                                           String filename,
                                                           long totalBytes,
                                                           long chunkSize,
                                                           int totalChunks,
                                                           String taskId,
                                                           String dataFilePath,
                                                           String sourcePath) {
        return new TransferChunkService.UploadSession(
                uploadId.toString(), username, virtualPath, filename, totalBytes, chunkSize, totalChunks,
                taskId, STATUS_RUNNING, Instant.now().toString(), Instant.now().toString(), dataFilePath, new ArrayList<>(),
                sourcePath == null ? "" : sourcePath, UPLOAD_MODE_RUNTIME, "");
    }

    void writeRuntimeSourceFile(Path sourceFile, InputStream contentStream) {
        try {
            Files.createDirectories(sourceFile.getParent());
            Files.copy(contentStream, sourceFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "prepare runtime upload source failed", ex);
        }
    }

    Path resolveUploadTargetFile(String username, String virtualPath, String filename) {
        NamespaceResolver.ResolveResult resolved = namespaceResolver.resolve(username, virtualPath, true, true);
        Path root = Path.of(resolved.mount().getPhysicalRoot()).normalize();
        Path targetDir = ".".equals(resolved.relPath()) ? root : root.resolve(resolved.relPath()).normalize();
        Path targetFile = targetDir.resolve(filename).normalize();
        if (!targetFile.startsWith(root)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid upload path");
        }
        return targetFile;
    }

    WriteResult writeStreamToTargetFile(Path targetFile, InputStream contentStream, long expectedTotalBytes) {
        Path tempFile = targetFile.resolveSibling(targetFile.getFileName().toString() + ".uploading");
        long written = 0L;
        byte[] buffer = new byte[64 * 1024];
        long startedAt = System.nanoTime();
        try {
            Files.createDirectories(targetFile.getParent());
            try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
                int read;
                while ((read = contentStream.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    outputStream.write(buffer, 0, read);
                    written += read;
                }
                outputStream.flush();
            }
            if (written != expectedTotalBytes) {
                Files.deleteIfExists(tempFile);
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "stream upload size mismatch");
            }
            Files.move(tempFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            return new WriteResult(targetFile, written, elapsedMs);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignore) {
                log.warn("cleanup temp upload file failed: {}", tempFile, ignore);
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "write stream upload payload failed", ex);
        }
    }

    public Path finalizeUploadFile(String username, TransferChunkService.UploadSession session) {
        Path targetFile = resolveUploadTargetFile(username, session.virtualPath(), session.filename());
        try {
            Files.createDirectories(targetFile.getParent());
            Files.copy(Path.of(session.dataFilePath()), targetFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("runtime upload finalized: taskId={} virtualPath={} targetFile={}",
                    session.taskId(), session.virtualPath(), targetFile);
            return targetFile;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "finalize upload failed", ex);
        }
    }

    String toUploadPayloadJson(TransferChunkService.UploadSession session) {
        return toJson(Map.of(
                "uploadSessionId", session.uploadId(),
                "virtualPath", session.virtualPath(),
                "filename", session.filename(),
                "uploadMode", session.uploadMode(),
                "sourceFilePath", session.sourceFilePath(),
                "providerSessionId", session.providerSessionId(),
                "chunkSize", session.chunkSizeBytes(),
                "totalChunks", session.totalChunks(),
                "completedParts", session.completedParts()
        ));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "serialize transfer payload failed", ex);
        }
    }

    /** 流式写入结果：用于上传阶段耗时与字节观测。 */
    record WriteResult(Path targetFile, long writtenBytes, long writeMs) { }
}
