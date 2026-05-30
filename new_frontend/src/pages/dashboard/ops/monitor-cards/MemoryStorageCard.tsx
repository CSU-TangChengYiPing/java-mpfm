import { FiDatabase } from "react-icons/fi";
import { useTranslation } from "react-i18next";
import type { MonitorTopCardContext } from "./types";
import MonitorMetricCard from "./MonitorMetricCard";
import RingGauge from "./RingGauge";

export default function MemoryStorageCard({ overview, memUsedPercent, diskUsedPercent, formatStorage }: Pick<MonitorTopCardContext, "overview" | "memUsedPercent" | "diskUsedPercent" | "formatStorage">) {
  const { t } = useTranslation();
  const swapPercent = overview ? ((overview.totalSwapBytes - overview.freeSwapBytes) / Math.max(1, overview.totalSwapBytes)) * 100 : 0;
  return (
    <MonitorMetricCard title={t("monitor.memoryStorageCard")} icon={<FiDatabase size={18} />}>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <RingGauge
          label={t("monitor.ram")}
          usedText={overview ? formatStorage(overview.totalMemBytes - overview.freeMemBytes) : "-"}
          totalText={overview ? formatStorage(overview.totalMemBytes) : "-"}
          percent={memUsedPercent}
          color="#36a3eb"
        />
        <RingGauge
          label={t("monitor.swap")}
          usedText={overview ? formatStorage(overview.totalSwapBytes - overview.freeSwapBytes) : "-"}
          totalText={overview ? formatStorage(overview.totalSwapBytes) : "-"}
          percent={swapPercent}
          color="#ff6385"
        />
        <RingGauge
          label={t("monitor.disk")}
          usedText={overview ? formatStorage(overview.diskTotalBytes - overview.diskUsableBytes) : "-"}
          totalText={overview ? formatStorage(overview.diskTotalBytes) : "-"}
          percent={diskUsedPercent}
          color="#87d068"
        />
      </div>
    </MonitorMetricCard>
  );
}
