import { Card, CardBody } from "@heroui/card";
import { Table, TableBody, TableCell, TableColumn, TableHeader, TableRow } from "@heroui/table";
import { Tab, Tabs } from "@heroui/tabs";
import { useTranslation } from "react-i18next";
import type { MonitorTopCardContext } from "./types";

function formatPercent(value: number): string {
  return `${value.toFixed(2)}%`;
}

function safeFixed(value: number | null | undefined, digits = 2): string {
  if (!Number.isFinite(value ?? Number.NaN)) return "-";
  return Number(value).toFixed(digits);
}

export default function SystemPerformanceTablesCard({ overview, totalTrafficBytes, totalUploadBps, totalDownloadBps, totalActiveUploadTasks, totalActiveDownloadTasks }: Pick<MonitorTopCardContext, "overview" | "totalTrafficBytes" | "totalUploadBps" | "totalDownloadBps" | "totalActiveUploadTasks" | "totalActiveDownloadTasks">) {
  const { t } = useTranslation();
  const statusData = [
    { key: "1", name: t("monitor.totalTraffic"), value: `${totalTrafficBytes}` },
    { key: "2", name: t("monitor.uploadThroughput"), value: `${totalUploadBps}` },
    { key: "3", name: t("monitor.downloadThroughput"), value: `${totalDownloadBps}` },
    { key: "4", name: t("monitor.cpuStatusCard"), value: overview ? formatPercent(overview.cpuLoad * 100) : "-" },
    { key: "5", name: t("monitor.ram"), value: overview ? `${formatPercent(((overview.totalMemBytes - overview.freeMemBytes) / Math.max(1, overview.totalMemBytes)) * 100)}` : "-" },
    { key: "6", name: t("monitor.disk"), value: overview ? `${formatPercent(((overview.diskTotalBytes - overview.diskUsableBytes) / Math.max(1, overview.diskTotalBytes)) * 100)}` : "-" },
    { key: "7", name: t("monitor.activeTasks"), value: `${totalActiveUploadTasks}/${totalActiveDownloadTasks}` },
  ];

  const workerData = [
    { key: "1", name: t("monitor.os"), value: overview ? `${overview.osName} ${overview.osVersion}` : "-" },
    { key: "2", name: t("monitor.uptime"), value: overview ? `${Math.floor(overview.uptimeMs / 1000)}s` : "-" },
    { key: "3", name: t("monitor.loadAverage"), value: overview ? `1min: ${safeFixed(overview.load1)} | 5min: ${safeFixed(overview.load5)} | 15min: ${safeFixed(overview.load15)}` : "-" },
    { key: "4", name: "CPU", value: overview ? `${overview.cpuModel || "Core"} * ${overview.cpuCores ?? 1}` : "-" },
    { key: "5", name: t("monitor.totalTrafficHint"), value: overview ? `${overview.networkRxBytes + overview.networkTxBytes}` : "-" },
    { key: "6", name: t("monitor.diskIoCard"), value: overview ? `${overview.diskReadBytes}/${overview.diskWriteBytes}` : "-" },
  ];

  const configData = [
    { key: "1", name: t("monitor.windowTabs"), value: "3min / 1h / 1day" },
    { key: "2", name: t("common.refresh"), value: "5s" },
    { key: "3", name: t("monitor.totalTraffic"), value: t("monitor.totalTrafficHint") },
  ];

  return (
    <Card className="border border-white/40 bg-white/60 shadow-sm backdrop-blur-xl dark:border-white/10 dark:bg-black/40">
      <CardBody className="p-0">
        <Tabs aria-label={t("monitor.windowTabs")} className="px-4 pt-3">
          <Tab key="status" title={t("monitor.cpuStatusCard")}>
            <div className="overflow-x-auto pb-4">
              <Table aria-label={t("monitor.cpuStatusCard")} removeWrapper classNames={{ table: "min-h-[180px]" }}>
                <TableHeader>
                  <TableColumn className="w-[40%]">Indicator</TableColumn>
                  <TableColumn>Value</TableColumn>
                </TableHeader>
                <TableBody emptyContent={t("common.empty")}>
                  {statusData.map((row) => (
                    <TableRow key={row.key}>
                      <TableCell>{row.name}</TableCell>
                      <TableCell>{row.value}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </Tab>
          <Tab key="workers" title={t("monitor.networkCard")}>
            <div className="overflow-x-auto pb-4">
              <Table aria-label={t("monitor.networkCard")} removeWrapper classNames={{ table: "min-h-[180px]" }}>
                <TableHeader>
                  <TableColumn className="w-[40%]">Indicator</TableColumn>
                  <TableColumn>Value</TableColumn>
                </TableHeader>
                <TableBody emptyContent={t("common.empty")}>
                  {workerData.map((row) => (
                    <TableRow key={row.key}>
                      <TableCell>{row.name}</TableCell>
                      <TableCell>{row.value}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </Tab>
          <Tab key="config" title={t("monitor.diskIoCard")}>
            <div className="overflow-x-auto pb-4">
              <Table aria-label={t("monitor.diskIoCard")} removeWrapper classNames={{ table: "min-h-[180px]" }}>
                <TableHeader>
                  <TableColumn className="w-[40%]">Indicator</TableColumn>
                  <TableColumn>Value</TableColumn>
                </TableHeader>
                <TableBody emptyContent={t("common.empty")}>
                  {configData.map((row) => (
                    <TableRow key={row.key}>
                      <TableCell>{row.name}</TableCell>
                      <TableCell>{row.value}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </Tab>
        </Tabs>
      </CardBody>
    </Card>
  );
}
