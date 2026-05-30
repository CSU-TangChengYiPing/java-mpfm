package com.mpfm.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 异步任务实体，映射 `file_tasks` 表并记录动作、操作者、目标与状态时间轴。
 */
@Entity
@Table(name = "file_tasks")
public class AsyncTaskEntity {

    @Id
    private UUID id;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "operator", nullable = false, length = 128)
    private String operator;

    @Column(name = "target", nullable = false, length = 256)
    private String target;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "created_request_id", length = 64)
    private String createdRequestId;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Embedded
    private final TaskCountSnapshotEmbeddable taskCountSnapshot = new TaskCountSnapshotEmbeddable();

    @Embedded
    private final ChunkProgressSnapshotEmbeddable chunkProgressSnapshot = new ChunkProgressSnapshotEmbeddable();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getCreatedRequestId() { return createdRequestId; }
    public void setCreatedRequestId(String createdRequestId) { this.createdRequestId = createdRequestId; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public int getTotalCount() { return taskCountSnapshot.getTotalCount(); }
    public void setTotalCount(int totalCount) { taskCountSnapshot.setTotalCount(totalCount); }
    public int getSuccessCount() { return taskCountSnapshot.getSuccessCount(); }
    public void setSuccessCount(int successCount) { taskCountSnapshot.setSuccessCount(successCount); }
    public int getFailedCount() { return taskCountSnapshot.getFailedCount(); }
    public void setFailedCount(int failedCount) { taskCountSnapshot.setFailedCount(failedCount); }
    public int getRunningCount() { return taskCountSnapshot.getRunningCount(); }
    public void setRunningCount(int runningCount) { taskCountSnapshot.setRunningCount(runningCount); }
    public String getItemResultsJson() { return taskCountSnapshot.getItemResultsJson(); }
    public void setItemResultsJson(String itemResultsJson) { taskCountSnapshot.setItemResultsJson(itemResultsJson); }
    public long getTransferredBytes() { return chunkProgressSnapshot.getTransferredBytes(); }
    public void setTransferredBytes(long transferredBytes) { chunkProgressSnapshot.setTransferredBytes(transferredBytes); }
    public long getTotalBytes() { return chunkProgressSnapshot.getTotalBytes(); }
    public void setTotalBytes(long totalBytes) { chunkProgressSnapshot.setTotalBytes(totalBytes); }
    public long getChunkSizeBytes() { return chunkProgressSnapshot.getChunkSizeBytes(); }
    public void setChunkSizeBytes(long chunkSizeBytes) { chunkProgressSnapshot.setChunkSizeBytes(chunkSizeBytes); }
    public int getTotalChunks() { return chunkProgressSnapshot.getTotalChunks(); }
    public void setTotalChunks(int totalChunks) { chunkProgressSnapshot.setTotalChunks(totalChunks); }
    public int getCompletedChunks() { return chunkProgressSnapshot.getCompletedChunks(); }
    public void setCompletedChunks(int completedChunks) { chunkProgressSnapshot.setCompletedChunks(completedChunks); }
    public int getFailedChunks() { return chunkProgressSnapshot.getFailedChunks(); }
    public void setFailedChunks(int failedChunks) { chunkProgressSnapshot.setFailedChunks(failedChunks); }
    public String getChunkStatesJson() { return chunkProgressSnapshot.getChunkStatesJson(); }
    public void setChunkStatesJson(String chunkStatesJson) { chunkProgressSnapshot.setChunkStatesJson(chunkStatesJson); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}





