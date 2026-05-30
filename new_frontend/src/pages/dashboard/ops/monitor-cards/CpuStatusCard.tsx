import { FiCpu } from "react-icons/fi";
import { useTranslation } from "react-i18next";
import type { MonitorTopCardContext } from "./types";
import MonitorMetricCard from "./MonitorMetricCard";
import TrendChart from "./TrendChart";

export default function CpuStatusCard({ overview, cpuTrend, isDark }: Pick<MonitorTopCardContext, "overview" | "cpuTrend" | "isDark">) {
  const { t } = useTranslation();
  const latest = cpuTrend[cpuTrend.length - 1];
  const cpuValue = overview ? Number((overview.cpuLoad * 100).toFixed(2)) : 0;
  return (
    <MonitorMetricCard title={t("monitor.cpuStatusCard")} icon={<FiCpu size={18} />}>
      <div className="flex flex-wrap items-baseline gap-3">
        <p className="text-4xl font-semibold tracking-tight text-default-800 dark:text-white">{overview ? `${cpuValue.toFixed(2)}%` : "-"}</p>
        <p className="text-sm text-default-500 dark:text-white">User: {latest ? `${latest.user.toFixed(2)}%` : "-"}</p>
        <p className="text-sm text-default-500 dark:text-white">Total: {latest ? `${latest.total.toFixed(2)}%` : "-"}</p>
      </div>
      <TrendChart
        data={cpuTrend}
        xKey="label"
        series={[
          { key: "user", name: "User", color: "#10b981", fill: "#10b981" },
          { key: "total", name: "Total", color: "#3b82f6", fill: "#3b82f6" },
        ]}
        yUnit="%"
        isDark={isDark}
      />
    </MonitorMetricCard>
  );
}
