package com.mpfm.backend.application.task;

import java.util.Locale;

/**
 * 传输任务动作语义：统一动作分组与运行时写口判定，避免多处散落规则漂移。
 */
public final class TransferTaskAction {
    public static final String ACTION_UPLOAD = "upload";
    public static final String ACTION_BATCH_UPLOAD = "batch_upload";
    public static final String TASK_GROUP_UPLOAD = "upload";
    public static final String TASK_GROUP_TRANSFER = "transfer";

    private TransferTaskAction() {
    }

    /** 判断是否上传类动作。 */
    public static boolean isUploadAction(String action) {
        String normalized = normalize(action);
        return ACTION_UPLOAD.equals(normalized)
                || ACTION_BATCH_UPLOAD.equals(normalized)
                || normalized.contains("upload")
                || normalized.contains("put_url");
    }

    /** 判断是否需要 Runtime 写口约束的传输动作。 */
    public static boolean isTransferRuntimeAction(String action) {
        String normalized = normalize(action);
        return ACTION_BATCH_UPLOAD.equals(normalized);
    }

    /** 下载能力已下线，统一返回 false。 */
    public static boolean isDownloadAction(String action) {
        return false;
    }

    /** 将动作映射为任务分组。 */
    public static String resolveTaskGroup(String action) {
        if (isUploadAction(action)) {
            return TASK_GROUP_UPLOAD;
        }
        return TASK_GROUP_TRANSFER;
    }

    private static String normalize(String action) {
        return action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
    }
}
