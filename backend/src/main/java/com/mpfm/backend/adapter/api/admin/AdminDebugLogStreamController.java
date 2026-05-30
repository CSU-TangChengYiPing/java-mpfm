package com.mpfm.backend.adapter.api.admin;

import com.mpfm.backend.application.audit.DebugLogStreamService;
import com.mpfm.backend.common.audit.SecurityEventLogger;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * DEBUG 日志流控制器，向 ROOT 用户提供后端日志 SSE 实时推送能力。
 */
@RestController
@RequestMapping("/api/v1/debug/logs")
public class AdminDebugLogStreamController {
    private final DebugLogStreamService debugLogStreamService;
    private final SecurityEventLogger securityEventLogger;

    public AdminDebugLogStreamController(DebugLogStreamService debugLogStreamService,
                                         SecurityEventLogger securityEventLogger) {
        this.debugLogStreamService = debugLogStreamService;
        this.securityEventLogger = securityEventLogger;
    }

    /**
     * 建立 DEBUG 日志流订阅。
     *
     * @param level    日志级别过滤。
     * @param category 日志类别过滤。
     * @param keyword  关键字过滤。
     * @param tailLines 首次回放行数。
     * @param principal 登录用户主体。
     * @return SSE 发射器。
     */
    @GetMapping("/stream")
    @PreAuthorize("hasRole('ROOT')")
    public SseEmitter stream(@RequestParam(required = false) String level,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) Integer tailLines,
                             @RequestParam(required = false) String file,
                             Principal principal) {
        DebugLogStreamService.DebugLogStreamRequest request =
                new DebugLogStreamService.DebugLogStreamRequest(level, category, keyword, tailLines, file);
        return debugLogStreamService.subscribe(request, principal.getName());
    }

    /**
     * 记录 DEBUG 日志复制行为审计，不包含日志正文。
     *
     * @param request   复制上下文。
     * @param principal 当前操作用户。
     * @return 审计结果。
     */
    @PostMapping("/copy-audit")
    @PreAuthorize("hasRole('ROOT')")
    public CopyAuditResponse recordCopyAudit(@RequestBody(required = false) CopyAuditRequest request,
                                             Principal principal) {
        String target = "backend_logs";
        if (request != null && request.visibleLines() != null) {
            target = target + "#visibleLines=" + request.visibleLines();
        }
        securityEventLogger.managementOperation(
                new SecurityEventLogger.ManagementAuditEvent("debug_log_copy", target, "success", null));
        return new CopyAuditResponse("success", principal.getName());
    }

    /** 复制审计请求模型，仅记录可见行数，不记录正文。 */
    public record CopyAuditRequest(Integer visibleLines) {
    }

    /** 复制审计响应模型。 */
    public record CopyAuditResponse(String status, String operator) {
    }
}
