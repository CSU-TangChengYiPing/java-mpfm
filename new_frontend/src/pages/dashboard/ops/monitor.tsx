import { Button } from "@heroui/button";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Table, TableBody, TableCell, TableColumn, TableHeader, TableRow } from "@heroui/table";
import { Tab, Tabs } from "@heroui/tabs";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FiTrendingUp, FiUsers } from "react-icons/fi";
import {
  Area,
  ComposedChart,
  CartesianGrid,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useMonitorData, type TimeWindow } from "./useMonitorData";
import { useAuth } from "../../../hooks/useAuth";
import RootOnlyNoticeCard from "../../../components/common/RootOnlyNoticeCard";
import CpuStatusCard from "./monitor-cards/CpuStatusCard";
import DiskIoCard from "./monitor-cards/DiskIoCard";
import MemoryStorageCard from "./monitor-cards/MemoryStorageCard";
import NetworkCard from "./monitor-cards/NetworkCard";
import ServerInfoCard from "./monitor-cards/ServerInfoCard";
import TrafficStatsCard from "./monitor-cards/TrafficStatsCard";
import { formatRateBps } from "../../../utils/rateFormat";

function formatTimeLabel(timestamp: string, windowMinutes: TimeWindow): string {
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return timestamp;
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  const ss = String(date.getSeconds()).padStart(2, "0");
  if (windowMinutes >= 1440) return `${month}-${day} ${hh}:${mm}`;
  if (windowMinutes <= 3) return `${hh}:${mm}:${ss}`;
  return `${hh}:${mm}`;
}

function formatTimeWithSecond(timestamp: string): string {
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return timestamp;
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  const ss = String(date.getSeconds()).padStart(2, "0");
  return `${hh}:${mm}:${ss}`;
}

/** 监控中心页：按 Grafana 常见分层重排为“核心指标 -> 趋势与容量 -> 热点用户”。 */
export default function MonitorPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [windowMinutes, setWindowMinutes] = useState<TimeWindow>(3);
  const [isDark, setIsDark] = useState(false);
  const rateUnitLabels = useMemo(() => ({
    B: t("common.rateUnits.b"),
    KB: t("common.rateUnits.kb"),
    MB: t("common.rateUnits.mb"),
    GB: t("common.rateUnits.gb"),
  }), [t]);
  const {
    loading,
    overview,
    history,
    userTransferStats,
    transferTrend,
    selectedUser,
    selectUser,
    selectedUserTrend,
    fetchInitialData,
  } = useMonitorData(Boolean(user?.is_root), windowMinutes);

  useEffect(() => {
    setIsDark(document.documentElement.classList.contains("dark"));
    const observer = new MutationObserver(() => {
      setIsDark(document.documentElement.classList.contains("dark"));
    });
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
    return () => observer.disconnect();
  }, []);

  const formatRate = (bps: number) => {
    return formatRateBps(bps, { labels: rateUnitLabels });
  };
  const formatBytes = (bytes: number) => {
    const value = Math.max(0, bytes);
    if (value >= 1024 ** 4) return `${(value / (1024 ** 4)).toFixed(2)} TB`;
    if (value >= 1024 ** 3) return `${(value / (1024 ** 3)).toFixed(2)} GB`;
    if (value >= 1024 ** 2) return `${(value / (1024 ** 2)).toFixed(2)} MB`;
    if (value >= 1024) return `${(value / 1024).toFixed(2)} KB`;
    return `${value} B`;
  };

  const formatStorage = (bytes: number) => `${(bytes / (1024 ** 3)).toFixed(2)} GB`;

  const memUsedPercent = overview ? ((overview.totalMemBytes - overview.freeMemBytes) / Math.max(1, overview.totalMemBytes)) * 100 : 0;
  const diskUsedPercent = overview ? ((overview.diskTotalBytes - overview.diskUsableBytes) / Math.max(1, overview.diskTotalBytes)) * 100 : 0;

  const cpuTrend = useMemo(() => history.map((item) => ({
    label: formatTimeLabel(item.timestamp, windowMinutes),
    user: Number((Math.max(0, item.processCpuLoad) * 100).toFixed(2)),
    total: Number((Math.max(0, item.cpuLoad) * 100).toFixed(2)),
  })), [history, windowMinutes]);
  const diskIoTrend = useMemo(() => {
    if (history.length < 2) return [];
    const result: { label: string; writes: number; reads: number }[] = [];
    for (let i = 1; i < history.length; i += 1) {
      const prev = history[i - 1];
      const cur = history[i];
      const dt = Math.max(1, (new Date(cur.timestamp).getTime() - new Date(prev.timestamp).getTime()) / 1000);
      result.push({
        label: formatTimeLabel(cur.timestamp, windowMinutes),
        writes: Math.max(0, (cur.diskWriteBytes - prev.diskWriteBytes) / dt),
        reads: Math.max(0, (cur.diskReadBytes - prev.diskReadBytes) / dt),
      });
    }
    return result;
  }, [history, windowMinutes]);

  const userTimelineData = useMemo(() => {
    return [...selectedUserTrend]
      .map((item) => {
      const ts = new Date(item.timestamp).getTime();
      if (Number.isNaN(ts)) return null;
      const iso = new Date(ts).toISOString();
      return {
        timestamp: iso,
        label: formatTimeLabel(iso, windowMinutes),
        uploadBps: Math.max(0, item.uploadBps),
        downloadBps: Math.max(0, item.downloadBps),
      };
    })
      .filter((item): item is { label: string; timestamp: string; uploadBps: number; downloadBps: number } => item !== null)
      .sort((left, right) => new Date(left.timestamp).getTime() - new Date(right.timestamp).getTime());
  }, [selectedUserTrend, windowMinutes]);

  const topUsers = useMemo(
    () => [...userTransferStats]
      .sort((left, right) => (right.uploadBps + right.downloadBps) - (left.uploadBps + left.downloadBps))
      .slice(0, 8),
    [userTransferStats],
  );

  if (!user?.is_root) return <RootOnlyNoticeCard message={t("auth.rootOnlyDebug")} />;

  return (
    <div className="h-full overflow-y-auto p-4">
      <div className="mx-auto flex min-h-full w-full max-w-[1600px] flex-col gap-4">
      <div className="rounded-sm border border-white/40 bg-white/60 px-4 py-3 shadow-sm backdrop-blur-xl dark:border-white/10 dark:bg-black/40">
        <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-default-800 dark:text-white">{t("monitor.title")}</h2>
        <div className="flex items-center gap-2">
          <Tabs
            aria-label={t("monitor.windowTabs")}
            size="sm"
            selectedKey={String(windowMinutes)}
            onSelectionChange={(k) => setWindowMinutes(Number(String(k)) as TimeWindow)}
          >
            <Tab key="3" title={t("monitor.range3m")} />
            <Tab key="60" title={t("monitor.range1h")} />
            <Tab key="1440" title={t("monitor.range1d")} />
          </Tabs>
          <Button size="sm" variant="flat" isLoading={loading} onPress={() => void fetchInitialData(windowMinutes)}>{t("common.refresh")}</Button>
        </div>
      </div>
      </div>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
        <ServerInfoCard overview={overview} />
        <MemoryStorageCard overview={overview} memUsedPercent={memUsedPercent} diskUsedPercent={diskUsedPercent} formatStorage={formatStorage} />
        <TrafficStatsCard overview={overview} formatBytes={formatBytes} />
        <CpuStatusCard overview={overview} cpuTrend={cpuTrend} isDark={isDark} />
        <NetworkCard transferTrend={transferTrend} isDark={isDark} />
        <DiskIoCard diskIoTrend={diskIoTrend} isDark={isDark} />
      </div>
{/* 
      <SystemPerformanceTablesCard
        overview={overview}
        totalTrafficBytes={totalTrafficBytes}
        totalUploadBps={totalUploadBps}
        totalDownloadBps={totalDownloadBps}
        totalActiveUploadTasks={totalActiveUploadTasks}
        totalActiveDownloadTasks={totalActiveDownloadTasks}
      /> */}
{/* 
      <div className="grid grid-cols-1 gap-3 xl:grid-cols-3">
        <Card className="border border-white/40 bg-white/60 shadow-sm backdrop-blur-xl dark:border-white/10 dark:bg-black/40 xl:col-span-2">
          <CardHeader className="text-sm text-default-700 dark:text-white">{t("monitor.history")}</CardHeader>
          <CardBody className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={historyChartData}>
                <defs>
                  <linearGradient id="cpuArea" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#2563eb" stopOpacity={0.28} />
                    <stop offset="95%" stopColor="#2563eb" stopOpacity={0.04} />
                  </linearGradient>
                  <linearGradient id="memArea" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#f59e0b" stopOpacity={0.24} />
                    <stop offset="95%" stopColor="#f59e0b" stopOpacity={0.04} />
                  </linearGradient>
                  <linearGradient id="diskArea" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#ef4444" stopOpacity={0.22} />
                    <stop offset="95%" stopColor="#ef4444" stopOpacity={0.04} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(120,120,120,0.2)" />
                <XAxis dataKey="label" tick={{ fontSize: 11 }} minTickGap={18} />
                <YAxis tick={{ fontSize: 11 }} domain={[0, 100]} unit="%" />
                <Tooltip
                  labelFormatter={(_, payload) => {
                    const first = payload?.[0]?.payload as { timestamp?: string } | undefined;
                    return first?.timestamp ? formatTimeWithSecond(first.timestamp) : String(_);
                  }}
                  contentStyle={{
                    backgroundColor: isDark ? "rgba(20,20,22,0.92)" : "rgba(255,255,255,0.95)",
                    border: isDark ? "1px solid rgba(255,255,255,0.16)" : "1px solid rgba(15,23,42,0.12)",
                    borderRadius: 8,
                    color: isDark ? "#e5e7eb" : "#0f172a",
                  }}
                  labelStyle={{ color: isDark ? "#f3f4f6" : "#111827" }}
                  itemStyle={{ color: isDark ? "#d1d5db" : "#1f2937" }}
                />
                <Legend />
                <ReferenceLine y={75} stroke="#f59e0b" strokeDasharray="4 4" />
                <ReferenceLine y={90} stroke="#ef4444" strokeDasharray="4 4" />
                <Area type="monotone" dataKey="cpu" fill="url(#cpuArea)" stroke="none" isAnimationActive={false} />
                <Line type="monotone" dataKey="cpu" stroke="#2563eb" dot={false} name={t("monitor.cpuTrend")} strokeWidth={2} isAnimationActive={false} />
                <Area type="monotone" dataKey="mem" fill="url(#memArea)" stroke="none" isAnimationActive={false} />
                <Line type="monotone" dataKey="mem" stroke="#f59e0b" dot={false} name={t("monitor.memTrend")} strokeWidth={2} isAnimationActive={false} />
                <Area type="monotone" dataKey="disk" fill="url(#diskArea)" stroke="none" isAnimationActive={false} />
                <Line type="monotone" dataKey="disk" stroke="#ef4444" dot={false} name={t("monitor.diskTrend")} strokeWidth={2} isAnimationActive={false} />
              </ComposedChart>
            </ResponsiveContainer>
          </CardBody>
        </Card>

        <Card className="border border-white/40 bg-white/60 shadow-sm backdrop-blur-xl dark:border-white/10 dark:bg-black/40">
          <CardHeader className="text-sm text-default-700 dark:text-white">{t("monitor.globalTraffic")}</CardHeader>
          <CardBody className="flex flex-col gap-3">
            <div>
              <p className="text-xs text-default-500 dark:text-white">{t("monitor.uploadThroughput")}</p>
              <p className="text-lg font-semibold text-default-800 dark:text-white">{formatRate(totalUploadBps)}</p>
            </div>
            <div>
              <p className="text-xs text-default-500 dark:text-white">{t("monitor.downloadThroughput")}</p>
              <p className="text-lg font-semibold text-default-800 dark:text-white">{formatRate(totalDownloadBps)}</p>
            </div>
            <div className="text-xs text-default-500 dark:text-white">{t("monitor.refreshHint")}</div>
          </CardBody>
        </Card>
      </div> */}

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
        <Card className="border border-white/40 bg-white/60 shadow-sm backdrop-blur-xl dark:border-white/10 dark:bg-black/40">
          <CardHeader className="flex items-center gap-2 text-sm text-default-800 dark:text-white">
            <span className="mr-2 text-default-500 dark:text-white"><FiUsers size={16} /></span>
            {t("monitor.topUsers")}
          </CardHeader>
          <CardBody className="pt-0">
            <div className="overflow-hidden rounded-medium border border-default-200/80 dark:border-white/10">
              <Table aria-label={t("monitor.topUsers")} removeWrapper classNames={{ table: "min-h-[240px]" }}>
                <TableHeader>
                  <TableColumn className="bg-default-50/80 text-[11px] font-medium uppercase tracking-wide text-default-500 dark:bg-white/5 dark:text-white">{t("users.columns.username")}</TableColumn>
                  <TableColumn className="bg-default-50/80 text-[11px] font-medium uppercase tracking-wide text-default-500 dark:bg-white/5 dark:text-white">{t("monitor.userUpload")}</TableColumn>
                  <TableColumn className="bg-default-50/80 text-[11px] font-medium uppercase tracking-wide text-default-500 dark:bg-white/5 dark:text-white">{t("monitor.userDownload")}</TableColumn>
                  <TableColumn className="bg-default-50/80 text-[11px] font-medium uppercase tracking-wide text-default-500 dark:bg-white/5 dark:text-white">{t("monitor.activeTasks")}</TableColumn>
                  <TableColumn className="bg-default-50/80 text-[11px] font-medium uppercase tracking-wide text-default-500 dark:bg-white/5 dark:text-white">{t("monitor.drilldown")}</TableColumn>
                </TableHeader>
                <TableBody emptyContent={t("common.empty")}>
                  {topUsers.map((item) => (
                    <TableRow key={item.username}>
                      <TableCell>{item.username}</TableCell>
                      <TableCell>{formatRate(item.uploadBps)}</TableCell>
                      <TableCell>{formatRate(item.downloadBps)}</TableCell>
                      <TableCell>{item.activeUploadTasks}/{item.activeDownloadTasks}</TableCell>
                      <TableCell>
                        <Button
                          size="sm"
                          variant={selectedUser === item.username ? "solid" : "flat"}
                          onPress={() => selectUser(item.username)}
                        >
                          {t("monitor.viewUserTrend")}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </CardBody>
        </Card>

        <Card className="border border-white/40 bg-white/60 shadow-sm backdrop-blur-xl dark:border-white/10 dark:bg-black/40">
          <CardHeader className="flex items-center gap-2 text-sm text-default-800 dark:text-white">
            <span className="mr-2 text-default-500 dark:text-white"><FiTrendingUp size={16} /></span>
            {t("monitor.userTimelineTitle", { username: selectedUser ?? "-" })}
          </CardHeader>
          <CardBody className="h-72">
            {selectedUser ? (
              <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={userTimelineData}>
                  <defs>
                    <linearGradient id="uploadArea" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#10b981" stopOpacity={0.24} />
                      <stop offset="95%" stopColor="#10b981" stopOpacity={0.04} />
                    </linearGradient>
                    <linearGradient id="downloadArea" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.24} />
                      <stop offset="95%" stopColor="#3b82f6" stopOpacity={0.04} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(120,120,120,0.2)" />
                  <XAxis
                    dataKey="timestamp"
                    tick={{ fontSize: 11 }}
                    minTickGap={18}
                    tickFormatter={(value) => formatTimeLabel(String(value), windowMinutes)}
                  />
                  <YAxis tick={{ fontSize: 11 }} tickFormatter={(value) => formatRate(Number(value))} />
                  <Tooltip
                    labelFormatter={(value) => formatTimeWithSecond(String(value))}
                    contentStyle={{
                      backgroundColor: isDark ? "rgba(20,20,22,0.92)" : "rgba(255,255,255,0.95)",
                      border: isDark ? "1px solid rgba(255,255,255,0.16)" : "1px solid rgba(15,23,42,0.12)",
                      borderRadius: 8,
                      color: isDark ? "#e5e7eb" : "#0f172a",
                    }}
                    labelStyle={{ color: isDark ? "#f3f4f6" : "#111827" }}
                    itemStyle={{ color: isDark ? "#d1d5db" : "#1f2937" }}
                  />
                  <Legend />
                  <Area type="monotone" dataKey="uploadBps" fill="url(#uploadArea)" stroke="none" isAnimationActive={false} />
                  <Line type="monotone" dataKey="uploadBps" stroke="#10b981" dot={{ r: 2 }} name={t("monitor.userUpload")} strokeWidth={2} isAnimationActive={false} />
                  <Area type="monotone" dataKey="downloadBps" fill="url(#downloadArea)" stroke="none" isAnimationActive={false} />
                  <Line type="monotone" dataKey="downloadBps" stroke="#3b82f6" dot={{ r: 2 }} name={t("monitor.userDownload")} strokeWidth={2} isAnimationActive={false} />
                </ComposedChart>
              </ResponsiveContainer>
            ) : (
              <div className="text-sm text-default-500 dark:text-white">{t("monitor.pickUserHint")}</div>
            )}
          </CardBody>
        </Card>
      </div>
{/* 
      <Card className="border border-white/40 bg-white/60 shadow-sm backdrop-blur-xl dark:border-white/10 dark:bg-black/40">
        <CardHeader className="text-sm text-default-700 dark:text-white">{t("monitor.abnormalUsers")}</CardHeader>
        <CardBody>
          <Table aria-label={t("monitor.abnormalUsers")} removeWrapper classNames={{ table: "min-h-[200px]" }}>
            <TableHeader>
              <TableColumn>{t("users.columns.username")}</TableColumn>
              <TableColumn>{t("monitor.userUpload")}</TableColumn>
              <TableColumn>{t("monitor.userDownload")}</TableColumn>
              <TableColumn>{t("monitor.activeTasks")}</TableColumn>
              <TableColumn>{t("common.actions")}</TableColumn>
            </TableHeader>
            <TableBody emptyContent={t("common.empty")}>
              {abnormalUsers.map((item) => (
                <TableRow key={`abnormal-${item.username}`}>
                  <TableCell>{item.username}</TableCell>
                  <TableCell>{formatRate(item.uploadBps)}</TableCell>
                  <TableCell>{formatRate(item.downloadBps)}</TableCell>
                  <TableCell>{item.activeUploadTasks}/{item.activeDownloadTasks}</TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-1">
                      <Button
                        size="sm"
                        variant="flat"
                        color="warning"
                        isLoading={governLoading === `${item.username}:PAUSE_UPLOAD`}
                        onPress={() => void onGovern(item.username, "PAUSE_UPLOAD")}
                      >
                        {t("users.qos.pauseUpload")}
                      </Button>
                      <Button
                        size="sm"
                        variant="flat"
                        color="success"
                        isLoading={governLoading === `${item.username}:RESUME_UPLOAD`}
                        onPress={() => void onGovern(item.username, "RESUME_UPLOAD")}
                      >
                        {t("users.qos.resumeUpload")}
                      </Button>
                      <Button
                        size="sm"
                        variant="flat"
                        color="danger"
                        isLoading={governLoading === `${item.username}:KICK_UPLOAD_TASKS`}
                        onPress={() => void onGovern(item.username, "KICK_UPLOAD_TASKS")}
                      >
                        {t("users.qos.kickUpload")}
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardBody>
      </Card> */}
      </div>
    </div>
  );
}
