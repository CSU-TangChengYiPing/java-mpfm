package com.mpfm.backend.application.task;

/** 任务执行器契约：按任务类型执行实际工作，需配合上下文处理暂停/取消信号。 */
@FunctionalInterface
public interface TransferTaskHandler {
    void execute(TransferTaskContext context) throws Exception;
}
