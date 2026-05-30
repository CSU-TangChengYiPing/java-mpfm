package com.mpfm.backend.application.audit;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * DEBUG 日志解析器，负责将原始日志行映射为结构化事件。
 */
@Component
public class DebugLogLineParser {
    private static final Pattern LEVEL_PATTERN = Pattern.compile("\\s(INFO|WARN|ERROR|DEBUG)\\s");
    private static final Pattern PATH_PATTERN = Pattern.compile("path=([^\\s]+)");
    private static final Pattern STATUS_PATTERN = Pattern.compile("status=(\\d{3})");
    private static final Pattern COST_PATTERN = Pattern.compile("costMs=(\\d+)");
    private static final Pattern TRACE_PATTERN = Pattern.compile("\\[([^,\\]]*),([^\\]]*)\\]");
    private static final Pattern SERVICE_PATTERN = Pattern.compile("---\\s*\\[([^\\]]+)\\]");

    /**
     * 解析日志行并返回结构化对象。
     *
     * @param line 原始日志文本。
     * @return 结构化事件。
     */
    public DebugLogStreamService.DebugLogEvent parse(String line) {
        String level = extractGroup(LEVEL_PATTERN.matcher(line), 1, "UNKNOWN");
        String category = resolveCategory(line);
        String path = extractGroup(PATH_PATTERN.matcher(line), 1, null);
        Integer status = parseInteger(extractGroup(STATUS_PATTERN.matcher(line), 1, null));
        Integer costMs = parseInteger(extractGroup(COST_PATTERN.matcher(line), 1, null));
        Matcher traceMatcher = TRACE_PATTERN.matcher(line);
        String traceId = null;
        String requestId = null;
        if (traceMatcher.find()) {
            traceId = blankToNull(traceMatcher.group(1));
            requestId = blankToNull(traceMatcher.group(2));
        }
        String time = extractIsoTime(line);
        String service = extractGroup(SERVICE_PATTERN.matcher(line), 1, null);
        return new DebugLogStreamService.DebugLogEvent(time, level, category, path, status, costMs, traceId, requestId, service, line);
    }

    private String extractIsoTime(String line) {
        int end = line.indexOf(' ');
        if (end <= 0) {
            return null;
        }
        String candidate = line.substring(0, end);
        if (candidate.contains("T") && candidate.endsWith("Z")) {
            return candidate;
        }
        return null;
    }

    private String resolveCategory(String line) {
        if (line.contains("ACCESS :")) {
            return "ACCESS";
        }
        if (line.contains("SECURITY :")) {
            return "SECURITY";
        }
        if (line.contains("application.driver") || line.contains("SftpDriver")) {
            return "DRIVER";
        }
        return "FRAMEWORK";
    }

    private Integer parseInteger(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractGroup(Matcher matcher, int groupIndex, String fallback) {
        if (!matcher.find()) {
            return fallback;
        }
        return matcher.group(groupIndex);
    }

    private String blankToNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw;
    }

    /**
     * 关键字匹配判断，统一使用不区分大小写匹配。
     *
     * @param event   结构化日志事件。
     * @param keyword 搜索关键字。
     * @return 匹配结果。
     */
    public boolean containsKeyword(DebugLogStreamService.DebugLogEvent event, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return event.message().toLowerCase(Locale.ROOT).contains(normalized);
    }
}
