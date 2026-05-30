package com.mpfm.backend.adapter.api.file;

import com.mpfm.backend.application.file.FileApplicationService;
import java.security.Principal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件写操作控制器，处理创建、改写、移动、批量任务与扩展执行等命令型接口。
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileCommandController {
    private static final String IF_MATCH_HEADER = "If-Match";

    private final FileApplicationService fileApplicationService;

    public FileCommandController(FileApplicationService fileApplicationService) {
        this.fileApplicationService = fileApplicationService;
    }

    // 写入文件内容
    @PutMapping("/content")
    public FileApiModels.FileEntryResponse write(@RequestBody FileApiModels.WriteFileRequest request,
                                                 @RequestHeader(IF_MATCH_HEADER) String ifMatch,
                                                 Principal principal) {
        boolean append = request.totalChunks() != null && request.totalChunks() > 1
                && request.chunkIndex() != null && request.chunkIndex() > 0;
        return FileApiModels.FileEntryResponse.from(fileApplicationService.writeFileChunk(
                principal.getName(), request.virtualPath(), request.content(), append, ifMatch));
    }

    // 创建目录
    @PostMapping("/mkdir")
    public FileApiModels.OperationResponse mkdir(@RequestBody FileApiModels.PathOperationRequest request,
                                                 @RequestHeader(IF_MATCH_HEADER) String ifMatch,
                                                 Principal principal) {
        fileApplicationService.mkdir(principal.getName(), request.virtualPath(), ifMatch);
        return new FileApiModels.OperationResponse("mkdir", request.virtualPath(), "success");
    }

    // 重命名文件或目录
    @PostMapping("/rename")
    public FileApiModels.FileEntryResponse rename(@RequestBody FileApiModels.RenameRequest request,
                                                  @RequestHeader(IF_MATCH_HEADER) String ifMatch,
                                                  Principal principal) {
        return FileApiModels.FileEntryResponse.from(
                fileApplicationService.rename(principal.getName(), request.virtualPath(), request.targetName(), ifMatch));
    }

    // 移动文件或目录
    @PostMapping("/move")
    public FileApiModels.FileEntryResponse move(@RequestBody FileApiModels.MoveRequest request,
                                                @RequestHeader(IF_MATCH_HEADER) String ifMatch,
                                                Principal principal) {
        return FileApiModels.FileEntryResponse.from(
                fileApplicationService.move(principal.getName(), request.fromVirtualPath(), request.toVirtualPath(), ifMatch));
    }

    // 复制文件或目录
    @PostMapping("/copy")
    public FileApiModels.FileEntryResponse copy(@RequestBody FileApiModels.MoveRequest request,
                                                @RequestHeader(IF_MATCH_HEADER) String ifMatch,
                                                Principal principal) {
        return FileApiModels.FileEntryResponse.from(
                fileApplicationService.copy(principal.getName(), request.fromVirtualPath(), request.toVirtualPath(), ifMatch));
    }

    // 解析符号链接
    @PostMapping("/symlink/resolve")
    public FileApiModels.FileEntryResponse symlinkResolve(@RequestBody FileApiModels.PathOperationRequest request, Principal principal) {
        return FileApiModels.FileEntryResponse.from(
                fileApplicationService.symlinkResolve(principal.getName(), request.virtualPath()));
    }

    // 删除文件或目录
    @DeleteMapping
    public FileApiModels.OperationResponse delete(@RequestParam String virtualPath,
                                                  @RequestHeader(IF_MATCH_HEADER) String ifMatch,
                                                  Principal principal) {
        fileApplicationService.delete(principal.getName(), virtualPath, ifMatch);
        return new FileApiModels.OperationResponse("delete", virtualPath, "success");
    }
}




