import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import SystemController, { type AdminDashboardResponse, type SystemTelemetrySnapshot } from "../../../controllers/system";

export type TransferUserRow = {
  username: string;
  uploadBps: number;
  downloadBps: number;
  activeUploadTasks: number;
  activeDownloadTasks: number;
};

export type TransferTrendPoint = {
  timestamp: string;
  uploadBps: number;
  downloadBps: number;
};

export type TimeWindow = 3 | 60 | 1440;

/** 监控数据组合器：先拉首帧，再持续刷新，贴近 nginx-ui 的“初始化 + 实时更新”模式。 */
export function useMonitorData(isRoot: boolean, windowMinutes: TimeWindow) {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [overview, setOverview] = useState<SystemTelemetrySnapshot | null>(null);
  const [history, setHistory] = useState<SystemTelemetrySnapshot[]>([]);
  const [userTransferStats, setUserTransferStats] = useState<TransferUserRow[]>([]);
  const [totalUploadBps, setTotalUploadBps] = useState(0);
  const [totalDownloadBps, setTotalDownloadBps] = useState(0);
  const [transferTrend, setTransferTrend] = useState<TransferTrendPoint[]>([]);
  const [selectedUser, setSelectedUser] = useState<string | null>(null);
  const [selectedUserTrendByUser, setSelectedUserTrendByUser] = useState<Record<string, TransferTrendPoint[]>>({});

  const fetchInitialData = useCallback(async (minutes: TimeWindow) => {
    if (!isRoot) return;
    setLoading(true);
    try {
      const payload: AdminDashboardResponse = await SystemController.adminDashboard(minutes);
      setOverview(payload.overview);
      setHistory(payload.history);
      setUserTransferStats(payload.users);
      const nextTotalUploadBps = payload.users.reduce((sum: number, item: TransferUserRow) => sum + Math.max(0, item.uploadBps), 0);
      const nextTotalDownloadBps = payload.users.reduce((sum: number, item: TransferUserRow) => sum + Math.max(0, item.downloadBps), 0);
      setTotalUploadBps(nextTotalUploadBps);
      setTotalDownloadBps(nextTotalDownloadBps);
      const now = new Date().toISOString();
      setTransferTrend((currentTrend) => [...currentTrend, { timestamp: now, uploadBps: nextTotalUploadBps, downloadBps: nextTotalDownloadBps }].slice(-40));
      if (selectedUser) {
        const selected = payload.users.find((item) => item.username === selectedUser);
        if (selected) {
          setSelectedUserTrendByUser((currentCache) => {
            const currentTrend = currentCache[selectedUser] ?? [];
            return {
              ...currentCache,
              [selectedUser]: [...currentTrend, {
                timestamp: now,
                uploadBps: selected.uploadBps,
                downloadBps: selected.downloadBps,
              }].slice(-40),
            };
          });
        }
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("debug.loadFailed"));
    } finally {
      setLoading(false);
    }
  }, [isRoot, selectedUser, t]);

  const selectUser = useCallback((username: string) => {
    setSelectedUser(username);
    const now = new Date().toISOString();
    const selected = userTransferStats.find((item) => item.username === username);
    if (!selected) {
      return;
    }
    setSelectedUserTrendByUser((currentCache) => {
      const currentTrend = currentCache[username] ?? [];
      if (currentTrend.length > 0) return currentCache;
      return {
        ...currentCache,
        [username]: [{ timestamp: now, uploadBps: selected.uploadBps, downloadBps: selected.downloadBps }],
      };
    });
  }, [userTransferStats]);

  useEffect(() => {
    void fetchInitialData(windowMinutes);
    const timer = window.setInterval(() => {
      void fetchInitialData(windowMinutes);
    }, 5000);
    return () => window.clearInterval(timer);
  }, [fetchInitialData, windowMinutes]);

  return {
    loading,
    overview,
    history,
    userTransferStats,
    totalUploadBps,
    totalDownloadBps,
    transferTrend,
    selectedUser,
    selectUser,
    selectedUserTrend: selectedUser ? (selectedUserTrendByUser[selectedUser] ?? []) : [],
    fetchInitialData,
  };
}
