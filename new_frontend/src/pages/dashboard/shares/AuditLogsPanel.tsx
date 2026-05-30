import { Select, SelectItem } from "@heroui/select";
import { TableCell, TableColumn, TableRow } from "@heroui/table";
import { useTranslation } from "react-i18next";
import PaginatedTableShell from "../../../components/common/PaginatedTableShell";
import type { MountInfo, ShareAuditInfo } from "../../../controllers/mounts";

type Row = ShareAuditInfo & { key: string };

export default function AuditLogsPanel({ rows, loading, selectedMountID, manageableMounts, onMountChange }: { rows: Row[]; loading: boolean; selectedMountID: string; manageableMounts: MountInfo[]; onMountChange: (v: string) => void }) {
  const { t } = useTranslation();
  return (
    <>
      <div className="rounded-sm border border-white/40 bg-white/60 px-4 py-3 backdrop-blur-sm dark:border-white/10 dark:bg-black/40">
        <Select className="max-w-md" label={t("shares.mountLabel")} selectedKeys={selectedMountID ? [selectedMountID] : []} onSelectionChange={(keys) => onMountChange(String(Array.from(keys)[0] ?? ""))}>
          {manageableMounts.map((m) => <SelectItem key={m.id}>{`${m.name} (${m.id})`}</SelectItem>)}
        </Select>
      </div>
      <PaginatedTableShell
        ariaLabel="audit-list"
        wrapperClassName="h-[calc(100vh-360px)]"
        rows={rows}
        loading={loading}
        totalLabel={(n) => t("shares.totalAudits", { count: n })}
        emptyContent={t("shares.emptyAudits")}
        header={<><TableColumn key="occurred_at">{t("shares.timeColumn")}</TableColumn><TableColumn key="action">{t("shares.actionColumn")}</TableColumn><TableColumn key="actor">{t("shares.actorColumn")}</TableColumn><TableColumn key="result">{t("shares.resultColumn")}</TableColumn><TableColumn key="detail">{t("shares.detailColumn")}</TableColumn></>}
        renderRow={(it) => <TableRow key={it.key}><TableCell>{it.occurred_at}</TableCell><TableCell>{it.action}</TableCell><TableCell>{it.actor || "-"}</TableCell><TableCell>{it.result}</TableCell><TableCell>{it.detail || "-"}</TableCell></TableRow>}
      />
    </>
  );
}
