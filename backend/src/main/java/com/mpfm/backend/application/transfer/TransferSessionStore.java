package com.mpfm.backend.application.transfer;

import tools.jackson.databind.ObjectMapper;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** 会话存储：负责上传会话落盘与恢复，保证进程重启后仍可继续传输。 */
@Component
public class TransferSessionStore {
    private final ObjectMapper objectMapper;
    private final Path baseDir;

    public TransferSessionStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.baseDir = Path.of(System.getProperty("java.io.tmpdir"), "mpfm-v4-transfer-sessions");
    }

    public TransferChunkService.UploadSession loadUpload(String uploadId) {
        Path meta = uploadMeta(uploadId);
        if (!Files.exists(meta)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "upload session not found");
        }
        try {
            return objectMapper.readValue(meta.toFile(), TransferChunkService.UploadSession.class);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read upload session failed", ex);
        }
    }

    public void saveUpload(TransferChunkService.UploadSession session) {
        write(uploadMeta(session.uploadId()), session, "persist upload session failed");
    }

    public Optional<TransferChunkService.UploadSession> findUploadByTaskId(UUID taskId) {
        return findByTaskId(taskId, "session-upload.json", TransferChunkService.UploadSession.class);
    }

    public Path resolveUploadDataFile(String uploadId) {
        return sessionDir(uploadId).resolve("payload.bin");
    }

    public Path resolveUploadSourceFile(String uploadId) {
        return sessionDir(uploadId).resolve("source.bin");
    }

    private Path uploadMeta(String uploadId) {
        return sessionDir(uploadId).resolve("session-upload.json");
    }

    private Path sessionDir(String sessionId) {
        return baseDir.resolve(sessionId);
    }

    private void write(Path metaFile, Object payload, String errorMessage) {
        try {
            Files.createDirectories(metaFile.getParent());
            objectMapper.writeValue(metaFile.toFile(), payload);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, errorMessage, ex);
        }
    }

    private <T> Optional<T> findByTaskId(UUID taskId, String fileName, Class<T> type) {
        if (!Files.exists(baseDir)) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.walk(baseDir, 2)) {
            return stream
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .map(path -> read(path, type))
                    .filter(value -> matchTaskId(value, taskId))
                    .findFirst();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "scan transfer session failed", ex);
        }
    }

    private <T> T read(Path meta, Class<T> type) {
        try {
            return objectMapper.readValue(meta.toFile(), type);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read transfer session failed", ex);
        }
    }

    private boolean matchTaskId(Object session, UUID taskId) {
        return session instanceof TransferChunkService.UploadSession uploadSession
                && taskId.toString().equals(uploadSession.taskId());
    }
}
