package com.mpfm.backend.application.task;

/** 任务控制异常：用于执行器协作取消/暂停等流程控制，不视为系统异常。 */
class TransferTaskControlException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    TransferTaskControlException(String message) {
        super(message);
    }
}
