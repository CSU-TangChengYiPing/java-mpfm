package com.mpfm.backend.application.task;

/**
 * 异步任务状态枚举，定义任务从创建到结束的状态集合。
 */
public enum AsyncTaskStatus {
    /** 任务已创建，等待执行。 */
    PENDING,
    /** 任务正在执行中。 */
    RUNNING,
    /** 任务正在暂停中。 */
    PAUSING,
    /** 任务已暂停。 */
    PAUSED,
    /** 任务正在恢复中。 */
    RESUMING,
    /** 任务等待重试。 */
    RETRY_WAITING,
    /** 任务正在重试。 */
    RETRYING,
    /** 任务正在取消中。 */
    CANCELING,
    /** 任务执行成功。 */
    SUCCESS,
    /** 任务执行失败。 */
    FAILED,
    /** 任务已取消。 */
    CANCELED
}




