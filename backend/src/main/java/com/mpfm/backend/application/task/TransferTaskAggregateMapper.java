package com.mpfm.backend.application.task;

import org.springframework.stereotype.Component;

/** 任务聚合映射器：将持久化快照转换为运行时聚合，隔离字段噪声。 */
@Component
public class TransferTaskAggregateMapper {
    public TransferTaskAggregate from(AsyncTask task) {
        return new TransferTaskAggregate(
                task.id(),
                task.action(),
                task.operator(),
                task.target(),
                task.status(),
                task.progress(),
                task.createdAt(),
                task.updatedAt(),
                task.errorCode(),
                task.transferredBytes(),
                task.totalBytes()
        );
    }
}
