import { FiHardDrive } from "react-icons/fi";
import { useTranslation } from "react-i18next";
import type { MonitorTopCardContext } from "./types";
import MonitorMetricCard from "./MonitorMetricCard";
import TrendChart from "./TrendChart";
import { formatRateBps } from "../../../../utils/rateFormat";

export default function DiskIoCard({ diskIoTrend, isDark }: Pick<MonitorTopCardContext, "diskIoTrend" | "isDark">) {
  const { t } = useTranslation();
  const rateUnitLabels = {
    B: t("common.rateUnits.b"),
    KB: t("common.rateUnits.kb"),
    MB: t("common.rateUnits.mb"),
    GB: t("common.rateUnits.gb"),
  };
  const latest = diskIoTrend[diskIoTrend.length - 1];
  return (
    <MonitorMetricCard title={t("monitor.diskIoCard")} icon={<FiHardDrive size={18} />}>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <p className="text-xs text-default-500 dark:text-white">{t("monitor.diskWrite")}</p>
          <p className="text-xl font-semibold text-default-800 dark:text-white">{latest ? formatRateBps(latest.writes, { labels: rateUnitLabels }) : "-"}</p>
        </div>
        <div>
          <p className="text-xs text-default-500 dark:text-white">{t("monitor.diskRead")}</p>
          <p className="text-xl font-semibold text-default-800 dark:text-white">{latest ? formatRateBps(latest.reads, { labels: rateUnitLabels }) : "-"}</p>
        </div>
      </div>
      <TrendChart
        data={diskIoTrend}
        xKey="label"
        series={[
          { key: "writes", name: t("monitor.diskWrite"), color: "#fb7185", fill: "#fb7185" },
          { key: "reads", name: t("monitor.diskRead"), color: "#f59e0b", fill: "#f59e0b" },
        ]}
        formatValue={(value) => formatRateBps(value, { labels: rateUnitLabels })}
        isDark={isDark}
      />
    </MonitorMetricCard>
  );
}
