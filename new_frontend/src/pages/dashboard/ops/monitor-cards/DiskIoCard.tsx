import { FiHardDrive } from "react-icons/fi";
import { useTranslation } from "react-i18next";
import type { MonitorTopCardContext } from "./types";
import MonitorMetricCard from "./MonitorMetricCard";
import TrendChart from "./TrendChart";

export default function DiskIoCard({ diskIoTrend, isDark }: Pick<MonitorTopCardContext, "diskIoTrend" | "isDark">) {
  const { t } = useTranslation();
  const latest = diskIoTrend[diskIoTrend.length - 1];
  return (
    <MonitorMetricCard title={t("monitor.diskIoCard")} icon={<FiHardDrive size={18} />}>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <p className="text-xs text-default-500 dark:text-white">Writes</p>
          <p className="text-xl font-semibold text-default-800 dark:text-white">{latest ? `${latest.writes.toFixed(2)} B/s` : "-"}</p>
        </div>
        <div>
          <p className="text-xs text-default-500 dark:text-white">Reads</p>
          <p className="text-xl font-semibold text-default-800 dark:text-white">{latest ? `${latest.reads.toFixed(2)} B/s` : "-"}</p>
        </div>
      </div>
      <TrendChart
        data={diskIoTrend}
        xKey="label"
        series={[
          { key: "writes", name: "Writes", color: "#fb7185", fill: "#fb7185" },
          { key: "reads", name: "Reads", color: "#f59e0b", fill: "#f59e0b" },
        ]}
        yUnit=" B/s"
        isDark={isDark}
      />
    </MonitorMetricCard>
  );
}
