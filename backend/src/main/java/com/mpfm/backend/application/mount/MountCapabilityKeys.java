package com.mpfm.backend.application.mount;

/**
 * 挂载能力字段键常量：统一能力探针输出键，避免多处硬编码导致语义漂移。
 */
final class MountCapabilityKeys {
    static final String VERSION = "1.0";
    static final String CORE_LIST_TREE = "list_tree";
    static final String CORE_STAT = "stat";
    static final String CORE_GET_BY_PATH = "get_by_path";
    static final String CORE_UPLOAD = "upload";
    static final String CORE_DOWNLOAD = "download";
    static final String CORE_MKDIR = "mkdir";
    static final String CORE_DELETE = "delete";
    static final String CORE_LINK = "link";
    static final String CORE_RENAME = "rename";
    static final String CORE_MOVE = "move";
    static final String CORE_COPY = "copy";
    static final String CORE_HEALTH_BASIC = "health_basic";
    static final String EXT_PUT_URL = "put_url";
    static final String EXT_BATCH_UPLOAD = "batch_upload";
    static final String EXT_COPY = "copy";
    static final String EXT_SYMLINK_RESOLVE = "symlink_resolve";
    static final String EXT_ASYNC_TASK_RESULT = "async_task_result";
    static final String EXT_STORAGE_DETAILS = "storage_details";
    static final String EXT_ARCHIVE_META = "archive_meta";
    static final String EXT_ARCHIVE_PREVIEW = "archive_preview";
    static final String EXT_ARCHIVE_READ_INNER = "archive_read_inner";
    static final String EXT_ARCHIVE_EXTRACT = "archive_extract";
    static final String EXT_ARCHIVE_COMPRESS = "archive_compress";
    static final String CONSTRAINT_NO_UPLOAD = "no_upload";
    static final String CONSTRAINT_NO_OVERWRITE_UPLOAD = "no_overwrite_upload";
    static final String CONSTRAINT_ONLY_PROXY = "only_proxy";
    static final String CONSTRAINT_NO_LINK_URL = "no_link_url";
    static final String CONSTRAINT_PREFER_PROXY = "prefer_proxy";
    static final String CONSTRAINT_MAX_UPLOAD_SIZE_MB = "max_upload_size_mb";
    static final String CONSTRAINT_RATE_LIMIT_PROFILE = "rate_limit_profile";
    static final String DEFAULT_RATE_LIMIT_PROFILE = "default";

    private MountCapabilityKeys() {
    }
}
