import type { SystemTelemetrySnapshot } from "../../../../controllers/system";

export type MonitorTopCardContext = {
  overview: SystemTelemetrySnapshot | null;
  cpuPercent: number;
  memUsedPercent: number;
  diskUsedPercent: number;
  totalUploadBps: number;
  totalDownloadBps: number;
  totalTrafficBytes: number;
  totalActiveUploadTasks: number;
  totalActiveDownloadTasks: number;
  diskWriteEstimateBps: number;
  cpuTrend: { label: string; user: number; total: number }[];
  networkTrend: { label: string; recv: number; send: number }[];
  diskIoTrend: { label: string; writes: number; reads: number }[];
  formatRate: (bps: number) => string;
  formatBytes: (bytes: number) => string;
  formatStorage: (bytes: number) => string;
  deltaText: (current: number, baseline: number) => string;
  cpuBaseline: number;
  isDark: boolean;
};
