import { FiBarChart2 } from "react-icons/fi";
import { useTranslation } from "react-i18next";
import type { MonitorTopCardContext } from "./types";
import MonitorMetricCard from "./MonitorMetricCard";

export default function TrafficStatsCard({ overview, formatBytes }: Pick<MonitorTopCardContext, "overview" | "formatBytes">) {
  const { t } = useTranslation();
  return (
    <MonitorMetricCard title={t("monitor.trafficStatsCard")} icon={<FiBarChart2 size={18} />}>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <p className="text-xs text-default-500 dark:text-white">{t("monitor.downloadThroughput")}</p>
          <p className="text-2xl font-semibold text-default-800 dark:text-white">{overview ? formatBytes(overview.networkRxBytesSinceStartup) : "-"}</p>
        </div>
        <div>
          <p className="text-xs text-default-500 dark:text-white">{t("monitor.uploadThroughput")}</p>
          <p className="text-2xl font-semibold text-default-800 dark:text-white">{overview ? formatBytes(overview.networkTxBytesSinceStartup) : "-"}</p>
        </div>
      </div>
      <p className="mt-3 text-xs text-default-500 dark:text-white">{t("monitor.trafficStatsHint")}</p>
    </MonitorMetricCard>
  );
}
