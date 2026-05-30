package com.mpfm.backend.application.audit;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * DEBUG 日志流请求校验器，负责参数合法性校验与默认值补齐。
 */
@Component
public class DebugLogStreamRequestValidator {
    public static final int DEFAULT_TAIL_LINES = 200;
    public static final int MAX_TAIL_LINES = 2000;
    private static final Set<String> ALLOWED_LEVELS = Set.of("INFO", "WARN", "ERROR", "DEBUG");
    private static final Set<String> ALLOWED_CATEGORIES = Set.of("ACCESS", "SECURITY", "DRIVER", "FRAMEWORK");

    /**
     * 校验并规范化请求参数。
     *
     * @param request 原始请求参数。
     * @return 规范化请求参数。
     */
    public DebugLogStreamService.DebugLogStreamRequest validateAndNormalize(
            DebugLogStreamService.DebugLogStreamRequest request) {
        String level = normalizeUpper(request.level());
        String category = normalizeUpper(request.category());
        Integer tailLines = request.tailLines() == null ? DEFAULT_TAIL_LINES : request.tailLines();

        if (level != null && !ALLOWED_LEVELS.contains(level)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid level");
        }
        if (category != null && !ALLOWED_CATEGORIES.contains(category)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid category");
        }
        if (tailLines < 1 || tailLines > MAX_TAIL_LINES) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "tailLines out of range");
        }
        return new DebugLogStreamService.DebugLogStreamRequest(level, category, request.keyword(), tailLines, request.file());
    }

    private String normalizeUpper(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.toUpperCase(Locale.ROOT);
    }
}
