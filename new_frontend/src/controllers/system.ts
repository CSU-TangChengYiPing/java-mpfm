export interface HealthResponse {
  service: string;
  status: string;
  timestamp: string;
}

export interface BackendLogsResponse {
  log_file: string;
  max_lines: number;
  lines: string[];
  content: string;
}

export interface SystemTelemetrySnapshot {
  timestamp: string;
  cpuLoad: number;
  processCpuLoad: number;
  totalMemBytes: number;
  freeMemBytes: number;
  totalSwapBytes: number;
  freeSwapBytes: number;
  heapUsedBytes: number;
  heapMaxBytes: number;
  diskTotalBytes: number;
  diskUsableBytes: number;
  osName: string;
  osVersion: string;
  osArch: string;
  cpuCores: number;
  cpuModel: string;
  load1: number;
  load5: number;
  load15: number;
  networkRxBytes: number;
  networkTxBytes: number;
  networkRxBytesSinceStartup: number;
  networkTxBytesSinceStartup: number;
  diskReadBytes: number;
  diskWriteBytes: number;
  uptimeMs: number;
}

export interface DebugLogStreamQuery {
  level?: string;
  category?: string;
  keyword?: string;
  tailLines?: number;
  file?: string;
}

export interface LogFilesResponse {
  active: string;
  files: string[];
}

export interface AdminDashboardResponse {
  overview: SystemTelemetrySnapshot;
  history: SystemTelemetrySnapshot[];
  users: Array<{
    username: string;
    uploadBps: number;
    downloadBps: number;
    activeUploadTasks: number;
    activeDownloadTasks: number;
    activeChunks: number;
    totalBps: number;
  }>;
}

type APIErrorShape = { error?: string | { code?: string; message?: string } };

async function parseErrorMessage(resp: Response): Promise<string> {
  const text = await resp.text();
  try {
    const json = JSON.parse(text) as APIErrorShape;
    if (typeof json.error === "string") return json.error;
    if (json.error && typeof json.error === "object") {
      return `${json.error.code ? `[${json.error.code}] ` : ""}${json.error.message ?? text}`;
    }
    return text;
  } catch {
    return text || `HTTP ${resp.status}`;
  }
}

/** 系统诊断控制器：提供健康探针与后端日志读取，供前端运维面板做状态观测。 */
export default class SystemController {
  public static async health(): Promise<HealthResponse> {
    const resp = await fetch("/api/v1/system/ping");
    if (!resp.ok) throw new Error(await parseErrorMessage(resp));
    return (await resp.json()) as HealthResponse;
  }

  public static async backendLogs(maxLines = 400): Promise<BackendLogsResponse> {
    const resp = await fetch(`/api/v1/admin/logs?max_lines=${Math.max(1, Math.min(2000, Math.floor(maxLines)))}`);
    if (!resp.ok) throw new Error(await parseErrorMessage(resp));
    return (await resp.json()) as BackendLogsResponse;
  }

  public static async adminSystemOverview(): Promise<SystemTelemetrySnapshot> {
    const resp = await fetch("/api/v1/admin/telemetry/system/overview");
    if (!resp.ok) throw new Error(await parseErrorMessage(resp));
    return (await resp.json()) as SystemTelemetrySnapshot;
  }

  public static async adminSystemHistory(minutes = 15): Promise<SystemTelemetrySnapshot[]> {
    const safe = Math.max(1, Math.min(1440, Math.floor(minutes)));
    const resp = await fetch(`/api/v1/admin/telemetry/system/history?minutes=${safe}`);
    if (!resp.ok) throw new Error(await parseErrorMessage(resp));
    return (await resp.json()) as SystemTelemetrySnapshot[];
  }

  public static async adminDashboard(minutes = 3): Promise<AdminDashboardResponse> {
    const safe = Math.max(1, Math.min(1440, Math.floor(minutes)));
    const resp = await fetch(`/api/v1/admin/telemetry/dashboard?minutes=${safe}`);
    if (!resp.ok) throw new Error(await parseErrorMessage(resp));
    return (await resp.json()) as AdminDashboardResponse;
  }

  public static buildDebugLogStreamUrl(query: DebugLogStreamQuery): string {
    const params = new URLSearchParams();
    if (query.level) params.set("level", query.level);
    if (query.category) params.set("category", query.category);
    if (query.keyword) params.set("keyword", query.keyword);
    if (typeof query.tailLines === "number") {
      const safeTail = Math.max(1, Math.min(2000, Math.floor(query.tailLines)));
      params.set("tailLines", String(safeTail));
    }
    if (query.file) params.set("file", query.file);
    const qs = params.toString();
    return qs.length > 0 ? `/api/v1/debug/logs/stream?${qs}` : "/api/v1/debug/logs/stream";
  }

  public static async listLogFiles(): Promise<LogFilesResponse> {
    const resp = await fetch("/api/v1/admin/logs/files");
    if (!resp.ok) throw new Error(await parseErrorMessage(resp));
    return (await resp.json()) as LogFilesResponse;
  }

  public static async debugCopyAudit(visibleLines: number): Promise<void> {
    const resp = await fetch("/api/v1/debug/logs/copy-audit", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ visibleLines: Math.max(0, Math.floor(visibleLines)) }),
    });
    if (!resp.ok) throw new Error(await parseErrorMessage(resp));
  }

  /** 下载本地开发证书：用于客户端导入信任根后访问 HTTPS/WebDAV。 */
  public static async downloadDevCert(): Promise<Blob> {
    const resp = await fetch("/api/v1/system/dev-cert");
    if (!resp.ok) throw new Error(await parseErrorMessage(resp));
    return await resp.blob();
  }
}
