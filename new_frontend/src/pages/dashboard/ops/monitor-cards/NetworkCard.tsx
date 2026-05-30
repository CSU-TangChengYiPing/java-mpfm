import { FiWifi } from "react-icons/fi";
import { useTranslation } from "react-i18next";
import type { TransferTrendPoint } from "../useMonitorData";
import MonitorMetricCard from "./MonitorMetricCard";
import TrendChart from "./TrendChart";

export default function NetworkCard({ transferTrend, isDark, formatRate }: { transferTrend: TransferTrendPoint[]; isDark: boolean; formatRate: (bps: number) => string }) {
  const { t } = useTranslation();
  const latest = transferTrend[transferTrend.length - 1];
  return (
    <MonitorMetricCard title={t("monitor.networkCard")} icon={<FiWifi size={18} />}>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <p className="text-xs text-default-500 dark:text-white">{t("monitor.userUpload")}</p>
          <p className="text-xl font-semibold text-default-800 dark:text-white">{latest ? formatRate(latest.uploadBps) : "-"}</p>
        </div>
        <div>
          <p className="text-xs text-default-500 dark:text-white">{t("monitor.userDownload")}</p>
          <p className="text-xl font-semibold text-default-800 dark:text-white">{latest ? formatRate(latest.downloadBps) : "-"}</p>
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
        formatValue={formatRate}
        isDark={isDark}
      />
    </MonitorMetricCard>
  );
}
