export type TransferDirection = "upload" | "download";

export type TransferRateSnapshot = {
  uploadBps: number;
  downloadBps: number;
};

type SampleState = {
  loadedBytes: number;
  tsMs: number;
  pendingBytes: number;
};

const listeners = new Set<(snapshot: TransferRateSnapshot) => void>();
const sampleState: Record<TransferDirection, SampleState | null> = {
  upload: null,
  download: null,
};

let snapshot: TransferRateSnapshot = { uploadBps: 0, downloadBps: 0 };
let decayTimer: number | null = null;

function emitSnapshot(): void {
  for (const listener of listeners) listener(snapshot);
}

function ensureDecayTimer(): void {
  if (decayTimer !== null) return;
  decayTimer = globalThis.setInterval(() => {
    const nextUpload = snapshot.uploadBps < 1 ? 0 : snapshot.uploadBps * 0.72;
    const nextDownload = snapshot.downloadBps < 1 ? 0 : snapshot.downloadBps * 0.72;
    if (nextUpload === snapshot.uploadBps && nextDownload === snapshot.downloadBps) return;
    snapshot = { uploadBps: nextUpload, downloadBps: nextDownload };
    emitSnapshot();
  }, 500) as unknown as number;
}

function stopDecayTimerIfIdle(): void {
  if (listeners.size > 0) return;
  if (decayTimer === null) return;
  globalThis.clearInterval(decayTimer);
  decayTimer = null;
}

/**
 * 本地速率采样：基于同一传输方向的 loaded 增量估算瞬时吞吐，并做指数平滑。
 */
export function reportTransferSample(direction: TransferDirection, loadedBytes: number): void {
  const now = Date.now();
  const prev = sampleState[direction];
  if (!prev) {
    sampleState[direction] = { loadedBytes: Math.max(0, loadedBytes), tsMs: now, pendingBytes: 0 };
    ensureDecayTimer();
    return;
  }
  const deltaBytes = Math.max(0, loadedBytes - prev.loadedBytes);
  const elapsedMs = now - prev.tsMs;
  const pendingBytes = prev.pendingBytes + deltaBytes;
  // 采样窗口过短时先聚合，避免出现 1ms 级抖动导致的速率尖刺。
  if (!Number.isFinite(elapsedMs) || elapsedMs < 200) {
    sampleState[direction] = { loadedBytes: Math.max(0, loadedBytes), tsMs: prev.tsMs, pendingBytes };
    ensureDecayTimer();
    return;
  }
  const deltaSeconds = elapsedMs / 1000;
  sampleState[direction] = { loadedBytes: Math.max(0, loadedBytes), tsMs: now, pendingBytes: 0 };
  if (!Number.isFinite(deltaSeconds) || deltaSeconds <= 0) return;
  const instantBps = pendingBytes / deltaSeconds;
  const prevBps = direction === "upload" ? snapshot.uploadBps : snapshot.downloadBps;
  const smoothedBps = prevBps * 0.35 + instantBps * 0.65;
  snapshot = direction === "upload"
    ? { ...snapshot, uploadBps: smoothedBps }
    : { ...snapshot, downloadBps: smoothedBps };
  ensureDecayTimer();
  emitSnapshot();
}

/** 开始新传输前重置方向采样基线，避免跨任务速度突刺。 */
export function resetTransferSample(direction: TransferDirection): void {
  sampleState[direction] = null;
  snapshot = direction === "upload"
    ? { ...snapshot, uploadBps: 0 }
    : { ...snapshot, downloadBps: 0 };
  emitSnapshot();
}

/** 订阅本地上传/下载速率快照；用于侧边栏等只读展示，卸载时需调用返回函数释放监听。 */
export function subscribeTransferRate(listener: (snapshot: TransferRateSnapshot) => void): () => void {
  listeners.add(listener);
  ensureDecayTimer();
  listener(snapshot);
  return () => {
    listeners.delete(listener);
    stopDecayTimerIfIdle();
  };
}
