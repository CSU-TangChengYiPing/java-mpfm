package com.mpfm.backend.application.transfer;

import java.util.List;
import java.io.InputStream;
import org.springframework.stereotype.Service;

/** v4 分片传输服务：作为控制器入口门面，委派上传与下载会话编排器执行。 */
@Service
public class TransferChunkService {
    private final UploadSessionOrchestrator uploadOrchestrator;

    public TransferChunkService(UploadSessionOrchestrator uploadOrchestrator) {
        this.uploadOrchestrator = uploadOrchestrator;
    }

    public UploadSession uploadSingleChunkTask(String username, String virtualPath, String filename, byte[] content) {
        return uploadOrchestrator.uploadSingleChunkTask(username, virtualPath, filename, content);
    }

    public UploadSession createStreamUpload(String username,
                                            String virtualPath,
                                            String filename,
                                            long totalBytes,
                                            InputStream contentStream) {
        return uploadOrchestrator.createStreamUpload(username, virtualPath, filename, totalBytes, contentStream);
    }

    public UploadSession getUploadSession(String username, String uploadId) {
        return uploadOrchestrator.getUploadSession(username, uploadId);
    }

    /** 上传会话视图：提供分片元数据、任务映射和会话状态。 */
    public record UploadSession(String uploadId, String operator, String virtualPath, String filename,
                                long totalBytes, long chunkSizeBytes, int totalChunks, String taskId, String status,
                                String createdAt, String updatedAt, String dataFilePath, List<Integer> completedParts,
                                String sourceFilePath, String uploadMode, String providerSessionId) {
        public UploadSession withCompletedParts(List<Integer> parts, String updatedAtValue) {
            return new UploadSession(uploadId, operator, virtualPath, filename, totalBytes, chunkSizeBytes, totalChunks,
                    taskId, status, createdAt, updatedAtValue, dataFilePath, List.copyOf(parts), sourceFilePath, uploadMode, providerSessionId);
        }

        public UploadSession withStatus(String nextStatus, String updatedAtValue) {
            return new UploadSession(uploadId, operator, virtualPath, filename, totalBytes, chunkSizeBytes, totalChunks,
                    taskId, nextStatus, createdAt, updatedAtValue, dataFilePath, completedParts, sourceFilePath, uploadMode, providerSessionId);
        }

        public UploadSession withUpdatedAt(String updatedAtValue) {
            return new UploadSession(uploadId, operator, virtualPath, filename, totalBytes, chunkSizeBytes, totalChunks,
                    taskId, status, createdAt, updatedAtValue, dataFilePath, completedParts, sourceFilePath, uploadMode, providerSessionId);
        }
    }

}
