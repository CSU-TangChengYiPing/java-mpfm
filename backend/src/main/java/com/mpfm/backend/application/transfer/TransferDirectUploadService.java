package com.mpfm.backend.application.transfer;

import com.mpfm.backend.application.driver.base.DriverFactory;
import com.mpfm.backend.application.driver.base.DriverCapabilitySnapshot;
import com.mpfm.backend.application.driver.base.StorageDriver;
import com.mpfm.backend.application.file.NamespaceResolver;
import com.mpfm.backend.application.monitor.TransferTelemetryService;
import com.mpfm.backend.application.monitor.UserTransferGovernanceService;
import com.mpfm.backend.application.task.AsyncTask;
import com.mpfm.backend.application.task.AsyncTaskService;
import com.mpfm.backend.application.task.TransferTaskRuntime;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.time.Instant;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * 直传编排服务：负责能力探测、票据签发与直传回执收敛。
 */
@Service
public class TransferDirectUploadService {
    private static final Logger log = LoggerFactory.getLogger(TransferDirectUploadService.class);
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String UPLOAD_MODE_DIRECT = "direct";
    private static final long DEFAULT_DIRECT_CHUNK_SIZE = 8L * 1024L * 1024L;
    private static final long MAX_CHUNK_SIZE = 64L * 1024L * 1024L;
    private static final String STATUS_CANCELED = "CANCELED";

    private final NamespaceResolver namespaceResolver;
    private final DriverFactory driverFactory;
    private final UserTransferGovernanceService governanceService;
    private final TransferTelemetryService transferTelemetryService;
    private final TransferTaskRuntime transferTaskRuntime;
    private final TransferSessionStore sessionStore;
    private final AsyncTaskService asyncTaskService;
    private final ObjectMapper objectMapper;
    private final Map<String, String> directPartTokens = new ConcurrentHashMap<>();

    public TransferDirectUploadService(NamespaceResolver namespaceResolver,
                                       DriverFactory driverFactory,
                                       UserTransferGovernanceService governanceService,
                                       TransferTelemetryService transferTelemetryService,
                                       TransferTaskRuntime transferTaskRuntime,
                                       TransferSessionStore sessionStore,
                                       AsyncTaskService asyncTaskService,
                                       ObjectMapper objectMapper) {
        this.namespaceResolver = namespaceResolver;
        this.driverFactory = driverFactory;
        this.governanceService = governanceService;
        this.transferTelemetryService = transferTelemetryService;
        this.transferTaskRuntime = transferTaskRuntime;
        this.sessionStore = sessionStore;
        this.asyncTaskService = asyncTaskService;
        this.objectMapper = objectMapper;
    }

    // 获取上传能力
    public UploadCapability getUploadCapability(String username, String virtualPath) {
        NamespaceResolver.ResolveResult resolved = namespaceResolver.resolve(username, virtualPath, true, true);
        String protocol = resolved.mount().getType();
        StorageDriver driver = driverFactory.resolve(protocol);
        DriverCapabilitySnapshot snapshot = DriverCapabilitySnapshot.from(protocol, driver.capability());
        // 对齐 OpenList：上传可用性由 Put 能力决定；directUpload 仅表示是否支持直传票据。
        boolean uploadAvailable = snapshot.put();
        long maxPartSizeBytes = snapshot.directUpload() ? MAX_CHUNK_SIZE : 0L;
        long suggestedChunkSizeBytes = snapshot.directUpload() ? DEFAULT_DIRECT_CHUNK_SIZE : 0L;
        return new UploadCapability(uploadAvailable, snapshot.protocol(), maxPartSizeBytes, suggestedChunkSizeBytes);
    }

    // 创建直传票据
    public DirectUploadTicket createDirectUploadTicket(String username,
                                                       String virtualPath,
                                                       String filename,
                                                       long totalBytes,
                                                       Long chunkSizeBytes) {
        governanceService.ensureUploadAllowed(username);
        if (filename == null || filename.isBlank() || totalBytes < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid direct upload request");
        }
        NamespaceResolver.ResolveResult resolved = namespaceResolver.resolve(username, virtualPath, true, true);
        String protocol = resolved.mount().getType();
        StorageDriver driver = driverFactory.resolve(protocol);
        if (!driver.capability().directUpload()) {
            throw new BusinessException(ErrorCode.CAPABILITY_NOT_SUPPORTED, "driver does not support direct upload");
        }
        long chunkSize = normalizeDirectChunkSize(chunkSizeBytes);
        int totalChunks = totalBytes == 0 ? 1 : (int) ((totalBytes + chunkSize - 1) / chunkSize);
        AsyncTask task = transferTaskRuntime.submit("batch_upload", username, virtualPath + "/" + filename);
        UUID uploadId = UUID.randomUUID();
        TransferChunkService.UploadSession session = new TransferChunkService.UploadSession(
                uploadId.toString(), username, virtualPath, filename, totalBytes, chunkSize, totalChunks,
                task.id().toString(), STATUS_RUNNING, Instant.now().toString(), Instant.now().toString(), "", new ArrayList<>(),
                "", UPLOAD_MODE_DIRECT, UUID.randomUUID().toString()
        );
        sessionStore.saveUpload(session);
        asyncTaskService.updatePayloadJson(UUID.fromString(session.taskId()), toUploadPayloadJson(session));
        List<DirectUploadPartTicket> partTickets = new ArrayList<>();
        for (int i = 1; i <= totalChunks; i += 1) {
            String token = UUID.randomUUID().toString();
            String uploadUrl = "/api/v4/transfers/uploads/direct/" + session.taskId() + "/parts/" + i;
            directPartTokens.put(buildPartKey(session.taskId(), i), token);
            partTickets.add(new DirectUploadPartTicket(i, uploadUrl, token));
        }
        return new DirectUploadTicket(session.taskId(), session.uploadId(), session.providerSessionId(), chunkSize, totalChunks, partTickets);
    }

    // 上传直传分片
    public TransferChunkService.UploadSession uploadDirectPart(String username,
                                                               String taskId,
                                                               int partNumber,
                                                               String token,
                                                               long expectedBytes,
                                                               InputStream contentStream) {
        UUID parsedTaskId = parseTaskId(taskId);
        TransferChunkService.UploadSession session = loadDirectUploadSession(parsedTaskId, username);
        if (STATUS_SUCCESS.equals(session.status()) || STATUS_FAILED.equals(session.status()) || STATUS_CANCELED.equals(session.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "direct upload session terminated");
        }
        String key = buildPartKey(taskId, partNumber);
        String expected = directPartTokens.get(key);
        if (expected == null || token == null || token.isBlank() || !expected.equals(token)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "invalid direct upload token");
        }
        long streamedBytes = consumePartStream(contentStream);
        if (expectedBytes >= 0 && streamedBytes != expectedBytes) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "direct upload part size mismatch");
        }
        transferTelemetryService.recordLiveUpload(username, streamedBytes);
        log.info("direct_upload_part_received username={} taskId={} part={} bytes={}", username, taskId, partNumber, streamedBytes);
        List<Integer> next = mergeCompletedParts(session.completedParts(), partNumber);
        TransferChunkService.UploadSession updated = session.withCompletedParts(next, Instant.now().toString())
                .withStatus(STATUS_RUNNING, Instant.now().toString());
        sessionStore.saveUpload(updated);
        asyncTaskService.updatePayloadJson(parsedTaskId, toUploadPayloadJson(updated));
        return updated;
    }

    private long consumePartStream(InputStream contentStream) {
        if (contentStream == null) {
            return 0L;
        }
        byte[] buffer = new byte[64 * 1024];
        long total = 0L;
        try {
            int read;
            while ((read = contentStream.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                total += read;
            }
            return total;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read direct upload stream failed", ex);
        }
    }

    // 确认直传分片完成
    public TransferChunkService.UploadSession ackDirectUpload(String username, String taskId, DirectUploadAckRequest request) {
        UUID parsedTaskId = parseTaskId(taskId);
        TransferChunkService.UploadSession session = loadDirectUploadSession(parsedTaskId, username);
        if (request.failed()) {
            return handleFailedAck(parsedTaskId, session);
        }
        List<Integer> next = mergeCompletedParts(session.completedParts(), request.partNumber());
        String nextStatus = STATUS_RUNNING;
        TransferChunkService.UploadSession updated = session.withCompletedParts(next, Instant.now().toString())
                .withStatus(nextStatus, Instant.now().toString());
        sessionStore.saveUpload(updated);
        asyncTaskService.updatePayloadJson(parsedTaskId, toUploadPayloadJson(updated));
        return updated;
    }

    // 加载直传会话
    private TransferChunkService.UploadSession loadDirectUploadSession(UUID taskId, String username) {
        TransferChunkService.UploadSession session = sessionStore.findUploadByTaskId(taskId)
                .map(found -> sessionStore.loadUpload(found.uploadId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "upload session not found"));
        if (!username.equals(session.operator())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "session owner mismatch");
        }
        if (!UPLOAD_MODE_DIRECT.equalsIgnoreCase(session.uploadMode())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "task is not in direct upload mode");
        }
        return session;
    }

    // 处理失败确认
    private TransferChunkService.UploadSession handleFailedAck(UUID taskId,
                                                               TransferChunkService.UploadSession session) {
        TransferChunkService.UploadSession failed = session.withStatus(STATUS_FAILED, Instant.now().toString());
        sessionStore.saveUpload(failed);
        asyncTaskService.updatePayloadJson(taskId, toUploadPayloadJson(failed));
        return failed;
    }

    // 构建分片键
    private String buildPartKey(String taskId, int partNumber) {
        return taskId + "#" + partNumber;
    }

    // 合并已完成分片
    private List<Integer> mergeCompletedParts(List<Integer> completedParts, int partNumber) {
        List<Integer> next = new ArrayList<>(completedParts);
        if (partNumber > 0 && !next.contains(partNumber)) {
            next.add(partNumber);
        }
        return next;
    }

    // 序列化上传会话
    private String toUploadPayloadJson(TransferChunkService.UploadSession session) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "uploadSessionId", session.uploadId(),
                    "virtualPath", session.virtualPath(),
                    "filename", session.filename(),
                    "uploadMode", session.uploadMode(),
                    "providerSessionId", session.providerSessionId(),
                    "chunkSize", session.chunkSizeBytes(),
                    "totalChunks", session.totalChunks(),
                    "completedParts", session.completedParts()
            ));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "serialize transfer payload failed", ex);
        }
    }

    // 规范直传分片大小 
    private long normalizeDirectChunkSize(Long raw) {
        long chunk = raw == null || raw <= 0 ? DEFAULT_DIRECT_CHUNK_SIZE : raw;
        if (chunk > MAX_CHUNK_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "chunk size too large");
        }
        return chunk;
    }

    // 解析任务 ID
    private UUID parseTaskId(String taskId) {
        try {
            return UUID.fromString(taskId);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid task id", ex);
        }
    }

    /** 上传能力视图：用于前端判断是否可走直传模式。 */
    public record UploadCapability(boolean supportsDirectUpload, String provider, long maxPartSizeBytes, long suggestedChunkSizeBytes) { }

    /** 直传票据：描述服务端签发的上传会话与分片占位信息。 */
    public record DirectUploadTicket(String taskId,
                                     String uploadId,
                                     String providerSessionId,
                                     long chunkSizeBytes,
                                     int totalChunks,
                                     List<DirectUploadPartTicket> parts) { }

    /** 直传分片票据：返回同域上传入口与一次性 token，由服务端校验后登记分片完成。 */
    public record DirectUploadPartTicket(int partNumber, String uploadUrl, String token) { }

    /** 直传回执请求：用于上报分片完成、整体完成或失败终止。 */
    public record DirectUploadAckRequest(int partNumber, boolean completed, boolean failed, String errorCode) { }
}
