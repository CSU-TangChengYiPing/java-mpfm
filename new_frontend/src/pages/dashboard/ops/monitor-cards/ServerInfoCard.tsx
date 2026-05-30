import { FiServer } from "react-icons/fi";
import { useTranslation } from "react-i18next";
import type { MonitorTopCardContext } from "./types";
import MonitorMetricCard from "./MonitorMetricCard";

function formatUptime(seconds: number): string {
  const day = Math.floor(seconds / 86400);
  const hour = Math.floor((seconds % 86400) / 3600);
  const min = Math.floor((seconds % 3600) / 60);
  return `${day}d ${hour}h ${min}m`;
}

export default function ServerInfoCard({ overview }: Pick<MonitorTopCardContext, "overview">) {
  const { t } = useTranslation();
  return (
    <MonitorMetricCard title={t("monitor.serverInfoCard")} icon={<FiServer size={18} />}>
      <p className="text-sm leading-7 text-default-700 dark:text-white">{t("monitor.uptime")}: {overview ? formatUptime(Math.floor(overview.uptimeMs / 1000)) : "-"}</p>
      <p className="text-sm leading-7 text-default-700 dark:text-white">{t("monitor.loadAverage")}: 1min: {overview?.load1?.toFixed(2) ?? "0.00"} | 5min: {overview?.load5?.toFixed(2) ?? "0.00"} | 15min: {overview?.load15?.toFixed(2) ?? "0.00"}</p>
      <p className="text-sm leading-7 text-default-700 dark:text-white">OS: {overview ? `${overview.osName} ${overview.osVersion} (${overview.osArch})` : "-"}</p>
      <p className="text-sm leading-7 text-default-700 dark:text-white">CPU: {overview ? `${overview.cpuModel || "Core"} * ${overview.cpuCores}` : "-"}</p>
    </MonitorMetricCard>
  );
}
