package com.mpfm.backend.application.file;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 本地挂载的统一文件访问应用服务。
 */
@Service
public class FileApplicationService {

    private final FileQueryService queryService;
    private final FileCommandService commandService;
    private final NamespaceResolver namespaceResolver;

    public FileApplicationService(FileQueryService queryService,
                                  FileCommandService commandService,
                                  NamespaceResolver namespaceResolver) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.namespaceResolver = namespaceResolver;
    }

    public List<EntryResult> tree(String username, String virtualPath) {
        var resolved = namespaceResolver.resolve(username, virtualPath, false, true);
        return queryService.tree(username, resolved.mount().getId(), resolved.relPath());
    }

    public List<EntryResult> list(String username, String virtualPath) {
        var resolved = namespaceResolver.resolve(username, virtualPath, false, true);
        return queryService.list(username, resolved.mount().getId(), resolved.relPath());
    }

    public EntryResult stat(String username, String virtualPath) {
        var resolved = namespaceResolver.resolve(username, virtualPath, false, true);
        return queryService.stat(username, resolved.mount().getId(), resolved.relPath());
    }

    public EntryResult writeFile(String username, String virtualPath, String content, String ifMatch) {
        var resolved = namespaceResolver.resolve(username, virtualPath, true, true);
        return commandService.writeFile(username, resolved.mount().getId(), resolved.relPath(), content, ifMatch);
    }

    public EntryResult writeFileBytes(String username, String virtualPath, byte[] content, String ifMatch) {
        var resolved = namespaceResolver.resolve(username, virtualPath, true, true);
        return commandService.writeFileBytes(username, resolved.mount().getId(), resolved.relPath(), content, ifMatch);
    }

    public EntryResult writeFileChunk(String username, String virtualPath, String content, boolean append, String ifMatch) {
        var resolved = namespaceResolver.resolve(username, virtualPath, true, true);
        return commandService.writeFileChunk(username, resolved.mount().getId(), resolved.relPath(), content, append, ifMatch);
    }

    public String readFile(String username, String virtualPath) {
        var resolved = namespaceResolver.resolve(username, virtualPath, false, true);
        return queryService.readFile(username, resolved.mount().getId(), resolved.relPath());
    }

    public byte[] readFileBytes(String username, String virtualPath) {
        var resolved = namespaceResolver.resolve(username, virtualPath, false, true);
        return queryService.readFileBytes(username, resolved.mount().getId(), resolved.relPath());
    }

    public void mkdir(String username, String virtualPath, String ifMatch) {
        var resolved = namespaceResolver.resolve(username, virtualPath, true, true);
        commandService.mkdir(username, resolved.mount().getId(), resolved.relPath(), ifMatch);
    }

    public EntryResult rename(String username, String virtualPath, String toName, String ifMatch) {
        var resolved = namespaceResolver.resolve(username, virtualPath, true, true);
        return commandService.rename(username, resolved.mount().getId(), resolved.relPath(), toName, ifMatch);
    }

    public EntryResult move(String username, String fromVirtualPath, String toVirtualPath, String ifMatch) {
        var from = namespaceResolver.resolve(username, fromVirtualPath, true, true);
        var to = namespaceResolver.resolve(username, toVirtualPath, true, true);
        if (!from.mount().getId().equals(to.mount().getId())) {
            throw new com.mpfm.backend.common.error.BusinessException(
                    com.mpfm.backend.common.error.ErrorCode.CAPABILITY_NOT_SUPPORTED, "cross-mount move not supported");
        }
        return commandService.move(username, from.mount().getId(), from.relPath(), to.relPath(), ifMatch);
    }

    public EntryResult copy(String username, String fromVirtualPath, String toVirtualPath, String ifMatch) {
        var from = namespaceResolver.resolve(username, fromVirtualPath, false, true);
        var to = namespaceResolver.resolve(username, toVirtualPath, true, true);
        if (!from.mount().getId().equals(to.mount().getId())) {
            throw new com.mpfm.backend.common.error.BusinessException(
                    com.mpfm.backend.common.error.ErrorCode.CAPABILITY_NOT_SUPPORTED, "cross-mount copy not supported");
        }
        return commandService.copy(username, from.mount().getId(), from.relPath(), to.relPath(), ifMatch);
    }

    public EntryResult symlinkResolve(String username, String virtualPath) {
        var resolved = namespaceResolver.resolve(username, virtualPath, false, true);
        return queryService.symlinkResolve(username, resolved.mount().getId(), resolved.relPath());
    }

    public void delete(String username, String virtualPath, String ifMatch) {
        var resolved = namespaceResolver.resolve(username, virtualPath, true, true);
        commandService.delete(username, resolved.mount().getId(), resolved.relPath(), ifMatch);
    }

    /** 文件条目结果模型，承载统一 FileEntry 字段与并发控制元信息。 */
    public record EntryResult(String path, String name, String type, long sizeBytes, String mtime,
                              Long linkCount, boolean visible, boolean readable, boolean writable,
                              String etag, String version) { }
}




