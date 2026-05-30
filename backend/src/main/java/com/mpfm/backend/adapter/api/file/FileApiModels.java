package com.mpfm.backend.adapter.api.file;

import com.mpfm.backend.application.file.FileApplicationService;
import com.mpfm.backend.application.task.AsyncTask;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * 文件域接口模型集合，集中定义文件操作相关请求与响应结构。
 */
public class FileApiModels {

    /** 写文件请求；分片上传时通过 `chunkIndex/totalChunks/uploadId` 传递分片上下文。 */
    public record WriteFileRequest(@NotBlank String virtualPath, String content,
                                   Integer chunkIndex, Integer totalChunks, String uploadId) { }
    /** 单路径操作请求，适用于 mkdir/delete/symlinkResolve 等按路径执行的命令。 */
    public record PathOperationRequest(@NotBlank String virtualPath) { }
    /** 重命名请求，`targetName` 为同目录下目标名称。 */
    public record RenameRequest(@NotBlank String virtualPath, @NotBlank String targetName) { }
    /** 移动/复制请求，`fromPath` 为源路径，`toPath` 为目标路径。 */
    public record MoveRequest(@NotBlank String fromVirtualPath, @NotBlank String toVirtualPath) { }
    /** URL 导入请求，`expectedSizeBytes` 用于下载前的大小校验。 */
    public record PutUrlRequest(@NotBlank String url, @NotBlank String targetVirtualPath, Long expectedSizeBytes) { }
    /** 批量路径请求，构造时会将空列表归一化为不可变空集合。 */
    public record BatchPathRequest(List<String> virtualPaths) {
        public BatchPathRequest(List<String> virtualPaths) {
            this.virtualPaths = virtualPaths == null ? List.of() : List.copyOf(virtualPaths);
        }
    }
    /** 扩展执行请求，`extensionKey` 标识扩展能力，`payload` 传递扩展输入。 */
    public record ExtensionExecRequest(@NotBlank String extensionKey, String payload) { }
    /** 断点续传初始化请求。 */
    public record UploadInitRequest(UUID mountId, @NotBlank String path, @NotBlank String filename, long totalBytes, Long chunkSizeBytes) { }
    /** 断点续传查询请求。 */
    public record UploadStatusRequest(UUID mountId, UUID uploadId) { }
    /** 断点续传完成请求。 */
    public record UploadCompleteRequest(UUID mountId, UUID uploadId) { }
    /** 断点续传单块请求参数。 */
    public record UploadChunkMeta(UUID mountId, UUID uploadId, int chunkIndex) { }

    /** 文件条目响应，统一对齐模块4 FileEntry 字段口径。 */
    public record FileEntryResponse(String path, String name, String type, long sizeBytes, String mtime,
                                    Long linkCount, boolean visible, boolean readable, boolean writable,
                                    String etag, String version) {
        public static FileEntryResponse from(FileApplicationService.EntryResult entry) {
            return new FileEntryResponse(
                    entry.path(), entry.name(), entry.type(), entry.sizeBytes(), entry.mtime(),
                    entry.linkCount(), entry.visible(), entry.readable(), entry.writable(),
                    entry.etag(), entry.version());
        }
    }
    /** 列表响应统一外形：items + page。 */
    public record FileItemsResponse(java.util.List<FileEntryResponse> items, PageMeta page) { }
    /** 单条响应统一外形：entry。 */
    public record FileEntryEnvelope(FileEntryResponse entry) { }
    /** 内容响应统一外形：entry + content。 */
    public record FileContentEnvelope(FileEntryResponse entry, String content) { }
    /** 分页元信息（当前阶段固定单页）。 */
    public record PageMeta(int page, int pageSize, int total) { }

    /** 文本内容响应，返回读取路径及对应文本内容。 */
    public record FileContentResponse(String path, String content) { }
    /** 通用操作响应，返回动作、作用路径和执行状态。 */
    public record OperationResponse(String action, String path, String status) { }
    /** 异步任务响应，返回任务标识、状态、进度、聚合统计与请求链路标识。 */
    public record TaskResponse(String taskId, String action, String status, int progress,
                               long transferredBytes, long totalBytes, String payloadJson,
                               String createdAt, String requestId, String createdRequestId, String errorCode) {
        public static TaskResponse from(AsyncTask task) {
            String requestId = valueOrEmpty(MDC.get("requestId"));
            return new TaskResponse(task.id().toString(), task.action(), task.status().name(), task.progress(),
                    task.transferredBytes(), task.totalBytes(), valueOrEmpty(task.payloadJson()),
                    task.createdAt().toString(), requestId, valueOrEmpty(task.createdRequestId()), valueOrEmpty(task.errorCode()));
        }

        private static String valueOrEmpty(String value) {
            return value == null ? "" : value;
        }
    }
    /** 断点续传初始化响应。 */
    public record UploadInitResponse(String uploadId, long chunkSizeBytes, int totalChunks, String taskId) { }
    /** 断点续传状态响应。 */
    public record UploadStatusResponse(String uploadId, String taskId, String status,
                                       long transferredBytes, long totalBytes, long chunkSizeBytes,
                                       int totalChunks, int completedChunks, int failedChunks,
                                       List<String> chunkStates) { }
    /** 断点续传单块响应。 */
    public record UploadChunkResponse(String uploadId, int chunkIndex, String status, long transferredBytes, int completedChunks) { }
}




