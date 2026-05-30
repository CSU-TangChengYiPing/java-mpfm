package com.mpfm.backend.adapter.api.admin;

import com.mpfm.backend.application.audit.BackendLogReadService;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.nio.file.Path;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端日志控制器，向 root 提供后端应用日志尾部读取能力。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminLogsController {
    private static final int DEFAULT_MAX_LINES = 400;
    private static final int MAX_ALLOWED_LINES = 2000;
    private final BackendLogReadService backendLogReadService;

    public AdminLogsController(BackendLogReadService backendLogReadService) {
        this.backendLogReadService = backendLogReadService;
    }

    /**
     * 读取应用日志尾部。
     *
     * @param maxLines 读取的最大行数，范围 1~2000。
     * @return 日志文件名、行数与内容。
     */
    @GetMapping("/logs")
    @PreAuthorize("hasRole('ROOT')")
    public BackendLogsResponse logs(@RequestParam(name = "max_lines", required = false) Integer maxLines,
                                    @RequestParam(name = "file", required = false) String file) {
        int resolved = maxLines == null ? DEFAULT_MAX_LINES : maxLines;
        if (resolved < 1 || resolved > MAX_ALLOWED_LINES) {
            throw new IllegalArgumentException("max_lines out of range");
        }
        Path logPath = backendLogReadService.resolveLogPath(file)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid log file"));
        if (!backendLogReadService.logExists(logPath)) {
            throw new BusinessException(ErrorCode.DEBUG_STREAM_SOURCE_UNAVAILABLE, "log file not found");
        }
        List<String> lines = backendLogReadService.readTailLines(logPath, resolved);
        return new BackendLogsResponse(
                logPath.toString(),
                resolved,
                lines,
                String.join("\n", lines)
        );
    }

    @GetMapping("/logs/files")
    @PreAuthorize("hasRole('ROOT')")
    public LogFilesResponse logFiles() {
        return new LogFilesResponse(backendLogReadService.activeLogFileName(), backendLogReadService.listLogFiles());
    }

    /** root 日志读取响应模型，兼容前端调试页字段约定。 */
    public record BackendLogsResponse(String log_file, int max_lines, List<String> lines, String content) {
    }

    /** 可选日志文件列表响应模型。 */
    public record LogFilesResponse(String active, List<String> files) {
    }
}

