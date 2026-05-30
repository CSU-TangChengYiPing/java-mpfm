import { TableCell, TableColumn, TableRow } from "@heroui/table";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import toast from "react-hot-toast";
import { Button } from "@heroui/button";
import { useNavigate } from "react-router-dom";
import PaginatedTableShell from "../../../components/common/PaginatedTableShell";
import MountsController, { type ShareMyRoleSummaryInfo } from "../../../controllers/mounts";

/** 我的共享角色页：展示当前用户在各挂载下已授予的角色绑定。 */
export default function SharesMyRolesPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [rows, setRows] = useState<Array<ShareMyRoleSummaryInfo & { key: string }>>([]);
  const [loading, setLoading] = useState(false);
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

  return (
    <div className="h-full w-full p-2 md:p-4">
      <PaginatedTableShell
        ariaLabel="my-shared-roles"
        wrapperClassName="h-[calc(100vh-240px)]"
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
            <TableColumn key="actions">{t("common.actions")}</TableColumn>
          </>
        }
        renderRow={(it) => (
          <TableRow key={it.key}>
            <TableCell>{it.mountName || "-"}</TableCell>
            <TableCell>{it.mountOwner || "-"}</TableCell>
            <TableCell>{it.roleName}</TableCell>
            <TableCell>{it.roleState}</TableCell>
            <TableCell>{it.grantedAt || "-"}</TableCell>
            <TableCell>
              <Button
                size="sm"
                variant="flat"
                onPress={() => navigate(`/app/files#${encodeURIComponent(`/shared/${toSharedAlias(it.mountName, it.mountOwner)}`)}`)}
              >
                {t("shares.quickEnterMountButton")}
              </Button>
            </TableCell>
          </TableRow>
        )}
      />
    </div>
  );
}
