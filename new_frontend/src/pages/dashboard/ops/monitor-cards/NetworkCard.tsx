import { FiWifi } from "react-icons/fi";
import { useTranslation } from "react-i18next";
import type { TransferTrendPoint } from "../useMonitorData";
import MonitorMetricCard from "./MonitorMetricCard";
import TrendChart from "./TrendChart";
import { formatRateBps } from "../../../../utils/rateFormat";

export default function NetworkCard({ transferTrend, isDark }: { transferTrend: TransferTrendPoint[]; isDark: boolean }) {
  const { t } = useTranslation();
  const rateUnitLabels = {
    B: t("common.rateUnits.b"),
    KB: t("common.rateUnits.kb"),
    MB: t("common.rateUnits.mb"),
    GB: t("common.rateUnits.gb"),
  };
  const latest = transferTrend[transferTrend.length - 1];
  return (
    <MonitorMetricCard title={t("monitor.networkCard")} icon={<FiWifi size={18} />}>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <p className="text-xs text-default-500 dark:text-white">{t("monitor.userUpload")}</p>
          <p className="text-xl font-semibold text-default-800 dark:text-white">{latest ? formatRateBps(latest.uploadBps, { labels: rateUnitLabels }) : "-"}</p>
        </div>
        <div>
          <p className="text-xs text-default-500 dark:text-white">{t("monitor.userDownload")}</p>
          <p className="text-xl font-semibold text-default-800 dark:text-white">{latest ? formatRateBps(latest.downloadBps, { labels: rateUnitLabels }) : "-"}</p>
        </div>
      </div>
      <TrendChart
        data={transferTrend.map((item) => ({
          label: new Date(item.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" }),
          uploadBps: item.uploadBps,
          downloadBps: item.downloadBps,
        }))}
        xKey="label"
        series={[
          { key: "uploadBps", name: t("monitor.userUpload"), color: "#3b82f6", fill: "#3b82f6" },
          { key: "downloadBps", name: t("monitor.userDownload"), color: "#10b981", fill: "#10b981" },
        ]}
        formatValue={(value) => formatRateBps(value, { labels: rateUnitLabels })}
        isDark={isDark}
      />
    </MonitorMetricCard>
  );
}
