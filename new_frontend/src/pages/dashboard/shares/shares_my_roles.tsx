import { TableCell, TableColumn, TableRow } from "@heroui/table";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import toast from "react-hot-toast";
import { Button } from "@heroui/button";
import { useNavigate } from "react-router-dom";
import PaginatedTableShell from "../../../components/common/PaginatedTableShell";
import MountsController, { type ShareMyRoleSummaryInfo } from "../../../controllers/mounts";
import FormModal from "../../../components/common/FormModal";
import { LargeGlassInput } from "../../../components/common/LargeGlassField";

/** 我的共享角色页：展示当前用户在各挂载下已授予的角色绑定。 */
export default function SharesMyRolesPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [rows, setRows] = useState<Array<ShareMyRoleSummaryInfo & { key: string }>>([]);
  const [loading, setLoading] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedRow, setSelectedRow] = useState<(ShareMyRoleSummaryInfo & { key: string }) | null>(null);
  const toSharedAlias = (mountName: string, mountOwner?: string): string => {
    const safeMountName = (mountName || "-").trim() || "-";
    const safeOwner = (mountOwner || "-").trim() || "-";
    return `${safeMountName}---${safeOwner}`;
  };

  useEffect(() => {
    void (async () => {
      setLoading(true);
      try {
        const list = await MountsController.listMyRoleSummariesV5();
        setRows(list.map((item, idx) => ({ ...item, key: `${item.mountId}-${item.roleId}-${idx}` })));
      } catch (err) {
        toast.error(err instanceof Error ? err.message : t("shares.loadFailed"));
        setRows([]);
      } finally {
        setLoading(false);
      }
    })();
  }, [t]);

  function isMobileViewport(): boolean {
    if (typeof window === "undefined") return false;
    return window.innerWidth < 768;
  }

  function openMount(it: ShareMyRoleSummaryInfo) {
    navigate(`/app/files#${encodeURIComponent(`/shared/${toSharedAlias(it.mountName, it.mountOwner)}`)}`);
  }

  return (
    <div className="h-full w-full p-2 md:p-4">
      <PaginatedTableShell
        ariaLabel="my-shared-roles"
        wrapperClassName="min-h-[420px]"
        rows={rows}
        loading={loading}
        totalLabel={(n) => t("shares.totalMyRoles", { count: n })}
        emptyContent={t("shares.emptyMyRoles")}
        header={
          <>
            <TableColumn key="mount_name">{t("shares.mountNameColumn")}</TableColumn>
            <TableColumn key="mount_owner">{t("shares.mountOwnerColumn")}</TableColumn>
            <TableColumn key="role_name">{t("shares.roleNameColumn")}</TableColumn>
            <TableColumn key="role_state">{t("shares.linkStateColumn")}</TableColumn>
            <TableColumn key="granted_at">{t("shares.grantedAtColumn")}</TableColumn>
            <TableColumn key="actions" className="hidden md:table-cell">{t("common.actions")}</TableColumn>
          </>
        }
        renderRow={(it) => (
          <TableRow
            key={it.key}
            className="cursor-pointer transition-all duration-150 active:scale-[0.992] active:bg-black/15 dark:active:bg-white/20 md:cursor-default md:active:scale-100 md:active:bg-transparent md:dark:active:bg-transparent"
            onClick={() => {
              if (!isMobileViewport()) return;
              setSelectedRow(it);
              setDetailOpen(true);
            }}
          >
            <TableCell>{it.mountName || "-"}</TableCell>
            <TableCell>{it.mountOwner || "-"}</TableCell>
            <TableCell>{it.roleName}</TableCell>
            <TableCell>{it.roleState}</TableCell>
            <TableCell>{it.grantedAt || "-"}</TableCell>
            <TableCell className="hidden md:table-cell">
              <Button
                size="sm"
                variant="flat"
                onPress={() => openMount(it)}
              >
                {t("shares.quickEnterMountButton")}
              </Button>
            </TableCell>
          </TableRow>
        )}
      />
      <FormModal
        isOpen={detailOpen}
        onClose={() => setDetailOpen(false)}
        title={t("shares.myRolesTitle")}
        submitText={t("shares.quickEnterMountButton")}
        cancelText={t("common.close")}
        onSubmit={() => {
          if (!selectedRow) return;
          openMount(selectedRow);
          setDetailOpen(false);
        }}
      >
        <LargeGlassInput label={t("shares.mountNameColumn")} value={selectedRow?.mountName || "-"} isReadOnly />
        <LargeGlassInput label={t("shares.mountOwnerColumn")} value={selectedRow?.mountOwner || "-"} isReadOnly />
        <LargeGlassInput label={t("shares.roleNameColumn")} value={selectedRow?.roleName || "-"} isReadOnly />
        <LargeGlassInput label={t("shares.linkStateColumn")} value={selectedRow?.roleState || "-"} isReadOnly />
        <LargeGlassInput label={t("shares.grantedAtColumn")} value={selectedRow?.grantedAt || "-"} isReadOnly />
      </FormModal>
    </div>
  );
}
