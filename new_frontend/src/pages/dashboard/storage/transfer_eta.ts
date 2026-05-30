/** 根据剩余字节与当前速率估算剩余秒数；速率不可用时返回 null 供 UI 展示占位。 */
export function estimateRemainingSeconds(remainingBytes: number, speedBps: number): number | null {
  if (!Number.isFinite(remainingBytes) || remainingBytes <= 0) return 0;
  if (!Number.isFinite(speedBps) || speedBps <= 0) return null;
  return Math.max(0, Math.floor(remainingBytes / speedBps));
}

/** 将剩余秒数格式化为 HH:MM:SS 倒计时，非法值回退为占位符。 */
export function formatHmsCountdown(totalSeconds: number | null): string {
  if (totalSeconds === null || !Number.isFinite(totalSeconds) || totalSeconds < 0) return "--:--:--";
  const safeSeconds = Math.floor(totalSeconds);
  const hours = Math.floor(safeSeconds / 3600);
  const minutes = Math.floor((safeSeconds % 3600) / 60);
  const seconds = safeSeconds % 60;
  const hh = String(hours).padStart(2, "0");
  const mm = String(minutes).padStart(2, "0");
  const ss = String(seconds).padStart(2, "0");
  return `${hh}:${mm}:${ss}`;
}

type EtaSample = {
  tsMs: number;
  loadedBytes: number;
};

/**
 * 创建滚动 ETA 估算器：仅使用最近 1~30 秒采样窗口估算速度，避免“全程均速”导致的迟钝。
 * 说明：返回值为估算剩余秒数；当窗口数据不足（<1 秒）或速度无效时返回 null。
 */
export function createRollingEtaEstimator(minWindowSeconds = 1, maxWindowSeconds = 30): (loadedBytes: number, totalBytes: number, nowMs?: number) => number | null {
  const samples: EtaSample[] = [];
  return (loadedBytes: number, totalBytes: number, nowMs = Date.now()) => {
    samples.push({ tsMs: nowMs, loadedBytes: Math.max(0, loadedBytes) });
    const maxWindowMs = Math.max(minWindowSeconds, maxWindowSeconds) * 1000;
    const minTs = nowMs - maxWindowMs;
    while (samples.length > 0 && samples[0].tsMs < minTs) samples.shift();
    if (samples.length < 2) return null;

    const speedCandidates: number[] = [];
    for (const sample of samples) {
      const ageSeconds = (nowMs - sample.tsMs) / 1000;
      if (ageSeconds < minWindowSeconds || ageSeconds > maxWindowSeconds) continue;
      const deltaBytes = Math.max(0, loadedBytes - sample.loadedBytes);
      if (deltaBytes <= 0) continue;
      const bps = deltaBytes / ageSeconds;
      if (Number.isFinite(bps) && bps > 0) speedCandidates.push(bps);
    }
    if (speedCandidates.length === 0) return null;
    speedCandidates.sort((a, b) => a - b);
    const mid = Math.floor(speedCandidates.length / 2);
    const medianBps = speedCandidates.length % 2 === 0
      ? (speedCandidates[mid - 1] + speedCandidates[mid]) / 2
      : speedCandidates[mid];
    return estimateRemainingSeconds(Math.max(0, totalBytes - loadedBytes), medianBps);
  };
}
