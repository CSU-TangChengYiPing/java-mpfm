export type LogLevel = "debug" | "info" | "warn" | "error" | "unknown";
export type LogCategory = "ACCESS" | "SECURITY" | "DRIVER" | "FRAMEWORK" | "UNKNOWN";

export type StreamLogEvent = {
  time?: string;
  level?: string;
  category?: string;
  service?: string;
  path?: string;
  status?: number;
  costMs?: number;
  traceId?: string;
  requestId?: string;
  message?: string;
};

export type ParsedLogLine = {
  timestamp: string;
  shortTime: string;
  level: LogLevel;
  levelText: string;
  levelShort: string;
  category: LogCategory;
  source: string;
  service: string;
  message: string;
  raw: string;
  method: string;
  path: string;
  statusText: string;
  costText: string;
  traceId: string;
  requestId: string;
};

/** 多行续行上下文，供日志合并规则使用。 */
export type ContinuationContext = {
  sqlBlockHeader: string;
};

/** 统一级别解析：兼容后端结构化字段与原始文本兜底。 */
export function parseLogLevel(value: string): LogLevel {
  const normalized = value.trim().toLowerCase();
  if (normalized === "debug") return "debug";
  if (normalized === "info") return "info";
  if (normalized === "warn" || normalized === "warning") return "warn";
  if (normalized === "error") return "error";
  return "unknown";
}

/** 统一分类解析：按后端 category 优先，文本模式兜底。 */
export function parseCategory(raw: string, category?: string): LogCategory {
  const direct = (category ?? "").toUpperCase();
  if (direct === "ACCESS" || direct === "SECURITY" || direct === "DRIVER" || direct === "FRAMEWORK") {
    return direct;
  }
  if (raw.includes("ACCESS :")) return "ACCESS";
  if (raw.includes("SECURITY :")) return "SECURITY";
  if (raw.includes("application.driver") || raw.includes("SftpDriver")) return "DRIVER";
  if (raw.length > 0) return "FRAMEWORK";
  return "UNKNOWN";
}

function toShortTime(iso: string): string {
  const matched = iso.match(/T(\d{2}:\d{2}:\d{2}(?:\.\d{3})?)/);
  return matched?.[1] ?? "";
}

function toLevelShort(level: LogLevel): string {
  if (level === "info") return "INF";
  if (level === "warn") return "WRN";
  if (level === "error") return "ERR";
  if (level === "debug") return "DBG";
  return "UNK";
}

function parseCommonFields(raw: string): { method: string; path: string; statusText: string; costText: string; traceId: string; requestId: string } {
  const methodMatch = raw.match(/\bmethod=([A-Z]+)/);
  const pathMatch = raw.match(/\bpath=([^\s]+)/);
  const statusMatch = raw.match(/\bstatus=(\d{3})/);
  const costMatch = raw.match(/\bcostMs=(\d+)/);
  const traceMatch = raw.match(/\[([^,\]]*),([^\]]*)\]/);
  return {
    method: methodMatch?.[1] ?? "",
    path: pathMatch?.[1] ?? "",
    statusText: statusMatch?.[1] ?? "",
    costText: costMatch?.[1] ?? "",
    traceId: traceMatch?.[1] ?? "",
    requestId: traceMatch?.[2] ?? "",
  };
}

function decodeUrlFragments(value: string): string {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function normalizeMessage(raw: string): string {
  const marker = raw.indexOf(" : ");
  const base = marker >= 0 ? raw.slice(marker + 3) : raw;
  const noTail = base
    .replace(/\s+query=.*$/i, "")
    .replace(/\s+body=.*$/i, "")
    .replace(/\s+trace(Id)?=.*$/i, "")
    .trim();
  return decodeUrlFragments(noTail);
}

/** 从 SSE 日志事件归一化为前端展示模型。 */
export function parseStructuredLog(event: StreamLogEvent): ParsedLogLine {
  const raw = event.message ?? "";
  const levelText = (event.level ?? "UNKNOWN").toLowerCase();
  const level = parseLogLevel(levelText);
  const category = parseCategory(raw, event.category);
  const common = parseCommonFields(raw);
  return {
    timestamp: event.time ?? "",
    shortTime: toShortTime(event.time ?? ""),
    level,
    levelText,
    levelShort: toLevelShort(level),
    category,
    source: category,
    service: event.service ?? "",
    message: normalizeMessage(raw),
    raw,
    method: common.method,
    path: event.path ?? common.path,
    statusText: typeof event.status === "number" ? String(event.status) : common.statusText,
    costText: typeof event.costMs === "number" ? String(event.costMs) : common.costText,
    traceId: event.traceId ?? common.traceId,
    requestId: event.requestId ?? common.requestId,
  };
}

/** 旧文本日志兜底解析（兼容历史接口）。 */
export function parseLegacyLogLine(line: string): ParsedLogLine {
  const levelMatch = line.match(/\s(INFO|WARN|ERROR|DEBUG)\s/i);
  const common = parseCommonFields(line);
  const timestamp = line.split(" ")[0] ?? "";
  const levelText = (levelMatch?.[1] ?? "UNKNOWN").toLowerCase();
  const level = parseLogLevel(levelText);
  const category = parseCategory(line);
  return {
    timestamp: timestamp.includes("T") ? timestamp : "",
    shortTime: toShortTime(timestamp),
    level,
    levelText,
    levelShort: toLevelShort(level),
    category,
    source: category,
    service: "",
    message: normalizeMessage(line),
    raw: line,
    method: common.method,
    path: common.path,
    statusText: common.statusText,
    costText: common.costText,
    traceId: common.traceId,
    requestId: common.requestId,
  };
}

function hasTimestampPrefix(line: ParsedLogLine): boolean {
  return line.timestamp.length > 0;
}

function isUnknownContinuation(line: ParsedLogLine): boolean {
  return !hasTimestampPrefix(line) && line.level === "unknown";
}

function isSqlContinuationFragment(line: ParsedLogLine): boolean {
  if (hasTimestampPrefix(line)) return false;
  const content = `${line.message}\n${line.raw}`.toLowerCase();
  return /\b(select|insert|update|delete|from|where|join|values|set)\b/.test(content);
}

/**
 * 续行归并判定：
 * 1) 传统无时间戳 unknown 行继续拼接到上一行；
 * 2) SQL 孤立碎片行可自成一个 SQL 块，供上层补头展示。
 */
export function resolveContinuationAction(
  line: ParsedLogLine,
  hasPrevious: boolean,
): "appendPrevious" | "startSqlBlock" | "none" {
  if (!hasPrevious && isSqlContinuationFragment(line)) {
    return "startSqlBlock";
  }
  if (isUnknownContinuation(line)) {
    return hasPrevious ? "appendPrevious" : "none";
  }
  if (isSqlContinuationFragment(line)) {
    return hasPrevious ? "appendPrevious" : "startSqlBlock";
  }
  return "none";
}

/** 生成 SQL 孤立块头。 */
export function buildSqlBlockHeader(context: ContinuationContext): string {
  return context.sqlBlockHeader;
}
