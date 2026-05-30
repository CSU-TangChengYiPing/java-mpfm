import { Button } from "@heroui/button";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { LargeGlassInput } from "../../../components/common/LargeGlassField";
import { Tab, Tabs } from "@heroui/tabs";
import { Select, SelectItem } from "@heroui/select";
import clsx from "clsx";
import { FiChevronDown, FiChevronUp, FiCopy, FiFilter, FiPause, FiPlay, FiRefreshCw, FiSearch, FiSlash, FiTrash2 } from "react-icons/fi";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import { Virtuoso } from "react-virtuoso";
import ShadowTooltip from "../../../components/common/ShadowTooltip";
import SystemController from "../../../controllers/system";
import { useAuth } from "../../../hooks/useAuth";
import { usePersistentState } from "../../../hooks/usePersistentState";
import key from "../../../const/key";
import { withAuthHeader } from "../../../utils/authFetch";
import {
  buildSqlBlockHeader,
  parseLegacyLogLine,
  parseStructuredLog,
  resolveContinuationAction,
  type LogCategory,
  type LogLevel,
  type ParsedLogLine,
  type StreamLogEvent,
} from "./debugLogParser";

type StreamState = "connecting" | "open" | "retrying" | "unauthorized" | "closed";

const ALL_LEVELS: LogLevel[] = ["debug", "info", "warn", "error"];
const ALL_CATEGORIES: LogCategory[] = ["ACCESS", "SECURITY", "DRIVER", "FRAMEWORK"];
const MAX_BUFFER = 2000;
const MAX_RENDER_ROWS = 400;
const CONNECT_TIMEOUT_BASE_MS = 8000;
const CONNECT_TIMEOUT_MAX_MS = 45000;
type PreparedLogLine = ParsedLogLine & { rowKey: string; searchBlobLower: string };
type LogRenderItem = PreparedLogLine & { repeatCount: number };

let globalLogSeq = 0;
let globalLogsStore: PreparedLogLine[] = [];
let globalStreamStateStore: StreamState = "closed";
let globalRetryCountStore = 0;
let globalStreamErrorStore = "";
let globalStreamStarted = false;
let globalStreamAbort: AbortController | null = null;
let globalRetryTimer: number | null = null;
let globalFlushTimer: number | null = null;
let globalPendingLogs: PreparedLogLine[] = [];
let globalReconnecting = false;
let globalAllowAutoReconnect = true;
let globalStreamEpoch = 0;

function resolveConnectTimeoutMs(retryCount: number): number {
  const factor = Math.pow(2, Math.min(Math.max(retryCount, 0), 3));
  return Math.min(CONNECT_TIMEOUT_MAX_MS, CONNECT_TIMEOUT_BASE_MS * factor);
}

function UploadLikeToast({ name, loaded, total }: { name: string; loaded: number; total: number }) {
  const { t } = useTranslation();
  const percent = total > 0 ? Math.max(0, Math.min(100, Math.round((loaded / total) * 100))) : 0;
  return (
    <div className="relative w-full overflow-hidden rounded-md bg-background px-3 py-2 text-sm text-foreground shadow-md">
      <div className="truncate text-default-700 dark:text-default-200">{t("debug.uploading", { name, current: 1, total: 1 })}</div>
      <div className="mt-1 text-xs text-default-500">{loaded} / {total}</div>
      <div className="absolute bottom-0 left-0 h-0.5 bg-primary transition-all duration-150" style={{ width: `${percent}%` }} />
    </div>
  );
}

function maskSensitive(text: string): string {
  return text
    .replace(/("?(password|token|secret|authorization|sessionId)"?\s*[:=]\s*"?)([^",\s}]+)/gi, "$1<redacted>")
    .replace(/(Bearer\s+)[^\s]+/gi, "$1<redacted>");
}

function toPreparedLogLine(line: ParsedLogLine): PreparedLogLine {
  const rowKey = `${line.timestamp || "no-ts"}-${globalLogSeq++}`;
  return {
    ...line,
    rowKey,
    searchBlobLower: `${line.message}\n${line.raw}`.toLowerCase(),
  };
}

function isDuplicateByTimestampAndBody(existing: PreparedLogLine[], incoming: PreparedLogLine): boolean {
  if (incoming.timestamp.length === 0) return false;
  return existing.some((item) => item.timestamp === incoming.timestamp && item.message === incoming.message);
}

type LogLineRowProps = {
  item: LogRenderItem;
  expanded: boolean;
  onToggle: (rowKey: string) => void;
};

const LogLineRow = memo(function LogLineRow({ item, expanded, onToggle }: LogLineRowProps) {
  const levelColor = item.level === "debug"
    ? "text-default-500"
    : item.level === "info"
      ? "text-primary"
      : item.level === "warn"
        ? "text-warning"
        : item.level === "error"
          ? "text-danger"
          : "text-default-500";
  const statusColor = item.statusText.startsWith("5")
    ? "text-danger"
    : item.statusText.startsWith("4")
      ? "text-warning"
      : "text-default-500";
  const multiline = item.message.includes("\n") || item.raw.includes("\n");
  const messageLines = maskSensitive(item.message).split("\n");
  const summaryLine = messageLines[0] ?? "";
  const continuationLines = messageLines.length > 1 ? messageLines.slice(1) : [];
  const expandedLines = [summaryLine, ...continuationLines];
  const expandable = multiline || summaryLine.length > 0;

  return (
    <div className="space-y-0.5">
      <div className={clsx("flex w-full items-center gap-3 whitespace-nowrap rounded-sm", expandable ? "bg-transparent hover:bg-default-200/70 dark:hover:bg-white/10" : "")}>
        <span className="inline-flex h-5 w-3 shrink-0 items-center justify-start">
          {item.repeatCount > 1 ? (
            <span className="rounded-sm bg-default-100 px-1.5 py-0.5 text-[11px] leading-none text-default-500">
              x{item.repeatCount}
            </span>
          ) : null}
        </span>
        <span className={clsx("inline-block h-4 w-0.5 shrink-0 rounded-sm", levelColor.replace("text-", "bg-"))} />
        <span className="w-21 shrink-0 text-[13px] text-default-400">{item.shortTime || "--:--:--"}</span>
        <span className={clsx("w-5 shrink-0 text-[13px] font-semibold", levelColor)}>{item.levelShort}</span>
        <span className={clsx("w-5 shrink-0 text-[12px] font-semibold", statusColor)}>{item.statusText || "---"}</span>
        <span className="w-22 shrink-0 overflow-hidden text-ellipsis text-[12px] text-default-500">{item.service || "-"}</span>
        <span className="min-w-0 flex-1 overflow-hidden text-ellipsis text-[13px] text-default-700">{summaryLine}</span>
        <span className="inline-flex h-6 w-6 shrink-0 items-center justify-center">
          {expandable ? (
            <button
              type="button"
              className="inline-flex h-5 w-5 items-center justify-center rounded-sm text-default-400 transition-colors hover:bg-default-200/70 hover:text-default-600 dark:hover:bg-white/10"
              onClick={() => onToggle(item.rowKey)}
              aria-label={expanded ? "collapse" : "expand"}
            >
              {expanded ? <FiChevronUp size={14} /> : <FiChevronDown size={14} />}
            </button>
          ) : null}
        </span>
      </div>
      {expanded && expandable ? (
        <div className="space-y-1 rounded-md bg-default-200/60 px-2 py-1 dark:bg-white/5">
          {expandedLines.map((line, lineIndex) => (
            <div key={`${item.rowKey}-line-${lineIndex}`} className="flex items-start gap-3 text-[12px] leading-5 text-default-600">
              <span className="inline-flex h-5 w-10 shrink-0 items-center justify-start">
                {item.repeatCount > 1 ? <span className="rounded-sm px-1.5 py-0.5 text-[11px] leading-none text-transparent">x{item.repeatCount}</span> : null}
              </span>
              <span className="inline-block h-4 w-0.5 shrink-0 rounded-sm bg-transparent" />
              <span className="w-21 shrink-0 text-[12px] text-default-300" />
              <span className="w-5 shrink-0 text-[12px] text-default-300" />
              <span className="w-5 shrink-0 text-[12px] text-default-300" />
              <span className="w-22 shrink-0 text-[12px] text-default-300" />
              <span className="min-w-0 flex-1 whitespace-pre-wrap break-all font-mono text-[12px] text-default-600">
                {"\t"}{line}
              </span>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
});

/** 调试页：toast 模拟 + DEBUG 日志 SSE 实时查看 + 系统监控。 */
export default function DebugPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [debugTab, setDebugTab] = usePersistentState(key.debugTab, "toast");
  const [toastMsg, setToastMsg] = useState("");
  const [toastProgress, setToastProgress] = useState(33);
  const [searchText, setSearchText] = useState("");
  const [selectedLevels, setSelectedLevels] = useState<Set<LogLevel>>(new Set(ALL_LEVELS));
  const [selectedCategories, setSelectedCategories] = useState<Set<LogCategory>>(new Set(ALL_CATEGORIES));
  const [streamState, setStreamState] = useState<StreamState>("closed");
  const [retryCount, setRetryCount] = useState(0);
  const [streamError, setStreamError] = useState("");
  const [autoFollow, setAutoFollow] = useState(true);
  const [logs, setLogs] = useState<PreparedLogLine[]>([]);
  const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());
  const [dedupeEnabled, setDedupeEnabled] = useState(true);
  const [monitorError, setMonitorError] = useState("");
  const [monitorLoading, setMonitorLoading] = useState(false);
  const [monitorSnapshot, setMonitorSnapshot] = useState<Awaited<ReturnType<typeof SystemController.adminSystemOverview>> | null>(null);
  const [searchFocused, setSearchFocused] = useState(false);
  const reconnectRef = useRef<(() => void) | null>(null);
  const isMountedRef = useRef(false);
  const hasConnectedLogsTabRef = useRef(false);
  const skipNextAtBottomSyncRef = useRef(false);

  const normalizedKeyword = searchText.trim().toLowerCase();
  const visibleLines = useMemo(() => {
    const filtered = logs.filter((item) => {
      const levelOk = selectedLevels.size === 0 || selectedLevels.has(item.level);
      const categoryOk = selectedCategories.size === 0 || selectedCategories.has(item.category);
      const searchOk = normalizedKeyword.length === 0
        ? true
        : item.searchBlobLower.includes(normalizedKeyword);
      return levelOk && categoryOk && searchOk;
    });
    if (!dedupeEnabled || filtered.length === 0) {
      return filtered.map((item) => ({ ...item, repeatCount: 1 })) as LogRenderItem[];
    }
    const grouped: LogRenderItem[] = [];
    for (const item of filtered) {
      const latest = grouped[grouped.length - 1];
      if (latest && latest.level === item.level && latest.path === item.path && latest.statusText === item.statusText && latest.message === item.message) {
        latest.repeatCount += 1;
        continue;
      }
      grouped.push({ ...item, repeatCount: 1 });
    }
    return grouped;
  }, [dedupeEnabled, logs, normalizedKeyword, selectedLevels, selectedCategories]);
  const renderedLines = useMemo(() => {
    if (visibleLines.length <= MAX_RENDER_ROWS) return visibleLines;
    return visibleLines.slice(visibleLines.length - MAX_RENDER_ROWS);
  }, [visibleLines]);
  const streamLampClass = useMemo(() => {
    if (streamError.length > 0) return "bg-danger";
    if (streamState === "open") return "bg-success";
    if (streamState === "connecting" || streamState === "retrying") return "bg-warning";
    return "bg-default-400";
  }, [streamError.length, streamState]);
  const streamStateDetail = useMemo(() => {
    const stateText = t(`debug.streamState${streamState.charAt(0).toUpperCase()}${streamState.slice(1)}`);
    const retryText = t("debug.streamStateRetryCount", { retry: retryCount });
    const errorText = streamError.length > 0 ? streamError : t("debug.streamStateNoError");
    return `${stateText}\n${retryText}\n${errorText}`;
  }, [retryCount, streamError, streamState, t]);
  const refreshReady = streamState === "open";
  const connectToggleReady = true;

  const loadMonitor = useCallback(async () => {
    if (!user?.is_root) return;
    setMonitorLoading(true);
    setMonitorError("");
    try {
      const current = await SystemController.adminSystemOverview();
      setMonitorSnapshot(current);
    } catch (err) {
      setMonitorError(err instanceof Error ? err.message : t("debug.loadFailed"));
    } finally {
      setMonitorLoading(false);
    }
  }, [t, user?.is_root]);

  const teardownStream = useCallback(() => {
    if (globalStreamAbort) {
      globalStreamAbort.abort();
      globalStreamAbort = null;
    }
    if (globalRetryTimer != null) {
      window.clearTimeout(globalRetryTimer);
      globalRetryTimer = null;
    }
    if (globalFlushTimer != null) {
      window.clearTimeout(globalFlushTimer);
      globalFlushTimer = null;
    }
    globalPendingLogs = [];
    globalReconnecting = false;
  }, []);

  const hardStopAllStreamActivity = useCallback(() => {
    globalAllowAutoReconnect = false;
    globalStreamEpoch += 1;
    teardownStream();
    globalStreamStarted = false;
    globalReconnecting = false;
    globalStreamStateStore = "closed";
    globalStreamErrorStore = "";
    globalRetryCountStore = 0;
    if (isMountedRef.current) {
      setStreamState("closed");
      setStreamError("");
      setRetryCount(0);
    }
  }, [teardownStream]);

  const enqueueLog = useCallback((line: ParsedLogLine) => {
    globalPendingLogs.push(toPreparedLogLine(line));
    if (globalFlushTimer != null) return;
    globalFlushTimer = window.setTimeout(() => {
      const batch = globalPendingLogs;
      globalPendingLogs = [];
      globalFlushTimer = null;
      if (batch.length === 0) return;
      const merged = [...globalLogsStore];
      for (const item of batch) {
        const continuationAction = resolveContinuationAction(item, merged.length > 0);
        if (continuationAction === "appendPrevious" && merged.length > 0) {
          const last = merged[merged.length - 1];
          merged[merged.length - 1] = {
            ...last,
            message: `${last.message}\n${item.message}`,
            raw: `${last.raw}\n${item.raw}`,
            searchBlobLower: `${last.searchBlobLower}\n${item.searchBlobLower}`,
          };
          continue;
        }
        if (continuationAction === "startSqlBlock") {
          const sqlHeader = buildSqlBlockHeader({ sqlBlockHeader: t("debug.sqlBlockHeader") });
          const synthetic = toPreparedLogLine({
            ...item,
            level: "debug",
            levelText: "debug",
            levelShort: "DBG",
            category: "FRAMEWORK",
            source: "FRAMEWORK",
            message: `${sqlHeader}\n${item.message}`,
            raw: `${sqlHeader}\n${item.raw}`,
          });
          if (isDuplicateByTimestampAndBody(merged, synthetic)) {
            continue;
          }
          merged.push(synthetic);
          continue;
        }
        if (isDuplicateByTimestampAndBody(merged, item)) {
          continue;
        }
        merged.push(item);
      }
      globalLogsStore = merged.length > MAX_BUFFER ? merged.slice(merged.length - MAX_BUFFER) : merged;
      if (isMountedRef.current) setLogs(globalLogsStore);
    }, 240);
  }, []);

  const scheduleReconnect = useCallback(() => {
    if (!globalAllowAutoReconnect) return;
    if (globalReconnecting) return;
    globalReconnecting = true;
    globalStreamStarted = false;
    const retryEpoch = globalStreamEpoch;
    globalStreamStateStore = "retrying";
    if (isMountedRef.current) setStreamState("retrying");
    globalRetryCountStore += 1;
    if (isMountedRef.current) setRetryCount(globalRetryCountStore);
    {
      const next = globalRetryCountStore;
      const waitMs = Math.min(15000, Math.max(1000, 1000 * Math.pow(2, Math.min(next - 1, 3))));
      globalRetryTimer = window.setTimeout(() => {
        if (!globalAllowAutoReconnect) {
          globalReconnecting = false;
          return;
        }
        if (retryEpoch !== globalStreamEpoch) {
          globalReconnecting = false;
          return;
        }
        globalStreamEpoch += 1;
        if (globalStreamAbort) {
          globalStreamAbort.abort("retry-restart");
          globalStreamAbort = null;
        }
        globalReconnecting = false;
        globalStreamStarted = false;
        globalStreamErrorStore = "";
        if (isMountedRef.current) setStreamError("");
        if (isMountedRef.current) setStreamState("connecting");
        reconnectRef.current?.();
      }, waitMs);
    }
  }, []);

  const connectStream = useCallback((resetLogs: boolean) => {
    if (!user?.is_root) return;
    if (!globalAllowAutoReconnect) return;
    if (globalStreamStarted && globalStreamAbort == null && !globalReconnecting) {
      globalStreamStarted = false;
    }
    if (globalStreamStarted) {
      if (isMountedRef.current) {
        setLogs(globalLogsStore);
        setStreamState(globalStreamStateStore);
        setRetryCount(globalRetryCountStore);
        setStreamError(globalStreamErrorStore);
      }
      return;
    }
    globalStreamStarted = true;
    teardownStream();
    globalStreamErrorStore = "";
    globalStreamStateStore = "connecting";
    if (isMountedRef.current) {
      setStreamError("");
      setStreamState("connecting");
    }
    if (resetLogs) {
      globalLogsStore = [];
      if (isMountedRef.current) setLogs([]);
    }
    const currentEpoch = globalStreamEpoch;
    const url = SystemController.buildDebugLogStreamUrl({ tailLines: 300 });
    const controller = new AbortController();
    globalStreamAbort = controller;
    void (async () => {
      let connectTimeout: number | null = null;
      const isCurrentEpoch = () => currentEpoch === globalStreamEpoch;
      let receivedAnyLog = false;
      try {
        const connectTimeoutMs = resolveConnectTimeoutMs(globalRetryCountStore);
        connectTimeout = window.setTimeout(() => {
          if (!controller.signal.aborted) controller.abort("connect-timeout");
        }, connectTimeoutMs);
        const resp = await fetch(url, {
          method: "GET",
          headers: withAuthHeader(url, { headers: { Accept: "text/event-stream" } })?.headers,
          signal: controller.signal,
        });
        if (connectTimeout != null) {
          window.clearTimeout(connectTimeout);
        }
        if (currentEpoch !== globalStreamEpoch) {
          controller.abort("stale-epoch");
          return;
        }
        if (resp.status === 401 || resp.status === 403) {
          if (!isCurrentEpoch()) return;
          globalStreamStateStore = "unauthorized";
          globalStreamStarted = false;
          globalStreamErrorStore = t("auth.rootOnlyDebug");
          if (isMountedRef.current) {
            setStreamState("unauthorized");
            setStreamError(t("auth.rootOnlyDebug"));
          }
          return;
        }
        if (!resp.ok || !resp.body) throw new Error(`stream failed: ${resp.status}`);
        if (!isCurrentEpoch()) return;
        if (globalRetryTimer != null) {
          window.clearTimeout(globalRetryTimer);
          globalRetryTimer = null;
        }
        globalStreamStateStore = "open";
        globalRetryCountStore = 0;
        globalReconnecting = false;
        if (isMountedRef.current) {
          setStreamState("open");
          setRetryCount(0);
        }
        const reader = resp.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        while (!controller.signal.aborted) {
          const { done, value } = await reader.read();
          if (done) break;
          if (currentEpoch !== globalStreamEpoch) {
            controller.abort("stale-epoch");
            break;
          }
          buffer += decoder.decode(value, { stream: true });
          const chunks = buffer.split("\n\n");
          buffer = chunks.pop() ?? "";
          for (const rawEvent of chunks) {
            if (!isCurrentEpoch()) {
              controller.abort("stale-epoch");
              break;
            }
            const lines = rawEvent.split("\n");
            let eventName = "message";
            const dataLines: string[] = [];
            for (const line of lines) {
              if (line.startsWith("event:")) eventName = line.slice(6).trim();
              if (line.startsWith("data:")) dataLines.push(line.slice(5).trim());
            }
            if (eventName === "heartbeat") continue;
            const data = dataLines.join("\n");
            if (eventName === "error") {
              if (!isCurrentEpoch()) continue;
              globalStreamErrorStore = t("debug.streamError");
              if (isMountedRef.current) setStreamError(t("debug.streamError"));
              continue;
            }
            try {
              const payload = JSON.parse(data) as StreamLogEvent;
              const parsed = parseStructuredLog(payload);
              if (!isCurrentEpoch()) continue;
              if (!receivedAnyLog) {
                receivedAnyLog = true;
                globalStreamStateStore = "open";
                globalStreamErrorStore = "";
                globalRetryCountStore = 0;
                globalReconnecting = false;
                if (globalRetryTimer != null) {
                  window.clearTimeout(globalRetryTimer);
                  globalRetryTimer = null;
                }
                if (isMountedRef.current) {
                  setStreamState("open");
                  setStreamError("");
                  setRetryCount(0);
                }
              }
              enqueueLog(parsed);
            } catch {
              const legacy = parseLegacyLogLine(data);
              if (!isCurrentEpoch()) continue;
              if (!receivedAnyLog) {
                receivedAnyLog = true;
                globalStreamStateStore = "open";
                globalStreamErrorStore = "";
                globalRetryCountStore = 0;
                globalReconnecting = false;
                if (globalRetryTimer != null) {
                  window.clearTimeout(globalRetryTimer);
                  globalRetryTimer = null;
                }
                if (isMountedRef.current) {
                  setStreamState("open");
                  setStreamError("");
                  setRetryCount(0);
                }
              }
              enqueueLog(legacy);
            }
          }
        }
        if (isCurrentEpoch()) {
          globalStreamStarted = false;
        }
      } catch {
        if (connectTimeout != null) {
          window.clearTimeout(connectTimeout);
        }
        if (!isCurrentEpoch()) return;
        if (!controller.signal.aborted) {
          globalStreamStarted = false;
          globalStreamErrorStore = t("debug.streamError");
          if (isMountedRef.current) setStreamError(t("debug.streamError"));
          scheduleReconnect();
        } else if (String(controller.signal.reason ?? "") === "connect-timeout") {
          if (receivedAnyLog) {
            return;
          }
          globalStreamStarted = false;
          globalStreamErrorStore = t("debug.streamConnectTimeout");
          if (isMountedRef.current) setStreamError(t("debug.streamConnectTimeout"));
          scheduleReconnect();
        }
      }
    })();
  }, [enqueueLog, scheduleReconnect, t, teardownStream, user?.is_root]);

  const forceReconnect = useCallback((resetLogs: boolean, successToastKey?: string) => {
    globalAllowAutoReconnect = true;
    globalStreamEpoch += 1;
    teardownStream();
    globalStreamStarted = false;
    globalReconnecting = false;
    globalStreamStateStore = "closed";
    globalStreamErrorStore = "";
    if (isMountedRef.current) {
      setStreamState("closed");
      setStreamError("");
    }
    connectStream(resetLogs);
    if (successToastKey) {
      toast.success(t(successToastKey));
    }
  }, [connectStream, t, teardownStream]);

  useEffect(() => {
    reconnectRef.current = () => connectStream(false);
  }, [connectStream]);

  useEffect(() => {
    isMountedRef.current = true;
    setLogs(globalLogsStore);
    if (globalStreamStarted) {
      setStreamState(globalStreamStateStore);
      setRetryCount(globalRetryCountStore);
      setStreamError(globalStreamErrorStore);
    }
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (debugTab !== "logs") hasConnectedLogsTabRef.current = false;
    if (debugTab === "logs") skipNextAtBottomSyncRef.current = true;
  }, [debugTab]);

  useEffect(() => {
    if (!user?.is_root) return;
    if (debugTab !== "logs") return;
    if (globalStreamStarted) {
      setStreamState(globalStreamStateStore);
      setRetryCount(globalRetryCountStore);
      setStreamError(globalStreamErrorStore);
    }
    if (hasConnectedLogsTabRef.current) return;
    hasConnectedLogsTabRef.current = true;
    connectStream(false);
  }, [connectStream, debugTab, user?.is_root]);

  const toggleExpandedRow = useCallback((rowKey: string) => {
    setExpandedRows((prev) => {
      const next = new Set(prev);
      if (next.has(rowKey)) next.delete(rowKey);
      else next.add(rowKey);
      return next;
    });
  }, []);

  if (!user?.is_root) {
    return (
      <div className="h-full w-full p-2 md:p-4">
        <Card radius="sm" className={clsx("border border-white/40 bg-white/60 shadow-sm backdrop-blur-xl dark:border-white/10 dark:bg-black/40")}>
          <CardBody className="text-sm text-danger">{t("auth.rootOnlyDebug")}</CardBody>
        </Card>
      </div>
    );
  }

  return (
    <div className="h-full w-full p-2 md:p-4">
      <Card radius="sm" className={clsx("border border-white/40 bg-white/70 shadow-sm dark:border-white/10 dark:bg-black/45")}>
        <CardHeader className="pb-0 text-lg font-semibold">{t("debug.title")}</CardHeader>
        <CardBody className="space-y-3">
          <Tabs
            aria-label={t("debug.tabsAriaLabel")}
            selectedKey={debugTab}
            onSelectionChange={(k) => setDebugTab(String(k))}
          >
            <Tab key="toast" title={t("debug.toastTab")}>
              <div className="space-y-3">
                <LargeGlassInput radius="sm" label={t("debug.message")} value={toastMsg} onValueChange={setToastMsg} commitMode="blur" />
                <LargeGlassInput radius="sm" type="number" label={t("debug.progress")} value={String(toastProgress)} onValueChange={(v) => setToastProgress(Math.max(0, Math.min(100, Number(v) || 0)))} commitMode="blur" />
                <div className="flex flex-wrap gap-2">
                  <Button radius="sm" color="default" variant="flat" onPress={() => toast(toastMsg)}>{t("debug.normal")}</Button>
                  <Button radius="sm" color="success" variant="flat" onPress={() => toast.success(toastMsg)}>{t("debug.success")}</Button>
                  <Button radius="sm" color="danger" variant="flat" onPress={() => toast.error(toastMsg)}>{t("debug.fail")}</Button>
                  <Button radius="sm" color="primary" variant="flat" onPress={() => toast.loading(toastMsg)}>{t("debug.loading")}</Button>
                  <Button
                    radius="sm"
                    color="primary"
                    onPress={() => {
                      const id = toast.custom(<UploadLikeToast name="debug.bin" loaded={0} total={100} />, { duration: Number.POSITIVE_INFINITY });
                      let curr = 0;
                      const timer = window.setInterval(() => {
                        curr += 10;
                        if (curr >= toastProgress) {
                          curr = toastProgress;
                          window.clearInterval(timer);
                        }
                        toast.custom(<UploadLikeToast name="debug.bin" loaded={curr} total={100} />, { id, duration: Number.POSITIVE_INFINITY });
                      }, 120);
                    }}
                  >
                    {t("debug.uploadStyle")}
                  </Button>
                  <Button radius="sm" color="warning" variant="flat" onPress={() => toast.dismiss()}>{t("debug.clearAll")}</Button>
                </div>
              </div>
            </Tab>

            <Tab key="logs" title={t("debug.logsTab")}>
              <div className="relative flex h-[calc(100vh-230px)] min-h-[480px] flex-col gap-1.5 rounded-2xl border border-white/35 bg-white/55 px-2.5 py-1.5 shadow-sm dark:border-white/10 dark:bg-white/10">
                <div className="flex flex-wrap items-end gap-2">
                  <ShadowTooltip content={<div className="max-w-[300px] whitespace-pre-wrap text-xs">{streamStateDetail}</div>}>
                    <span className={clsx("mb-3 inline-block h-2.5 w-2.5 shrink-0 rounded-full", streamLampClass)} />
                  </ShadowTooltip>
                  <div className="min-w-0 flex-[1_1_240px] ml-0">
                    <label className="flex h-8 items-center gap-2 rounded-sm border border-transparent bg-transparent px-2">
                      <FiSearch aria-hidden="true" size={16} className={clsx(searchFocused ? "text-primary" : "text-default-400 dark:text-default-500")} />
                      <input
                        type="text"
                        className="h-full w-full border-none bg-transparent text-sm text-default-700 outline-none placeholder:text-default-400 dark:text-white dark:placeholder:text-white/55"
                        aria-label={t("debug.searchPlaceholder")}
                        placeholder={searchFocused ? "" : t("debug.searchPlaceholder")}
                        value={searchText}
                        onChange={(event) => setSearchText(event.target.value)}
                        onFocus={() => setSearchFocused(true)}
                        onBlur={() => setSearchFocused(false)}
                      />
                    </label>
                  </div>
                  <Tabs
                    selectedKey={selectedLevels.size === ALL_LEVELS.length ? "all" : [...selectedLevels][0]}
                    onSelectionChange={(k) => {
                      const key = String(k) as "all" | LogLevel;
                      if (key === "all") setSelectedLevels(new Set(ALL_LEVELS));
                      else setSelectedLevels(new Set([key]));
                    }}
                    radius="full"
                    size="sm"
                    classNames={{
                      tabList: "min-h-7 h-7",
                      tab: "h-6 min-h-0 px-2 text-xs",
                    }}
                  >
                    <Tab key="all" title={t("debug.levelAll")} />
                    <Tab key="info" title={t("debug.levelInfo")} />
                    <Tab key="warn" title={t("debug.levelWarn")} />
                    <Tab key="error" title={t("debug.levelError")} />
                    <Tab key="debug" title={t("debug.levelDebug")} />
                  </Tabs>
                  <Select
                    size="sm"
                    selectedKeys={new Set([selectedCategories.size === ALL_CATEGORIES.length ? "all" : [...selectedCategories][0]])}
                    onSelectionChange={(keys) => {
                      const first = Array.from(keys)[0];
                      const key = String(first) as "all" | LogCategory;
                      if (key === "all") setSelectedCategories(new Set(ALL_CATEGORIES));
                      else setSelectedCategories(new Set([key]));
                    }}
                    className="w-[180px] flex-none"
                    classNames={{
                      trigger: "h-7 min-h-0 px-2",
                      value: "text-xs",
                    }}
                    aria-label={t("debug.categoryAll")}
                  >
                    <SelectItem key="all">{t("debug.categoryAll")}</SelectItem>
                    <SelectItem key="ACCESS">ACCESS</SelectItem>
                    <SelectItem key="SECURITY">SECURITY</SelectItem>
                    <SelectItem key="DRIVER">DRIVER</SelectItem>
                    <SelectItem key="FRAMEWORK">FRAMEWORK</SelectItem>
                  </Select>
                  <ShadowTooltip content={t("common.refresh")}>
                    <Button
                      size="sm"
                      isIconOnly
                      aria-label={t("common.refresh")}
                      variant="light"
                      isDisabled={!refreshReady}
                      className={clsx("h-7 min-h-0 min-w-0 px-1.5", !refreshReady ? "bg-default-200 text-default-400" : "")}
                      onPress={() => {
                        if (!refreshReady) return;
                        forceReconnect(false, "debug.refreshStart");
                      }}
                    >
                      <FiRefreshCw aria-hidden="true" size={14} />
                    </Button>
                  </ShadowTooltip>
                  <ShadowTooltip content={t("common.copy")}>
                    <Button
                      size="sm"
                      isIconOnly
                      aria-label={t("common.copy")}
                      variant="light"
                      className="h-7 min-h-0 min-w-0 px-1.5"
                      onPress={async () => {
                        const text = renderedLines.map((line) => maskSensitive(line.raw)).join("\n");
                        if (text.length === 0) {
                          toast.error(t("debug.copyEmpty"));
                          return;
                        }
                        await navigator.clipboard.writeText(text);
                        await SystemController.debugCopyAudit(renderedLines.length);
                        toast.success(t("debug.copySuccess"));
                      }}
                    >
                      <FiCopy aria-hidden="true" size={14} />
                    </Button>
                  </ShadowTooltip>
                  <ShadowTooltip content={streamState === "closed" ? t("debug.connect") : t("debug.disconnect")}>
                    <Button
                      size="sm"
                      isIconOnly
                      aria-label={streamState === "closed" ? t("debug.connect") : t("debug.disconnect")}
                      variant="solid"
                      color={streamState === "closed" ? "success" : "danger"}
                      isDisabled={!connectToggleReady}
                      className={clsx("h-7 min-h-0 min-w-0 px-1.5 text-white", !connectToggleReady ? "bg-default-300 text-white/70" : "")}
                      onPress={() => {
                        if (!connectToggleReady) return;
                        if (streamState === "closed") {
                          forceReconnect(false, "debug.connectSuccess");
                          return;
                        }
                        hardStopAllStreamActivity();
                        toast.success(t("debug.disconnectSuccess"));
                      }}
                    >
                      {streamState === "closed" ? <FiPlay aria-hidden="true" size={14} /> : <FiSlash aria-hidden="true" size={14} />}
                    </Button>
                  </ShadowTooltip>
                  <ShadowTooltip content={t("common.clear")}>
                    <Button
                      size="sm"
                      isIconOnly
                      aria-label={t("common.clear")}
                      variant="solid"
                      color="danger"
                      className="h-7 min-h-0 min-w-0 px-1.5 text-white"
                      onPress={() => {
                        globalLogsStore = [];
                        globalPendingLogs = [];
                        setExpandedRows(new Set());
                        setLogs([]);
                        toast.success(t("debug.clearViewSuccess"));
                      }}
                    >
                      <FiTrash2 aria-hidden="true" size={14} />
                    </Button>
                  </ShadowTooltip>
                  <Button
                    size="sm"
                    variant={dedupeEnabled ? "solid" : "light"}
                    color={dedupeEnabled ? "primary" : "default"}
                    className="h-7 min-h-0 px-2"
                    onPress={() => setDedupeEnabled((v) => !v)}
                  >
                    <FiFilter aria-hidden="true" size={14} />
                    {dedupeEnabled ? t("debug.dedupeOn") : t("debug.dedupeOff")}
                  </Button>
                  <div className="ml-auto">
                    <Button
                      size="sm"
                      variant={autoFollow ? "solid" : "light"}
                      color={autoFollow ? "primary" : "default"}
                      className="h-7 min-h-0 px-2"
                      aria-label={autoFollow ? t("debug.autoFollowOn") : t("debug.autoFollowOff")}
                      onPress={() => {
                        setAutoFollow((v) => !v);
                        toast.success(autoFollow ? t("debug.autoFollowOff") : t("debug.autoFollowOn"));
                      }}
                    >
                      {autoFollow ? <FiPause aria-hidden="true" size={14} /> : <FiPlay aria-hidden="true" size={14} />} {autoFollow ? t("debug.autoFollowRunning") : t("debug.autoFollowPaused")}
                    </Button>
                  </div>
                </div>

                {streamState === "unauthorized" ? (
                  <div className="rounded-md border border-danger-300 bg-danger-50 p-2 text-sm text-danger-600">{t("auth.rootOnlyDebug")}</div>
                ) : null}
                {streamError ? <div className="text-sm text-danger">{streamError}</div> : null}

                <div
                  className="min-h-0 flex-1 overflow-hidden rounded-xl border border-default-200 bg-content1 p-2 font-mono text-[13px] leading-6"
                >
                  {renderedLines.length === 0 ? (
                    <div className="text-zinc-400">{searchText.trim().length > 0 ? t("debug.emptyFiltered") : t("debug.emptyStream")}</div>
                  ) : (
                    <Virtuoso
                      style={{ height: "100%" }}
                      data={renderedLines}
                      followOutput={autoFollow ? "auto" : false}
                      atBottomStateChange={(atBottom) => {
                        if (skipNextAtBottomSyncRef.current) {
                          skipNextAtBottomSyncRef.current = false;
                          return;
                        }
                        if (atBottom) return;
                      }}
                      itemContent={(_, item) => (
                        <LogLineRow item={item} expanded={expandedRows.has(item.rowKey)} onToggle={toggleExpandedRow} />
                      )}
                    />
                  )}
                </div>
              </div>
            </Tab>

            <Tab key="monitor" title={t("debug.monitorTab")}>
              <div className="space-y-3">
                <div className="flex items-center gap-2">
                  <Button radius="sm" color="primary" isLoading={monitorLoading} onPress={() => void loadMonitor()}>{t("common.refresh")}</Button>
                </div>
                {monitorError ? <div className="text-sm text-danger">{monitorError}</div> : null}
                {monitorSnapshot ? (
                  <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                    <Card radius="sm"><CardBody className="text-sm">{t("debug.monitor.cpu", { value: (Math.max(0, monitorSnapshot.cpuLoad) * 100).toFixed(1) })}</CardBody></Card>
                    <Card radius="sm"><CardBody className="text-sm">{t("debug.monitor.ram", { used: ((monitorSnapshot.totalMemBytes - monitorSnapshot.freeMemBytes) / (1024 ** 3)).toFixed(2), total: (monitorSnapshot.totalMemBytes / (1024 ** 3)).toFixed(2) })}</CardBody></Card>
                    <Card radius="sm"><CardBody className="text-sm">{t("debug.monitor.disk", { used: ((monitorSnapshot.diskTotalBytes - monitorSnapshot.diskUsableBytes) / (1024 ** 3)).toFixed(2), total: (monitorSnapshot.diskTotalBytes / (1024 ** 3)).toFixed(2) })}</CardBody></Card>
                    <Card radius="sm"><CardBody className="text-sm">{t("debug.monitor.heap", { used: (monitorSnapshot.heapUsedBytes / (1024 ** 2)).toFixed(1), total: (monitorSnapshot.heapMaxBytes / (1024 ** 2)).toFixed(1) })}</CardBody></Card>
                    <Card radius="sm"><CardBody className="text-sm">{t("debug.monitor.os", { value: monitorSnapshot.osName })}</CardBody></Card>
                    <Card radius="sm"><CardBody className="text-sm">{t("debug.monitor.uptime", { value: (monitorSnapshot.uptimeMs / 1000).toFixed(0) })}</CardBody></Card>
                  </div>
                ) : null}
              </div>
            </Tab>
          </Tabs>
        </CardBody>
      </Card>
    </div>
  );
}
