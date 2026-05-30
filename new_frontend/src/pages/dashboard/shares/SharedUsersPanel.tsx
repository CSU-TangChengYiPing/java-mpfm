import { Button } from "@heroui/button";
import FormModal from "../../../components/common/FormModal";
import { Select, SelectItem } from "@heroui/select";
import { TableCell, TableColumn, TableRow } from "@heroui/table";
import { Tab, Tabs } from "@heroui/tabs";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import toast from "react-hot-toast";
import PaginatedTableShell from "../../../components/common/PaginatedTableShell";
import { LargeGlassInput } from "../../../components/common/LargeGlassField";
import type { MountInfo } from "../../../controllers/mounts";
import type { ShareInfo } from "../../../controllers/mounts";
import type { ShareMyRoleInfo } from "../../../controllers/mounts";

type Row = ShareInfo & { key: string };

/** 共享用户面板：管理共享链接创建、角色选择与撤销操作。 */
export default function SharedUsersPanel({
  selectedMountID,
  manageableMounts,
  shareRole,
  expiresAt,
  roleExpiresAt,
  maxUses,
  resolveToken,
  myRoles,
  grantedRoleOptions,
  presetRoleList,
  roleNameMap,
  loading,
  rows,
  onMountChange,
  onShareRoleChange,
  onExpiresAtChange,
  onRoleExpiresAtChange,
  onCreateShare,
  onRevokeShare,
  onDeleteShare,
  onMaxUsesChange,
  onResolveTokenChange,
  onResolveLink,
  onUpdateGrantedRole,
  onRevokeGrantedRole,
  createOpen,
  onCreateOpenChange,
}: {
  selectedMountID: string;
  manageableMounts: MountInfo[];
  presetRoleList: string[];
  roleNameMap: Map<string, string>;
  loading: boolean;
  rows: Row[];
  onMountChange: (v: string) => void;
  shareRole: string;
  expiresAt: string;
  roleExpiresAt: string;
  maxUses: string;
  resolveToken: string;
  myRoles: ShareMyRoleInfo[];
  grantedRoleOptions: Array<{ roleId: string; name: string }>;
  onShareRoleChange: (v: string) => void;
  onExpiresAtChange: (v: string) => void;
  onRoleExpiresAtChange: (v: string) => void;
  onCreateShare: () => void;
  onRevokeShare: (shareID: string) => void;
  onDeleteShare: (shareID: string) => void;
  onMaxUsesChange: (v: string) => void;
  onResolveTokenChange: (v: string) => void;
  onResolveLink: () => void;
  onUpdateGrantedRole: (item: ShareMyRoleInfo, roleId: string, roleExpireAt?: string) => void;
  onRevokeGrantedRole: (item: ShareMyRoleInfo) => void;
  createOpen: boolean;
  onCreateOpenChange: (v: boolean) => void;
}) {
  const { t } = useTranslation();
  const [step, setStep] = useState<1 | 2>(1);
  const [linkExpireMode, setLinkExpireMode] = useState<"countdown" | "datetime">("countdown");
  const [roleExpireMode, setRoleExpireMode] = useState<"countdown" | "datetime">("countdown");
  const [linkHours, setLinkHours] = useState("24");
  const [roleHours, setRoleHours] = useState("24");
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLink, setDetailLink] = useState<Row | null>(null);
  const [activeTab, setActiveTab] = useState<"links" | "roles">("links");
  const [editGrantedOpen, setEditGrantedOpen] = useState(false);
  const [editingGranted, setEditingGranted] = useState<ShareMyRoleInfo | null>(null);
  const [editingRoleId, setEditingRoleId] = useState("");
  const [editingRoleExpireAt, setEditingRoleExpireAt] = useState("");
  const datetimeValue = useMemo(() => (expiresAt ? expiresAt.slice(0, 16) : ""), [expiresAt]);
  const roleDatetimeValue = useMemo(() => (roleExpiresAt ? roleExpiresAt.slice(0, 16) : ""), [roleExpiresAt]);
  const selectableRoles = useMemo(
    () =>
      presetRoleList.filter((roleId) => {
        const label = (roleNameMap.get(roleId) || roleId).trim().toLowerCase();
        return roleId !== "owner" && label !== "owner" && label !== "所有者";
      }),
    [presetRoleList, roleNameMap]
  );
  useEffect(() => {
    if (!createOpen) return;
    setStep(1);
  }, [createOpen]);
  return (
    <>
      <div className="rounded-sm border border-white/40 bg-white/60 px-4 py-3 backdrop-blur-sm dark:border-white/10 dark:bg-black/40">
        <div className="flex items-end justify-between gap-3">
          <Select label={t("shares.mountLabel")} selectedKeys={selectedMountID ? [selectedMountID] : []} onSelectionChange={(keys) => onMountChange(String(Array.from(keys)[0] ?? ""))}>
            {manageableMounts.map((m) => <SelectItem key={m.id}>{`${m.name} (${m.id})`}</SelectItem>)}
          </Select>
          <div className="flex items-center gap-2">
            <Button color="primary" onPress={() => onCreateOpenChange(true)} isDisabled={!selectedMountID}>{t("shares.createShareButton")}</Button>
          </div>
        </div>
        <div className="mt-3 flex items-end gap-2">
          <LargeGlassInput label={t("shares.applyLinkPlaceholder")} value={resolveToken} onValueChange={onResolveTokenChange} commitMode="blur" />
          <Button color="secondary" onPress={onResolveLink} isDisabled={!resolveToken.trim()}>{t("shares.applyLinkButton")}</Button>
        </div>
      </div>
      <div className="rounded-sm border border-white/40 bg-white/60 px-4 py-3 backdrop-blur-sm dark:border-white/10 dark:bg-black/40">
        <Tabs selectedKey={activeTab} onSelectionChange={(k) => setActiveTab(String(k) as "links" | "roles")} size="sm">
          <Tab key="links" title={t("shares.linksTab")} />
          <Tab key="roles" title={t("shares.grantedRolesTab")} />
        </Tabs>
      </div>
      {activeTab === "links" ? (
        <PaginatedTableShell
          ariaLabel="share-list"
          wrapperClassName="h-[calc(100vh-420px)]"
          rows={rows}
          loading={loading}
          totalLabel={(n) => t("shares.totalShares", { count: n })}
          emptyContent={t("shares.emptyShares")}
          header={<><TableColumn key="id">ID</TableColumn><TableColumn key="role">{t("shares.roleColumn")}</TableColumn><TableColumn key="state">{t("shares.linkStateColumn")}</TableColumn><TableColumn key="expires_at">{t("shares.expiresAtColumn")}</TableColumn><TableColumn key="max_uses">{t("shares.maxUsesColumn")}</TableColumn><TableColumn key="actions">{t("common.actions")}</TableColumn></>}
          renderRow={(it) => (
            <TableRow key={it.key}>
              <TableCell>{it.id}</TableCell>
              <TableCell>{roleNameMap.get(it.roleId || it.role || "") || it.roleId || it.role || "-"}</TableCell>
              <TableCell>{it.state || (it.revoked_at ? "revoked" : "active")}</TableCell>
              <TableCell>{it.expires_at || "-"}</TableCell>
              <TableCell>{typeof it.maxUses === "number" ? `${it.usedCount ?? 0}/${it.maxUses}` : "-"}</TableCell>
              <TableCell className="flex gap-2">
                <Button size="sm" variant="flat" onPress={() => { setDetailLink(it); setDetailOpen(true); }}>{t("shares.viewLinkButton")}</Button>
                <Button size="sm" color="danger" variant="flat" isDisabled={it.state === "revoked" || !!it.revoked_at} onPress={() => onRevokeShare(it.id)}>{t("shares.revokeButton")}</Button>
                <Button size="sm" variant="flat" onPress={() => onDeleteShare(it.id)}>{t("shares.deleteLinkButton")}</Button>
              </TableCell>
            </TableRow>
          )}
        />
      ) : (
        <PaginatedTableShell
          ariaLabel="my-role-list"
          wrapperClassName="h-[calc(100vh-420px)]"
          rows={myRoles.map((r, idx) => ({ ...r, key: `${r.mountId}-${r.roleId}-${idx}` }))}
          loading={loading}
          totalLabel={(n) => t("shares.totalMyRoles", { count: n })}
          emptyContent={t("shares.emptyMyRoles")}
          header={<><TableColumn key="mountId">{t("shares.mountIdColumn")}</TableColumn><TableColumn key="granteeUsername">{t("shares.granteeUsernameColumn")}</TableColumn><TableColumn key="roleName">{t("shares.roleNameColumn")}</TableColumn><TableColumn key="roleState">{t("shares.roleStateColumn")}</TableColumn><TableColumn key="roleExpireAt">{t("shares.roleExpireAtColumn")}</TableColumn><TableColumn key="grantedAt">{t("shares.grantedAtColumn")}</TableColumn><TableColumn key="actions">{t("common.actions")}</TableColumn></>}
          renderRow={(it) => (
            <TableRow key={it.key}>
              <TableCell>{it.mountId}</TableCell>
              <TableCell>{it.granteeUsername || "-"}</TableCell>
              <TableCell>{it.roleName || it.roleId || "-"}</TableCell>
              <TableCell>{it.roleState || "-"}</TableCell>
              <TableCell>{it.roleExpireAt || "-"}</TableCell>
              <TableCell>{it.grantedAt || "-"}</TableCell>
              <TableCell className="flex gap-2">
                <Button
                  size="sm"
                  variant="flat"
                  onPress={() => {
                    setEditingGranted(it);
                    setEditingRoleId(it.roleId);
                    setEditingRoleExpireAt(it.roleExpireAt ? it.roleExpireAt.slice(0, 16) : "");
                    setEditGrantedOpen(true);
                  }}
                >
                  {t("common.modify")}
                </Button>
                <Button size="sm" color="danger" variant="flat" onPress={() => onRevokeGrantedRole(it)}>
                  {t("shares.revokeGrantButton")}
                </Button>
              </TableCell>
            </TableRow>
          )}
        />
      )}
      <FormModal
        isOpen={createOpen}
        onClose={() => onCreateOpenChange(false)}
        title={t("shares.createLinkTitle")}
        submitText={step === 1 ? t("shares.nextStep") : t("common.create")}
        secondaryActionText={step === 2 ? t("shares.prevStep") : undefined}
        onSecondaryAction={step === 2 ? () => setStep(1) : undefined}
        isDismissable={false}
        onSubmit={() => {
          if (step === 1) {
            if (linkExpireMode === "countdown") {
              const n = Number(linkHours);
              if (Number.isFinite(n) && n > 0) {
                onExpiresAtChange(new Date(Date.now() + n * 3600 * 1000).toISOString());
              } else {
                onExpiresAtChange("");
              }
            }
            setStep(2);
            return;
          }
          if (roleExpireMode === "countdown") {
            const n = Number(roleHours);
            if (Number.isFinite(n) && n > 0) {
              onRoleExpiresAtChange(new Date(Date.now() + n * 3600 * 1000).toISOString());
            } else {
              onRoleExpiresAtChange("");
            }
          }
          onCreateShare();
          onCreateOpenChange(false);
        }}
      >
        <div className="space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div className="flex min-w-0 items-center gap-2">
              <span className={`inline-flex h-6 w-6 items-center justify-center rounded-full text-xs ${step === 1 ? "bg-primary text-white" : "bg-success text-white"}`}>1</span>
              <span className={`truncate text-xs ${step === 1 ? "font-semibold text-foreground" : "text-default-500"}`}>{t("shares.createStepLinkSetting")}</span>
            </div>
            <div className="h-px flex-1 bg-default-300" />
            <div className="flex min-w-0 items-center gap-2">
              <span className={`inline-flex h-6 w-6 items-center justify-center rounded-full text-xs ${step === 2 ? "bg-primary text-white" : "bg-default-300 text-default-700"}`}>2</span>
              <span className={`truncate text-xs ${step === 2 ? "font-semibold text-foreground" : "text-default-500"}`}>{t("shares.createStepRoleExpireSetting")}</span>
            </div>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-default-200">
            <div className={`h-full bg-primary transition-all ${step === 1 ? "w-1/2" : "w-full"}`} />
          </div>
        </div>
        {step === 1 ? (
          <>
            <Select label={t("shares.roleColumn")} selectedKeys={[shareRole]} onSelectionChange={(keys) => onShareRoleChange(String(Array.from(keys)[0]))}>
              {selectableRoles.map((r) => <SelectItem key={r}>{`${roleNameMap.get(r) || r} (ID:${r})`}</SelectItem>)}
            </Select>
            <Select label={t("shares.expireModeLabel")} selectedKeys={[linkExpireMode]} onSelectionChange={(keys) => setLinkExpireMode(String(Array.from(keys)[0]) as "countdown" | "datetime")}>
              <SelectItem key="countdown">{t("shares.expireCountdown")}</SelectItem>
              <SelectItem key="datetime">{t("shares.expireDatetime")}</SelectItem>
            </Select>
            {linkExpireMode === "countdown" ? (
              <LargeGlassInput label={t("shares.expireHoursPlaceholder")} value={linkHours} onValueChange={setLinkHours} commitMode="blur" />
            ) : (
              <LargeGlassInput type="datetime-local" label={t("shares.expireDatetime")} value={datetimeValue} onValueChange={(v) => onExpiresAtChange(v ? new Date(v).toISOString() : "")} commitMode="blur" />
            )}
            <LargeGlassInput label={t("shares.maxUsesPlaceholder")} value={maxUses} onValueChange={onMaxUsesChange} commitMode="blur" />
          </>
        ) : (
          <>
            <Select label={t("shares.expireModeLabel")} selectedKeys={[roleExpireMode]} onSelectionChange={(keys) => setRoleExpireMode(String(Array.from(keys)[0]) as "countdown" | "datetime")}>
              <SelectItem key="countdown">{t("shares.expireCountdown")}</SelectItem>
              <SelectItem key="datetime">{t("shares.expireDatetime")}</SelectItem>
            </Select>
            {roleExpireMode === "countdown" ? (
              <LargeGlassInput label={t("shares.expireHoursPlaceholder")} value={roleHours} onValueChange={setRoleHours} commitMode="blur" />
            ) : (
              <LargeGlassInput type="datetime-local" label={t("shares.expireDatetime")} value={roleDatetimeValue} onValueChange={(v) => onRoleExpiresAtChange(v ? new Date(v).toISOString() : "")} commitMode="blur" />
            )}
          </>
        )}
      </FormModal>
      <FormModal
        isOpen={editGrantedOpen}
        onClose={() => setEditGrantedOpen(false)}
        title={t("shares.editGrantedRoleTitle")}
        submitText={t("common.save")}
        onSubmit={() => {
          if (!editingGranted) return;
          if (editingRoleExpireAt && new Date(editingRoleExpireAt).getTime() <= Date.now()) {
            toast.error(t("shares.expireAtMustBeFuture"));
            return;
          }
          onUpdateGrantedRole(editingGranted, editingRoleId, editingRoleExpireAt ? new Date(editingRoleExpireAt).toISOString() : undefined);
          setEditGrantedOpen(false);
        }}
      >
        <Select label={t("shares.roleColumn")} selectedKeys={editingRoleId ? [editingRoleId] : []} onSelectionChange={(keys) => setEditingRoleId(String(Array.from(keys)[0] ?? ""))}>
          {grantedRoleOptions.map((r) => <SelectItem key={r.roleId}>{`${r.name} (ID:${r.roleId})`}</SelectItem>)}
        </Select>
        <LargeGlassInput type="datetime-local" label={t("shares.roleExpireAtColumn")} value={editingRoleExpireAt} onValueChange={setEditingRoleExpireAt} commitMode="blur" />
      </FormModal>
      <FormModal
        isOpen={detailOpen}
        onClose={() => setDetailOpen(false)}
        title={t("shares.linkDetailTitle")}
        submitText={t("common.close")}
        cancelText={t("common.cancel")}
        onSubmit={() => setDetailOpen(false)}
      >
        <LargeGlassInput
          label={t("shares.linkUrlColumn")}
          value={detailLink?.token ? `${window.location.origin}/app/shares/shared-users?shareToken=${encodeURIComponent(detailLink.token)}` : ""}
          readOnly
          endContent={(
            <Button
              size="sm"
              variant="flat"
              onPress={async () => {
                const value = detailLink?.token ? `${window.location.origin}/app/shares/shared-users?shareToken=${encodeURIComponent(detailLink.token)}` : "";
                if (!value) return;
                await navigator.clipboard.writeText(value);
                toast.success(t("shares.copyLinkSuccess"));
              }}
            >
              {t("shares.copyLinkButton")}
            </Button>
          )}
        />
        <LargeGlassInput
          label={t("shares.linkTokenColumn")}
          value={detailLink?.token || ""}
          readOnly
          endContent={(
            <Button
              size="sm"
              variant="flat"
              onPress={async () => {
                const value = detailLink?.token || "";
                if (!value) return;
                await navigator.clipboard.writeText(value);
                toast.success(t("shares.copyTokenSuccess"));
              }}
            >
              {t("shares.copyTokenButton")}
            </Button>
          )}
        />
      </FormModal>
    </>
  );
}
