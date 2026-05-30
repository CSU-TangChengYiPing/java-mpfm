package com.mpfm.backend.adapter.api.file;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * v5 文件下载控制器：复用 v1 下载主链路的 Range/If-Range 语义，避免双轨下载实现漂移。
 */
@RestController
@RequestMapping("/api/v5/files")
public class FileQueryV5Controller {

    private final FileQueryController fileQueryController;

    public FileQueryV5Controller(FileQueryController fileQueryController) {
        this.fileQueryController = fileQueryController;
    }

    /**
     * v5 单文件下载入口：纯 Range 协议，不引入后端下载会话。
     */
    @GetMapping("/content")
    public ResponseEntity<StreamingResponseBody> content(@RequestParam String virtualPath,
                                                         HttpServletRequest request,
                                                         Principal principal) {
        return fileQueryController.download(virtualPath, request, principal);
    }
}
