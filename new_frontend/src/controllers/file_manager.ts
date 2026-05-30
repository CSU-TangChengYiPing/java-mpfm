import path from "path-browserify";
import { AUTH_KEY } from "../hooks/authStorage";
import { reportTransferSample, resetTransferSample } from "../utils/transferRateMeter";
import {
  deleteDownloadResumeRecord,
  loadDownloadResumeRecord,
  saveDownloadResumeRecord,
  type DownloadResumeRecord,
} from "./download_resume_store";

export interface FileEntry {
  path: string;
  name: string;
  type: "file" | "directory" | "symlink" | "shortcut";
  sizeBytes: number;
  mtime: string;
  linkCount: number | null;
  visible: boolean;
  readable: boolean;
  writable: boolean;
  etag?: string;
  version?: string;
}

export interface FileInfo {
  path?: string;
  name: string;
  isDirectory: boolean;
  size: number;
  mtime: string;
  disabled?: boolean;
  visible?: boolean;
  readable?: boolean;
  writable?: boolean;
  etag?: string;
  version?: string;
  effective_permissions?: string[];
  visibility?: "public" | "private";
  share_override?: boolean;
  share_override_id?: string;
  permission_source?: "default" | "override";
}

export interface FileContentResult {
  content: string;
  entry: FileInfo | null;
}

type FileEntryResponse = Partial<FileEntry> & { type?: string; items?: FileEntryResponse[]; entry?: FileEntryResponse };
type FileItemsEnvelope = { items?: FileEntryResponse[]; page?: { page: number; pageSize: number; total: number } };
type FileEntryEnvelope = { entry?: FileEntryResponse };
type FileContentEnvelope = { entry?: FileEntryResponse; content?: string };
type APIErrorShape = { error?: string | { code?: string; message?: string } };
type V4TaskView = {
  id: string;
  name: string;
  creator: string;
  taskGroup?: string;
  state: string;
  status: string;
  progress: number;
  startTime: string;
  endTime?: string | null;
  totalBytes: number;
  error?: string | null;
};
type V4UploadAckResponse = {
  uploadId: string;
  taskId: string;
  status: string;
  totalBytes: number;
  chunkSizeBytes: number;
  totalChunks: number;
  completedParts: number[];
  updatedAt: string;
  uploadMode?: string;
};
type UploadCapabilityResponse = {
  supportsDirectUpload?: boolean;
  provider?: string;
  maxPartSizeBytes?: number;
  suggestedChunkSizeBytes?: number;
};
type V4TaskStreamEvent = {
  taskId: string;
  state: string;
  progress: number;
  transferredBytes: number;
  totalBytes: number;
  speedBytesPerSec: number;
  etaSeconds: number;
  updatedAt: string;
  errorCode?: string;
};
const TRANSFER_TASK_POLL_INTERVAL_MS = 500;
const TRANSFER_TASK_WAIT_TIMEOUT_MS = 30 * 60 * 1000;
const LOCAL_DOWNLOAD_HISTORY_KEY = "mpfm_download_center_history_v1";

export interface TaskInfo {
  taskId: string;
  action: string;
  actionLabel?: string;
  status: string;
  progress: number;
  result: string;
  requestId: string;
  createdRequestId?: string;
  errorCode: string;
  target?: string;
  targetName?: string;
  updatedAt?: string;
  totalCount?: number;
  successCount?: number;
  failedCount?: number;
  runningCount?: number;
  itemResults?: Array<{ itemPath: string; status: string; errorCode: string }>;
  transferredBytes?: number;
  totalBytes?: number;
  chunkSizeBytes?: number;
  totalChunks?: number;
  completedChunks?: number;
  failedChunks?: number;
  chunkStates?: string[];
  mountId?: string;
  retryDirPath?: string;
  expectedFileName?: string;
  retryTargetPath?: string;
  speedBytesPerSec?: number;
  etaSeconds?: number;
}

export const FILE_TASK_BOUNDARY = {
  sync: ["list", "tree", "stat", "read", "write", "mkdir", "delete", "rename", "move", "copy", "download"] as const,
  task: ["upload", "batchUpload", "batchDownload", "thumbnailPrepare"] as const,
};

/** 文件域统一异常：保留业务错误码与 HTTP 状态，供页面决定字段提示、表单提示或全局提示策略。 */
export class APIError extends Error {
  code?: string;
  status?: number;

  constructor(message: string, code?: string, status?: number) {
    super(message);
    this.name = "APIError";
    this.code = code;
    this.status = status;
  }
}

function parseErrorPayload(text: string, status?: number): APIError {
  try {
    const json = JSON.parse(text) as APIErrorShape;
    if (typeof json.error === "string") return new APIError(json.error, undefined, status);
    if (json.error && typeof json.error === "object") {
      return new APIError(json.error.message || text || `HTTP ${status ?? 0}`, json.error.code, status);
    }
  } catch {
    // ignore
  }
  return new APIError(text || `HTTP ${status ?? 0}`, undefined, status);
}

async function parseResponseError(resp: Response): Promise<never> {
  const text = await resp.text();
  throw parseErrorPayload(text, resp.status);
}


function canonicalType(raw?: string): FileEntry["type"] {
  if (raw === "directory" || raw === "file" || raw === "symlink" || raw === "shortcut") return raw;
  if (raw === "dir") return "directory";
  return "file";
}

function normalizeVirtualPathForApi(rawPath: string): string {
  const safeDecodeOnce = (value: string): string => {
    try {
      return decodeURIComponent(value);
    } catch {
      return value;
    }
  };
  let normalized = safeDecodeOnce((rawPath || ".").trim()).replace(/\\/g, "/");
  normalized = normalized.replace(/^\/?\.\//, "/");
  normalized = normalized.replace(/\/{2,}/g, "/");
  if (!normalized || normalized === ".") {
    return ".";
  }
  if (normalized.startsWith("/./")) {
    normalized = normalized.slice(2);
  }
  if (normalized.startsWith("/")) {
    return normalized;
  }
  return `/${normalized}`;
}

export interface UploadCapability {
  supportsDirectUpload: boolean;
  provider: string;
  maxPartSizeBytes: number;
  suggestedChunkSizeBytes: number;
}

function withVirtualPathQuery(basePath: string, virtualPath: string): string {
  const query = new URLSearchParams();
  query.set("virtualPath", normalizeVirtualPathForApi(virtualPath));
  return `${basePath}?${query.toString()}`;
}

function readAuthHeaderFromStorage(): string | null {
  if (typeof localStorage === "undefined") return null;
  const raw = localStorage.getItem(AUTH_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as { tokenType?: string; accessToken?: string };
    if (!parsed.accessToken) return null;
    const tokenType = (parsed.tokenType || "Bearer").trim();
    return `${tokenType} ${parsed.accessToken}`;
  } catch {
    return null;
  }
}

function withAuthHeader(headers: HeadersInit = {}): Headers {
  const merged = new Headers(headers);
  const auth = readAuthHeaderFromStorage();
  if (auth) merged.set("Authorization", auth);
  return merged;
}

function uploadRuntimeTaskStreamRequest(
  virtualPath: string,
  file: File,
  onProgress?: (loaded: number, total: number) => void,
): Promise<V4UploadAckResponse> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/api/v4/transfers/uploads/runtime/tasks");
    xhr.setRequestHeader("Content-Type", "application/octet-stream");
    xhr.setRequestHeader("X-Upload-Virtual-Path", encodeURIComponent(virtualPath));
    xhr.setRequestHeader("X-Upload-Filename", encodeURIComponent(file.name || "upload.bin"));
    xhr.setRequestHeader("X-Upload-Size", String(file.size));
    const auth = readAuthHeaderFromStorage();
    if (auth) xhr.setRequestHeader("Authorization", auth);
    xhr.upload.onprogress = (event) => {
      if (!event.lengthComputable) return;
      onProgress?.(event.loaded, event.total);
    };
    xhr.onerror = () => reject(new APIError("network error", "NETWORK_ERROR"));
    xhr.onload = () => {
      const text = xhr.responseText ?? "";
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          resolve(JSON.parse(text) as V4UploadAckResponse);
        } catch {
          reject(new APIError("invalid upload response", "INTERNAL_ERROR", xhr.status));
        }
        return;
      }
      reject(parseErrorPayload(text, xhr.status));
    };
    xhr.send(file);
  });
}

function uploadRuntimeTaskMultipartFallbackRequest(
  virtualPath: string,
  file: File,
  onProgress?: (loaded: number, total: number) => void,
): Promise<V4UploadAckResponse> {
  const form = new FormData();
  form.append("virtualPath", virtualPath);
  form.append("file", file, file.name);
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/api/v4/transfers/uploads/runtime/tasks");
    const auth = readAuthHeaderFromStorage();
    if (auth) xhr.setRequestHeader("Authorization", auth);
    xhr.upload.onprogress = (event) => {
      if (!event.lengthComputable) return;
      onProgress?.(event.loaded, event.total);
    };
    xhr.onerror = () => reject(new APIError("network error", "NETWORK_ERROR"));
    xhr.onload = () => {
      const text = xhr.responseText ?? "";
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          resolve(JSON.parse(text) as V4UploadAckResponse);
        } catch {
          reject(new APIError("invalid upload response", "INTERNAL_ERROR", xhr.status));
        }
        return;
      }
      reject(parseErrorPayload(text, xhr.status));
    };
    xhr.send(form);
  });
}


function normalizeEntry(raw: FileEntryResponse): FileEntry {
  const safeDecodeOnce = (value: string): string => {
    try {
      return decodeURIComponent(value);
    } catch {
      return value;
    }
  };
  return {
    path: safeDecodeOnce(raw.path ?? ""),
    name: safeDecodeOnce(raw.name ?? ""),
    type: canonicalType(raw.type),
    sizeBytes: typeof raw.sizeBytes === "number" ? raw.sizeBytes : 0,
    mtime: raw.mtime ?? "",
    linkCount: typeof raw.linkCount === "number" ? raw.linkCount : null,
    visible: raw.visible !== false,
    readable: raw.readable !== false,
    writable: raw.writable !== false,
    etag: raw.etag,
    version: raw.version,
  };
}

function extractItems(payload: unknown): FileEntry[] {
  if (payload && typeof payload === "object") {
    const obj = payload as FileItemsEnvelope;
    if (Array.isArray(obj.items)) return obj.items.map(normalizeEntry);
  }
  return [];
}

function toFileInfo(entry: FileEntry): FileInfo {
  const effective: string[] = [];
  if (entry.visible) effective.push("visible");
  if (entry.readable) effective.push("read");
  if (entry.writable) effective.push("write", "delete", "move");
  return {
    path: entry.path,
    name: entry.name,
    isDirectory: entry.type === "directory",
    size: entry.sizeBytes,
    mtime: entry.mtime,
    visible: entry.visible,
    readable: entry.readable,
    writable: entry.writable,
    etag: entry.etag,
    version: entry.version,
    effective_permissions: effective,
    visibility: entry.visible ? "public" : "private",
    share_override: false,
  };
}

/** 文件域聚合控制器：统一封装文件 API、If-Match 并发控制、断点续传与本地任务恢复。 */
export default class FileManager {
  private static versionCache = new Map<string, string>();
  private static taskStream: EventSource | null = null;
  private static taskStreamAbortController: AbortController | null = null;
  private static taskCache = new Map<string, TaskInfo>();
  private static taskCacheVersion = new Map<string, string>();
  private static taskDeletedTombstoneAt = new Map<string, number>();
  private static taskStreamListeners = new Set<(tasks: TaskInfo[]) => void>();
  private static taskStreamEmitTimer: number | null = null;
  private static readonly TASK_STREAM_EMIT_INTERVAL_MS = 120;
  private static readonly TASK_DELETE_TOMBSTONE_TTL_MS = 5 * 60 * 1000;
  private static readonly DOWNLOAD_CHUNK_SIZE = 4 * 1024 * 1024;
  private static readonly activeDownloadControllers = new Map<string, AbortController>();
  private static readonly localDownloadTasks = new Map<string, TaskInfo>();
  private static readonly localDownloadTaskKeyById = new Map<string, string>();
  private static readonly localDownloadSpeedSamples = new Map<string, { bytes: number; ts: number }>();
  private static localDownloadHistoryLoaded = false;
  private static pendingResumeHintCount = 0;
  private static readonly localDownloadListeners = new Set<(tasks: TaskInfo[]) => void>();

  private static cacheKey(virtualPath: string): string {
    return virtualPath;
  }

  private static putVersion(virtualPath: string, etag?: string, version?: string): void {
    if (etag) this.versionCache.set(this.cacheKey(virtualPath), etag);
    else if (version) this.versionCache.set(this.cacheKey(virtualPath), version);
  }

  private static ifMatchHeader(virtualPath: string): Record<string, string> {
    const value = this.versionCache.get(this.cacheKey(virtualPath)) || "*";
    return { "If-Match": value };
  }

  public static async listFiles(virtualPath = "."): Promise<FileInfo[]> {
    const resp = await fetch(withVirtualPathQuery("/api/v1/files/list", virtualPath), { cache: "no-store" });
    if (!resp.ok) await parseResponseError(resp);
    const entries = extractItems(await resp.json());
    for (const entry of entries) this.putVersion(entry.path || path.join(virtualPath, entry.name), entry.etag, entry.version);
    return entries.map(toFileInfo);
  }

  public static async listDirectories(virtualPath = "."): Promise<FileInfo[]> {
    const files = await this.listFiles(virtualPath);
    return files.filter((file) => file.isDirectory);
  }

  public static async createDirectory(virtualPath: string): Promise<boolean> {
    const apiPath = normalizeVirtualPathForApi(virtualPath);
    const resp = await fetch("/api/v1/files/mkdir", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...this.ifMatchHeader(apiPath) },
      body: JSON.stringify({ virtualPath: apiPath }),
    });
    if (!resp.ok) await parseResponseError(resp);
    return true;
  }

  public static async createFile(virtualPath: string): Promise<boolean> {
    return this.writeFile(virtualPath, "");
  }

  public static async delete(virtualPath: string): Promise<boolean> {
    const apiPath = normalizeVirtualPathForApi(virtualPath);
    const resp = await fetch(withVirtualPathQuery("/api/v1/files", apiPath), {
      method: "DELETE",
      headers: this.ifMatchHeader(apiPath),
    });
    if (!resp.ok) await parseResponseError(resp);
    return true;
  }

  public static async readFileWithMeta(virtualPath: string): Promise<FileContentResult> {
    const apiPath = normalizeVirtualPathForApi(virtualPath);
    const resp = await fetch(withVirtualPathQuery("/api/v1/files/content", apiPath), { cache: "no-store" });
    if (!resp.ok) await parseResponseError(resp);
    const body = (await resp.json()) as FileContentEnvelope;
    this.putVersion(apiPath, resp.headers.get("ETag") || undefined, undefined);
    const entry = body.entry ? normalizeEntry(body.entry) : null;
    if (entry) this.putVersion(apiPath, entry.etag, entry.version);
    return { content: body.content ?? "", entry: entry ? toFileInfo(entry) : null };
  }

  public static async readFile(virtualPath: string): Promise<string> {
    const result = await this.readFileWithMeta(virtualPath);
    return result.content;
  }

  public static async statFile(virtualPath: string): Promise<FileInfo | null> {
    const apiPath = normalizeVirtualPathForApi(virtualPath);
    const resp = await fetch(withVirtualPathQuery("/api/v1/files/stat", apiPath), { cache: "no-store" });
    if (!resp.ok) await parseResponseError(resp);
    const body = (await resp.json()) as FileEntryEnvelope;
    if (!body.entry) return null;
    const entry = normalizeEntry(body.entry);
    this.putVersion(apiPath, entry.etag, entry.version);
    return toFileInfo(entry);
  }

  public static async writeFile(virtualPath: string, content: string, ifMatch?: string): Promise<boolean> {
    const apiPath = normalizeVirtualPathForApi(virtualPath);
    const headerValue = ifMatch && ifMatch.trim() ? ifMatch : "*";
    const resp = await fetch("/api/v1/files/content", {
      method: "PUT",
      headers: { "Content-Type": "application/json", "If-Match": headerValue },
      body: JSON.stringify({ virtualPath: apiPath, content }),
    });
    if (!resp.ok) await parseResponseError(resp);
    const entry = normalizeEntry((await resp.json()) as FileEntryResponse);
    this.putVersion(apiPath, entry.etag, entry.version);
    return true;
  }

  public static async rename(oldVirtualPath: string, newVirtualPath: string): Promise<boolean> {
    const oldApiPath = normalizeVirtualPathForApi(oldVirtualPath);
    const targetName = path.basename(newVirtualPath);
    const resp = await fetch("/api/v1/files/rename", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...this.ifMatchHeader(oldApiPath) },
      body: JSON.stringify({ virtualPath: oldApiPath, targetName }),
    });
    if (!resp.ok) await parseResponseError(resp);
    return true;
  }

  public static async move(fromVirtualPath: string, toVirtualPath: string): Promise<boolean> {
    const fromApiPath = normalizeVirtualPathForApi(fromVirtualPath);
    const toApiPath = normalizeVirtualPathForApi(toVirtualPath);
    const resp = await fetch("/api/v1/files/move", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...this.ifMatchHeader(fromApiPath) },
      body: JSON.stringify({ fromVirtualPath: fromApiPath, toVirtualPath: toApiPath }),
    });
    if (!resp.ok) await parseResponseError(resp);
    return true;
  }

  public static async copy(fromVirtualPath: string, toVirtualPath: string): Promise<boolean> {
    const fromApiPath = normalizeVirtualPathForApi(fromVirtualPath);
    const toApiPath = normalizeVirtualPathForApi(toVirtualPath);
    const resp = await fetch("/api/v1/files/copy", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...this.ifMatchHeader(fromApiPath) },
      body: JSON.stringify({ fromVirtualPath: fromApiPath, toVirtualPath: toApiPath }),
    });
    if (!resp.ok) await parseResponseError(resp);
    return true;
  }

  public static download(targetPath: string, mountId?: string): void {
    void this.downloadWithProgress(targetPath, mountId);
  }

  public static pauseDownload(targetPath: string, mountId?: string): void {
    const key = this.downloadResumeKey(targetPath, mountId);
    const controller = this.activeDownloadControllers.get(key);
    if (controller) controller.abort();
  }

  public static pauseDownloadByTaskId(taskId: string): boolean {
    const key = this.localDownloadTaskKeyById.get(taskId);
    if (!key) return false;
    const controller = this.activeDownloadControllers.get(key);
    if (!controller) return false;
    controller.abort();
    return true;
  }

  public static async resumeDownloadByTaskId(taskId: string): Promise<TaskInfo> {
    const task = this.localDownloadTasks.get(taskId);
    if (!task || !task.target) {
      throw new APIError("download task not resumable", "TASK_NOT_FOUND");
    }
    return this.downloadWithProgress(task.target);
  }

  public static consumePendingResumeHintCount(): number {
    const count = this.pendingResumeHintCount;
    this.pendingResumeHintCount = 0;
    return count;
  }

  /**
   * 下载代理直连流程：读取响应流并在本地累计字节，用于侧边栏展示下载瞬时速率。
   * 前置条件：targetPath 必须是可访问虚拟路径；若后端返回非 2xx，将抛出统一 APIError。
   */
  public static async downloadWithProgress(
    targetPath: string,
    mountId?: string,
    onProgress?: (loaded: number, total: number, speedBytesPerSec: number) => void,
  ): Promise<TaskInfo> {
    const normalizedTargetPath = targetPath.replace(/^\/+/, "");
    const virtualPath = normalizeVirtualPathForApi(mountId ? `/personal/${mountId}/${normalizedTargetPath}` : targetPath);
    const key = this.downloadResumeKey(targetPath, mountId);
    const localTaskId = this.localDownloadTaskId(key);
    this.localDownloadTaskKeyById.set(localTaskId, key);
    const initial = await this.prepareDownloadResumeRecord(key, virtualPath, targetPath);
    resetTransferSample("download");
    this.upsertLocalDownloadTask(localTaskId, initial, "RUNNING", "");
    const controller = new AbortController();
    this.activeDownloadControllers.set(key, controller);
    const record = initial;
    let lastUiEmitAt = 0;
    try {
      while (record.loadedBytes < record.totalSize) {
        const start = record.loadedBytes;
        const end = Math.min(record.totalSize - 1, start + record.chunkSize - 1);
        const chunkIndex = Math.floor(start / record.chunkSize);
        const headers = withAuthHeader({
          Range: `bytes=${start}-${end}`,
          "If-Range": record.etag,
        });
        const resp = await fetch(`/api/v5/files/content?${new URLSearchParams({ virtualPath }).toString()}`, {
          method: "GET",
          headers,
          signal: controller.signal,
        });
        if (!resp.ok) await parseResponseError(resp);
        const contentRange = resp.headers.get("Content-Range");
        if (resp.status !== 206 || !contentRange) {
          throw new APIError("range download contract violated", "INTERNAL_ERROR", resp.status);
        }
        const chunkParts: BlobPart[] = [];
        let rangeLoaded = 0;
        if (resp.body) {
          const reader = resp.body.getReader();
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            if (!value) continue;
            chunkParts.push(new Uint8Array(value));
            rangeLoaded += value.byteLength;
            const liveLoaded = Math.min(end + 1, start + rangeLoaded);
            reportTransferSample("download", liveLoaded);
            const now = Date.now();
            if (now - lastUiEmitAt >= 180) {
              lastUiEmitAt = now;
              const liveRecord: DownloadResumeRecord = {
                ...record,
                loadedBytes: liveLoaded,
                updatedAt: new Date().toISOString(),
              };
              this.upsertLocalDownloadTask(localTaskId, liveRecord, "RUNNING", "");
              const speed = this.localDownloadTasks.get(localTaskId)?.speedBytesPerSec ?? 0;
              onProgress?.(liveLoaded, record.totalSize, speed);
            }
          }
        }
        const chunk = new Blob(chunkParts);
        record.chunks[chunkIndex] = chunk;
        if (!record.completedChunkIndexes.includes(chunkIndex)) {
          record.completedChunkIndexes.push(chunkIndex);
          record.completedChunkIndexes.sort((a, b) => a - b);
        }
        record.loadedBytes = end + 1;
        record.updatedAt = new Date().toISOString();
        await saveDownloadResumeRecord(record);
        reportTransferSample("download", record.loadedBytes);
        this.upsertLocalDownloadTask(localTaskId, record, "RUNNING", "");
        const speed = this.localDownloadTasks.get(localTaskId)?.speedBytesPerSec ?? 0;
        onProgress?.(record.loadedBytes, record.totalSize, speed);
      }
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        this.upsertLocalDownloadTask(localTaskId, record, "PAUSED", "DOWNLOAD_PAUSED");
        throw new APIError("download paused", "DOWNLOAD_PAUSED");
      }
      this.upsertLocalDownloadTask(localTaskId, record, "FAILED", error instanceof Error ? error.message : "INTERNAL_ERROR");
      throw error;
    } finally {
      this.activeDownloadControllers.delete(key);
    }
    const blob = new Blob(record.chunks.filter((item): item is Blob => item !== null), { type: "application/octet-stream" });
    const filename = path.basename(targetPath) || "download.bin";
    const url = URL.createObjectURL(blob);
    try {
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
    } finally {
      URL.revokeObjectURL(url);
    }
    await deleteDownloadResumeRecord(key);
    this.upsertLocalDownloadTask(localTaskId, record, "SUCCESS", "");
    this.localDownloadSpeedSamples.delete(localTaskId);
    return {
      taskId: localTaskId,
      action: "download",
      actionLabel: "download",
      status: "SUCCESS",
      progress: 100,
      result: "SUCCESS",
      requestId: "",
      createdRequestId: "",
      errorCode: "",
      target: virtualPath,
      targetName: filename,
      updatedAt: new Date().toISOString(),
    };
  }

  private static async prepareDownloadResumeRecord(key: string, virtualPath: string, targetPath: string): Promise<DownloadResumeRecord> {
    const probeResp = await fetch(`/api/v5/files/content?${new URLSearchParams({ virtualPath }).toString()}`, {
      method: "GET",
      headers: withAuthHeader({ Range: "bytes=0-0" }),
    });
    if (!probeResp.ok) await parseResponseError(probeResp);
    const etag = probeResp.headers.get("ETag") || "";
    const contentRange = probeResp.headers.get("Content-Range");
    const totalSize = this.parseTotalSize(contentRange, probeResp.headers.get("Content-Length"));
    if (!etag || totalSize <= 0) {
      throw new APIError("download metadata missing", "INTERNAL_ERROR", probeResp.status);
    }
    const cached = await loadDownloadResumeRecord(key);
    if (cached && cached.etag === etag && cached.totalSize === totalSize) {
      return cached;
    }
    if (cached && (cached.etag !== etag || cached.totalSize !== totalSize)) {
      await deleteDownloadResumeRecord(key);
      throw new APIError("download source changed, resume invalid", "VERSION_CONFLICT");
    }
    const next: DownloadResumeRecord = {
      key,
      virtualPath,
      fileName: path.basename(targetPath) || "download.bin",
      etag,
      totalSize,
      chunkSize: this.DOWNLOAD_CHUNK_SIZE,
      loadedBytes: 0,
      chunks: [],
      completedChunkIndexes: [],
      fileHandle: undefined,
      updatedAt: new Date().toISOString(),
    };
    await saveDownloadResumeRecord(next);
    return next;
  }

  private static parseTotalSize(contentRange: string | null, contentLength: string | null): number {
    if (contentRange) {
      const slash = contentRange.lastIndexOf("/");
      if (slash >= 0) {
        const raw = Number(contentRange.slice(slash + 1));
        if (Number.isFinite(raw) && raw > 0) return raw;
      }
    }
    const len = Number(contentLength ?? "0");
    return Number.isFinite(len) && len > 0 ? len : 0;
  }

  private static downloadResumeKey(targetPath: string, mountId?: string): string {
    return `${mountId ?? "default"}::${targetPath}`;
  }

  private static localDownloadTaskId(key: string): string {
    return `download-local-${encodeURIComponent(key)}`;
  }

  private static upsertLocalDownloadTask(taskId: string, record: DownloadResumeRecord, status: string, errorCode: string): void {
    const now = Date.now();
    const prev = this.localDownloadSpeedSamples.get(taskId);
    let speedBytesPerSec = 0;
    if (status === "RUNNING") {
      if (prev && now > prev.ts && record.loadedBytes >= prev.bytes) {
        speedBytesPerSec = Math.max(0, Math.round(((record.loadedBytes - prev.bytes) * 1000) / (now - prev.ts)));
      }
      this.localDownloadSpeedSamples.set(taskId, { bytes: record.loadedBytes, ts: now });
    } else if (prev) {
      speedBytesPerSec = 0;
    }
    const totalChunks = Math.max(1, Math.ceil(record.totalSize / Math.max(1, record.chunkSize)));
    const completed = new Set(record.completedChunkIndexes);
    const chunkStates = Array.from({ length: totalChunks }, (_, idx) => (completed.has(idx) ? "done" : "pending"));
    const progress = record.totalSize > 0 ? Math.round((record.loadedBytes / record.totalSize) * 100) : 0;
    const next: TaskInfo = {
      taskId,
      action: "download",
      actionLabel: "download",
      status,
      progress: Math.max(0, Math.min(100, progress)),
      result: status,
      requestId: "",
      createdRequestId: "",
      errorCode,
      target: record.virtualPath,
      targetName: record.fileName,
      updatedAt: record.updatedAt,
      transferredBytes: record.loadedBytes,
      totalBytes: record.totalSize,
      chunkSizeBytes: record.chunkSize,
      totalChunks,
      completedChunks: record.completedChunkIndexes.length,
      chunkStates,
      speedBytesPerSec,
    };
    this.localDownloadTasks.set(taskId, next);
    this.persistLocalDownloadHistory();
    this.emitLocalDownloadTasks();
    this.taskCache.set(taskId, next);
    this.taskCacheVersion.set(taskId, `${taskId}#${next.updatedAt || ""}`);
    this.scheduleTaskStreamEmit();
  }

  public static async upload(targetPath: string, files: File[], mountId?: string): Promise<TaskInfo[]> {
    const tasks: TaskInfo[] = [];
    for (const file of files) {
      tasks.push(await this.uploadWithProgress(targetPath, file, undefined, mountId));
    }
    return tasks;
  }

  public static async getUploadCapability(virtualPath: string): Promise<UploadCapability> {
    const apiPath = normalizeVirtualPathForApi(virtualPath);
    const resp = await fetch(withVirtualPathQuery("/api/v4/transfers/uploads/capabilities", apiPath), {
      headers: withAuthHeader(),
    });
    if (!resp.ok) await parseResponseError(resp);
    const body = (await resp.json()) as UploadCapabilityResponse;
    return {
      supportsDirectUpload: body.supportsDirectUpload !== false,
      provider: body.provider || "runtime",
      maxPartSizeBytes: Math.max(0, Number(body.maxPartSizeBytes ?? 0)),
      suggestedChunkSizeBytes: Math.max(0, Number(body.suggestedChunkSizeBytes ?? 0)),
    };
  }

  public static async batchUpload(paths: string[]): Promise<TaskInfo> {
    const targetPath = targetPathFromBatch(paths);
    throw new APIError(`batch upload requires files for target: ${targetPath}`, "VALIDATION_ERROR");
  }

  public static async batchUploadFiles(targetPath: string, files: File[], mountId?: string): Promise<TaskInfo> {
    const tasks = await this.upload(targetPath, files, mountId);
    return tasks[0] || {
      taskId: "",
      action: "upload",
      status: "FAILED",
      progress: 0,
      result: "FAILED",
      requestId: "",
      errorCode: "VALIDATION_ERROR",
    };
  }

  public static async batchDownload(paths: string[], mountId?: string): Promise<TaskInfo> {
    if (paths.length === 0) throw new APIError("empty download paths", "VALIDATION_ERROR");
    return this.downloadWithProgress(paths[0], mountId);
  }

  /**
   * 直传上传主链路：先向服务端申请分片票据，再逐片上传并回传 ack，确保任务状态由服务端 Runtime 收敛。
   * 失败时统一抛出 APIError，供页面按字段/表单/全局分层提示。
   */
  public static async uploadWithProgress(targetPath: string, file: File, onProgress?: (loaded: number, total: number) => void, mountId?: string): Promise<TaskInfo> {
    const normalizedTargetPath = targetPath.replace(/^\/+/, "");
    const virtualPath = normalizeVirtualPathForApi(mountId ? `/personal/${mountId}/${normalizedTargetPath}` : targetPath);
    onProgress?.(0, file.size);
    let session: V4UploadAckResponse;
    try {
      session = await uploadRuntimeTaskStreamRequest(virtualPath, file, (loaded, total) => {
        reportTransferSample("upload", loaded);
        onProgress?.(loaded, total || file.size);
      });
    } catch (error) {
      const apiError = error instanceof APIError ? error : null;
      const shouldFallback = apiError !== null && (apiError.status === 404 || apiError.status === 405 || apiError.status === 415);
      if (!shouldFallback) throw error;
      session = await uploadRuntimeTaskMultipartFallbackRequest(virtualPath, file, (loaded, total) => {
        reportTransferSample("upload", loaded);
        onProgress?.(loaded, total || file.size);
      });
    }
    onProgress?.(file.size, file.size);
    const uploadMode = (session.uploadMode || "").toLowerCase();
    if (uploadMode === "stream" || !session.taskId || session.taskId.trim() === "") {
      return this.buildImmediateUploadTask(virtualPath, file, session);
    }
    return await this.waitTaskTerminal(session.taskId);
  }

  /** 上传成功判定以任务终态为准，避免“入口发送完成即成功”的状态错判。 */
  private static async waitTaskTerminal(taskId: string): Promise<TaskInfo> {
    const start = Date.now();
    while (Date.now() - start < TRANSFER_TASK_WAIT_TIMEOUT_MS) {
      const task = await this.getTask(taskId);
      const status = (task.status || "").toUpperCase();
      if (status === "SUCCESS") return task;
      if (status === "FAILED" || status === "CANCELED") {
        throw new APIError(task.errorCode || "upload task failed", task.errorCode || "TRANSFER_FAILED");
      }
      await new Promise((resolve) => globalThis.setTimeout(resolve, TRANSFER_TASK_POLL_INTERVAL_MS));
    }
    throw new APIError("upload task wait timeout", "TRANSFER_TIMEOUT");
  }

  private static buildImmediateUploadTask(virtualPath: string, file: File, session: V4UploadAckResponse): TaskInfo {
    return {
      taskId: session.uploadId || "",
      action: "upload",
      actionLabel: "upload",
      status: "SUCCESS",
      progress: 100,
      result: "SUCCESS",
      requestId: "",
      createdRequestId: "",
      errorCode: "",
      target: `${virtualPath}/${file.name}`,
      targetName: file.name,
      updatedAt: session.updatedAt || new Date().toISOString(),
      transferredBytes: file.size,
      totalBytes: file.size,
    };
  }

  public static async uploadBatchWithProgress(targetPath: string, files: File[], onProgress?: (loaded: number, total: number) => void, mountId?: string): Promise<TaskInfo> {
    let loaded = 0;
    const total = files.reduce((sum, item) => sum + item.size, 0);
    let lastTask: TaskInfo | null = null;
    for (const file of files) {
      lastTask = await this.uploadWithProgress(targetPath, file, (partLoaded, partTotal) => {
        const fileBase = loaded;
        onProgress?.(fileBase + partLoaded, total);
        if (partLoaded >= partTotal) {
          loaded += partTotal;
        }
      }, mountId);
    }
    if (lastTask) return lastTask;
    throw new APIError("empty upload files", "VALIDATION_ERROR");
  }

  /** 单文件可续传上传：会话恢复、分片重试、完整性校验与完成提交。 */
  public static async getTask(taskId: string): Promise<TaskInfo> {
    const resp = await fetch(`/api/v4/transfers/tasks/${encodeURIComponent(taskId)}`, { headers: withAuthHeader() });
    if (!resp.ok) await parseResponseError(resp);
    return this.mapV4TaskView((await resp.json()) as V4TaskView);
  }

  /** 聚合任务列表：合并本地上传/下载任务与后端任务并按 taskId 去重。 */
  public static async listTasks(): Promise<TaskInfo[]> {
    this.loadLocalDownloadHistoryIfNeeded();
    let remote: TaskInfo[] = [];
    try {
      const [undoneResp, doneResp] = await Promise.all([
        fetch("/api/v4/transfers/tasks/undone", { headers: withAuthHeader() }),
        fetch("/api/v4/transfers/tasks/done", { headers: withAuthHeader() }),
      ]);
      if (undoneResp.ok && doneResp.ok) {
        const undone = (await undoneResp.json()) as V4TaskView[];
        const done = (await doneResp.json()) as V4TaskView[];
        remote = [...undone, ...done].map((item) => this.mapV4TaskView(item));
      }
    } catch {
      remote = [];
    }
    const nextCache = new Map<string, TaskInfo>();
    const nextVersion = new Map<string, string>();
    for (const item of remote) {
      this.taskDeletedTombstoneAt.delete(item.taskId);
      nextCache.set(item.taskId, item);
      nextVersion.set(item.taskId, `${item.taskId}#${item.updatedAt || ""}`);
    }
    for (const item of this.localDownloadTasks.values()) {
      nextCache.set(item.taskId, item);
      nextVersion.set(item.taskId, `${item.taskId}#${item.updatedAt || ""}`);
    }
    this.taskCache = nextCache;
    this.taskCacheVersion = nextVersion;
    return Array.from(this.taskCache.values());
  }

  public static listDownloadCenterTasks(): TaskInfo[] {
    this.loadLocalDownloadHistoryIfNeeded();
    return Array.from(this.localDownloadTasks.values())
      .filter((item) => item.taskId.startsWith("download-local-"))
      .sort((a, b) => Date.parse(b.updatedAt || "") - Date.parse(a.updatedAt || ""));
  }

  public static subscribeDownloadCenterTasks(onChange: (tasks: TaskInfo[]) => void): () => void {
    this.loadLocalDownloadHistoryIfNeeded();
    this.localDownloadListeners.add(onChange);
    onChange(this.listDownloadCenterTasks());
    return () => {
      this.localDownloadListeners.delete(onChange);
    };
  }

  private static persistLocalDownloadHistory(): void {
    if (typeof localStorage === "undefined") return;
    const items = Array.from(this.localDownloadTasks.values())
      .filter((item) => item.taskId.startsWith("download-local-"))
      .sort((a, b) => Date.parse(b.updatedAt || "") - Date.parse(a.updatedAt || ""))
      .slice(0, 200);
    try {
      localStorage.setItem(LOCAL_DOWNLOAD_HISTORY_KEY, JSON.stringify(items));
    } catch {
      // ignore quota or serialization errors
    }
  }

  private static emitLocalDownloadTasks(): void {
    const snapshot = this.listDownloadCenterTasks();
    for (const listener of this.localDownloadListeners) {
      listener(snapshot);
    }
  }

  private static loadLocalDownloadHistoryIfNeeded(): void {
    if (this.localDownloadHistoryLoaded) return;
    this.localDownloadHistoryLoaded = true;
    if (typeof localStorage === "undefined") return;
    const raw = localStorage.getItem(LOCAL_DOWNLOAD_HISTORY_KEY);
    if (!raw) return;
    try {
      const parsed = JSON.parse(raw) as TaskInfo[];
      let pendingCount = 0;
      for (const item of parsed) {
        if (!item || !item.taskId || !item.taskId.startsWith("download-local-")) continue;
        this.localDownloadTasks.set(item.taskId, item);
        const st = (item.status || "").toUpperCase();
        if (st !== "SUCCESS" && st !== "CANCELED") pendingCount += 1;
      }
      this.pendingResumeHintCount = pendingCount;
      this.emitLocalDownloadTasks();
    } catch {
      // ignore malformed cache
    }
  }

  /**
   * 任务流订阅：消费 SSE 事件并按 taskId+updatedAt 幂等合并，使用节流批量派发避免高频渲染风暴。
   * 返回值用于页面卸载时解除监听并释放 EventSource。
   */
  public static subscribeTaskStream(onChange: (tasks: TaskInfo[]) => void): () => void {
    this.taskStreamListeners.add(onChange);
    const hasBearerAuth = !!readAuthHeaderFromStorage();
    if (hasBearerAuth) {
      if (!this.taskStreamAbortController) this.startTaskStreamWithFetch();
    } else if (!this.taskStream) {
      const stream = new EventSource("/api/v4/transfers/tasks/stream");
      this.taskStream = stream;
      stream.addEventListener("task", (raw) => {
        try {
          const message = raw as MessageEvent<string>;
          const event = JSON.parse(message.data) as V4TaskStreamEvent;
          this.gcTaskDeleteTombstones();
          if (this.taskDeletedTombstoneAt.has(event.taskId)) return;
          const key = `${event.taskId}#${event.updatedAt || ""}`;
          if (this.taskCacheVersion.get(event.taskId) === key) return;
          this.taskCacheVersion.set(event.taskId, key);
          const current = this.taskCache.get(event.taskId);
          const next: TaskInfo = {
            taskId: event.taskId,
            action: current?.action || "transfer",
            actionLabel: current?.actionLabel || "transfer",
            status: (event.state || "").toUpperCase(),
            progress: Math.max(0, Math.min(100, Math.round(event.progress ?? 0))),
            result: (event.state || "").toUpperCase(),
            requestId: current?.requestId || "",
            createdRequestId: current?.createdRequestId || "",
            errorCode: event.errorCode || "",
            target: current?.target || "",
            targetName: current?.targetName || "",
            updatedAt: event.updatedAt || new Date().toISOString(),
            transferredBytes: Math.max(0, Number(event.transferredBytes ?? 0)),
            totalBytes: Math.max(0, Number(event.totalBytes ?? current?.totalBytes ?? 0)),
            speedBytesPerSec: Math.max(0, Number(event.speedBytesPerSec ?? 0)),
            etaSeconds: Number(event.etaSeconds ?? -1),
          };
          this.taskCache.set(next.taskId, next);
          this.scheduleTaskStreamEmit();
        } catch {
          // ignore malformed SSE message
        }
      });
      stream.onerror = () => {
        // keep browser auto-reconnect behavior
      };
    }
    onChange(Array.from(this.taskCache.values()));
    return () => {
      this.taskStreamListeners.delete(onChange);
      if (this.taskStreamListeners.size === 0 && this.taskStream) {
        this.taskStream.close();
        this.taskStream = null;
      }
      if (this.taskStreamListeners.size === 0 && this.taskStreamAbortController) {
        this.taskStreamAbortController.abort();
        this.taskStreamAbortController = null;
      }
      if (this.taskStreamListeners.size === 0 && this.taskStreamEmitTimer !== null) {
        globalThis.clearTimeout(this.taskStreamEmitTimer);
        this.taskStreamEmitTimer = null;
      }
    };
  }

  private static emitTaskStream(): void {
    if (this.taskStreamEmitTimer !== null) {
      globalThis.clearTimeout(this.taskStreamEmitTimer);
      this.taskStreamEmitTimer = null;
    }
    const snapshot = Array.from(this.taskCache.values());
    for (const listener of this.taskStreamListeners) {
      listener(snapshot);
    }
  }

  private static scheduleTaskStreamEmit(): void {
    if (this.taskStreamEmitTimer !== null) return;
    this.taskStreamEmitTimer = globalThis.setTimeout(() => {
      this.taskStreamEmitTimer = null;
      this.emitTaskStream();
    }, this.TASK_STREAM_EMIT_INTERVAL_MS) as unknown as number;
  }

  public static async cancelTask(taskId: string): Promise<TaskInfo> {
    const resp = await fetch(`/api/v4/transfers/tasks/${encodeURIComponent(taskId)}/cancel`, { method: "POST", headers: withAuthHeader() });
    if (!resp.ok) await parseResponseError(resp);
    return this.mapV4TaskView((await resp.json()) as V4TaskView);
  }

  public static async deleteTask(taskId: string): Promise<{ action: string; taskId: string; status: string }> {
    if (taskId.startsWith("download-local-")) {
      this.localDownloadTasks.delete(taskId);
      this.localDownloadTaskKeyById.delete(taskId);
      this.localDownloadSpeedSamples.delete(taskId);
      this.persistLocalDownloadHistory();
      this.emitLocalDownloadTasks();
      return { action: "delete", taskId, status: "SUCCESS" };
    }
    const resp = await fetch(`/api/v4/transfers/tasks/${encodeURIComponent(taskId)}`, { method: "DELETE", headers: withAuthHeader() });
    if (!resp.ok) await parseResponseError(resp);
    const body = (await resp.json()) as { taskId: string; status: string };
    this.localDownloadTasks.delete(body.taskId);
    this.persistLocalDownloadHistory();
    this.emitLocalDownloadTasks();
    this.gcTaskDeleteTombstones();
    this.taskDeletedTombstoneAt.set(body.taskId, Date.now());
    this.taskCache.delete(body.taskId);
    this.taskCacheVersion.delete(body.taskId);
    this.scheduleTaskStreamEmit();
    return { action: "delete", taskId: body.taskId, status: body.status };
  }

  private static gcTaskDeleteTombstones(): void {
    const now = Date.now();
    for (const [taskId, createdAt] of this.taskDeletedTombstoneAt) {
      if (now - createdAt > this.TASK_DELETE_TOMBSTONE_TTL_MS) {
        this.taskDeletedTombstoneAt.delete(taskId);
      }
    }
  }

  public static async cleanupTasks(statuses: string[]): Promise<{ deletedCount: number }> {
    const status = statuses.length > 0 ? statuses[0] : "";
    const resp = await fetch("/api/v4/transfers/tasks/clear-done", {
      method: "POST",
      headers: withAuthHeader({ "Content-Type": "application/json" }),
      body: JSON.stringify({ status }),
    });
    if (!resp.ok) await parseResponseError(resp);
    return (await resp.json()) as { deletedCount: number };
  }

  private static mapV4TaskView(task: V4TaskView): TaskInfo {
    const normalizedStatus = (task.status || "").toUpperCase();
    const result = normalizedStatus || "UNKNOWN";
    return {
      taskId: task.id,
      action: task.name,
      actionLabel: task.name,
      status: normalizedStatus,
      progress: Math.max(0, Math.min(100, Math.round(task.progress ?? 0))),
      result,
      requestId: "",
      createdRequestId: "",
      errorCode: task.error || "",
      target: "",
      targetName: task.taskGroup || "",
      updatedAt: task.endTime || task.startTime || new Date().toISOString(),
      transferredBytes: 0,
      totalBytes: Math.max(0, Number(task.totalBytes ?? 0)),
    };
  }

  private static startTaskStreamWithFetch(): void {
    const run = async () => {
      while (this.taskStreamListeners.size > 0) {
        const controller = new AbortController();
        this.taskStreamAbortController = controller;
        try {
          const resp = await fetch("/api/v4/transfers/tasks/stream", {
            method: "GET",
            headers: withAuthHeader({ Accept: "text/event-stream" }),
            signal: controller.signal,
          });
          if (!resp.ok || !resp.body) throw new Error(`sse stream failed: ${resp.status}`);
          const reader = resp.body.getReader();
          const decoder = new TextDecoder();
          let buffer = "";
          while (this.taskStreamListeners.size > 0) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const chunks = buffer.split("\n\n");
            buffer = chunks.pop() ?? "";
            for (const raw of chunks) {
              const lines = raw.split("\n");
              let eventType = "message";
              const dataLines: string[] = [];
              for (const line of lines) {
                if (line.startsWith("event:")) eventType = line.slice(6).trim();
                if (line.startsWith("data:")) dataLines.push(line.slice(5).trim());
              }
              if (eventType !== "task" || dataLines.length === 0) continue;
              try {
                const event = JSON.parse(dataLines.join("\n")) as V4TaskStreamEvent;
                this.gcTaskDeleteTombstones();
                if (this.taskDeletedTombstoneAt.has(event.taskId)) continue;
                const key = `${event.taskId}#${event.updatedAt || ""}`;
                if (this.taskCacheVersion.get(event.taskId) === key) continue;
                this.taskCacheVersion.set(event.taskId, key);
                const current = this.taskCache.get(event.taskId);
                const next: TaskInfo = {
                  taskId: event.taskId,
                  action: current?.action || "transfer",
                  actionLabel: current?.actionLabel || "transfer",
                  status: (event.state || "").toUpperCase(),
                  progress: Math.max(0, Math.min(100, Math.round(event.progress ?? 0))),
                  result: (event.state || "").toUpperCase(),
                  requestId: current?.requestId || "",
                  createdRequestId: current?.createdRequestId || "",
                  errorCode: event.errorCode || "",
                  target: current?.target || "",
                  targetName: current?.targetName || "",
                  updatedAt: event.updatedAt || new Date().toISOString(),
                  transferredBytes: Math.max(0, Number(event.transferredBytes ?? 0)),
                  totalBytes: Math.max(0, Number(event.totalBytes ?? current?.totalBytes ?? 0)),
                  speedBytesPerSec: Math.max(0, Number(event.speedBytesPerSec ?? 0)),
                  etaSeconds: Number(event.etaSeconds ?? -1),
                };
                this.taskCache.set(next.taskId, next);
                this.scheduleTaskStreamEmit();
              } catch {
                // ignore malformed task event
              }
            }
          }
        } catch {
          // reconnect on transient network/auth handshake errors
        } finally {
          if (this.taskStreamAbortController === controller) this.taskStreamAbortController = null;
        }
        if (this.taskStreamListeners.size > 0) {
          await new Promise((resolve) => globalThis.setTimeout(resolve, 1500));
        }
      }
    };
    void run();
  }

  public static async fetchBinary(sourceURL: string): Promise<Response> {
    return await fetch(sourceURL);
  }
}

function targetPathFromBatch(paths: string[]): string {
  return paths.length > 0 ? paths[0] : ".";
}



