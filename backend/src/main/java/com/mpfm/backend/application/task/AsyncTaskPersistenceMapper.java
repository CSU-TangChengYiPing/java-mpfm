package com.mpfm.backend.application.task;

import com.mpfm.backend.infrastructure.persistence.entity.AsyncTaskEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * 任务实体与领域对象映射器，统一处理持久化字段与子项明细序列化细节。
 */
@Component
class AsyncTaskPersistenceMapper {
    AsyncTaskEntity toEntity(AsyncTask task) {
        AsyncTaskEntity entity = new AsyncTaskEntity();
        entity.setId(task.id());
        entity.setAction(task.action());
        entity.setOperator(task.operator());
        entity.setTarget(task.target());
        entity.setStatus(task.status().name());
        entity.setProgress(task.progress());
        entity.setErrorCode(task.errorCode());
        entity.setCreatedRequestId(task.createdRequestId());
        entity.setPayloadJson(task.payloadJson());
        entity.setTotalCount(0);
        entity.setSuccessCount(0);
        entity.setFailedCount(0);
        entity.setRunningCount(0);
        entity.setItemResultsJson("[]");
        entity.setTransferredBytes(task.transferredBytes());
        entity.setTotalBytes(task.totalBytes());
        entity.setChunkSizeBytes(0L);
        entity.setTotalChunks(0);
        entity.setCompletedChunks(0);
        entity.setFailedChunks(0);
        entity.setChunkStatesJson("[]");
        entity.setCreatedAt(OffsetDateTime.ofInstant(task.createdAt(), ZoneOffset.UTC));
        entity.setUpdatedAt(OffsetDateTime.ofInstant(task.updatedAt(), ZoneOffset.UTC));
        return entity;
    }

    AsyncTask fromEntity(AsyncTaskEntity entity) {
        return new AsyncTask(
                entity.getId(),
                entity.getAction(),
                entity.getOperator(),
                entity.getTarget(),
                AsyncTaskStatus.valueOf(entity.getStatus()),
                entity.getProgress(),
                entity.getCreatedAt().toInstant(),
                entity.getUpdatedAt().toInstant(),
                valueOrEmpty(entity.getErrorCode()),
                valueOrEmpty(entity.getCreatedRequestId()),
                valueOrEmpty(entity.getPayloadJson()),
                entity.getTransferredBytes(),
                entity.getTotalBytes());
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
