import { Button } from "@heroui/button";
import { Input } from "@heroui/input";
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
import { pickSingleSelectKey } from "./selectKey";

type Row = ShareInfo & { key: string };

/** 共享用户面板：管理共享链接创建、角色选择与撤销操作。 */
export default function SharedUsersPanel({
  selectedMountID,
  manageableMounts,
  shareRole,
  shareRoleList,
  expiresAt,
  roleExpiresAt,
  maxUses,
  resolveToken,
  myRoles,
  grantedRoleOptions,
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
  roleNameMap: Map<string, string>;
  loading: boolean;
  rows: Row[];
  onMountChange: (v: string) => void;
  shareRole: string;
  shareRoleList: string[];
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
      shareRoleList.filter((roleId) => {
        const label = (roleNameMap.get(roleId) || roleId).trim().toLowerCase();
        return roleId !== "owner" && label !== "owner" && label !== "所有者";
      }),
    [roleNameMap, shareRoleList]
  );
  useEffect(() => {
    if (!createOpen) return;
    setStep(1);
  }, [createOpen]);

  function isMobileViewport(): boolean {
    if (typeof window === "undefined") return false;
    return window.innerWidth < 768;
  }
  return (
    <>
      <div className="rounded-sm border border-white/40 bg-white/60 px-4 py-3 backdrop-blur-sm dark:border-white/10 dark:bg-black/40">
        <div className="flex items-end justify-between gap-2">
          <div className="flex min-w-0 flex-1 items-center gap-2 md:max-w-md">
            <span className="w-16 shrink-0 text-xs text-default-600">{t("shares.mountLabel")}</span>
            <Select
              className="min-w-0 flex-1"
              size="sm"
              classNames={{ trigger: "h-8 min-h-8", value: "text-xs" }}
              aria-label={t("shares.mountLabel")}
              selectedKeys={selectedMountID ? [selectedMountID] : []}
              onSelectionChange={(keys) => onMountChange(pickSingleSelectKey(keys as "all" | Set<string | number>))}
            >
              {manageableMounts.map((m) => <SelectItem key={m.id}>{m.name}</SelectItem>)}
            </Select>
          </div>
          <Button className="shrink-0" size="sm" color="primary" onPress={() => onCreateOpenChange(true)} isDisabled={!selectedMountID}>{t("shares.createShareButton")}</Button>
        </div>
        <div className="mt-3 flex items-end gap-2">
          <Input
            size="sm"
            radius="sm"
            variant="bordered"
            className="min-w-0 flex-1"
            placeholder={t("shares.applyLinkPlaceholder")}
            value={resolveToken}
            onValueChange={onResolveTokenChange}
            classNames={{
              inputWrapper: "h-9 min-h-9",
              input: "text-sm",
            }}
          />
          <Button size="sm" color="secondary" onPress={onResolveLink} isDisabled={!resolveToken.trim()}>{t("shares.applyLinkButton")}</Button>
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
          wrapperClassName="min-h-[360px] gap-0"
          rows={rows}
          loading={loading}
          totalLabel={(n) => t("shares.totalShares", { count: n })}
          emptyContent={t("shares.emptyShares")}
          header={<><TableColumn key="role">{t("shares.roleColumn")}</TableColumn><TableColumn key="state">{t("shares.linkStateColumn")}</TableColumn><TableColumn key="expires_at">{t("shares.expiresAtColumn")}</TableColumn><TableColumn key="max_uses">{t("shares.maxUsesColumn")}</TableColumn><TableColumn key="actions" className="hidden md:table-cell">{t("common.actions")}</TableColumn></>}
          renderRow={(it) => (
            <TableRow
              key={it.key}
              className="cursor-pointer transition-all duration-150 active:scale-[0.992] active:bg-black/15 dark:active:bg-white/20 md:cursor-default md:active:scale-100 md:active:bg-transparent md:dark:active:bg-transparent"
              onClick={() => {
                if (!isMobileViewport()) return;
                setDetailLink(it);
                setDetailOpen(true);
              }}
            >
              <TableCell>{roleNameMap.get(it.roleId || it.role || "") || it.roleId || it.role || "-"}</TableCell>
              <TableCell>{it.state || (it.revoked_at ? "revoked" : "active")}</TableCell>
              <TableCell>{it.expires_at || "-"}</TableCell>
              <TableCell>{typeof it.maxUses === "number" ? `${it.usedCount ?? 0}/${it.maxUses}` : "-"}</TableCell>
              <TableCell className="hidden gap-2 md:flex">
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
          wrapperClassName="min-h-[360px]"
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
            <Select label={t("shares.roleColumn")} selectedKeys={[shareRole]} onSelectionChange={(keys) => onShareRoleChange(pickSingleSelectKey(keys as "all" | Set<string | number>))}>
              {selectableRoles.map((r) => <SelectItem key={r}>{`${roleNameMap.get(r) || r} (ID:${r})`}</SelectItem>)}
            </Select>
            <Select label={t("shares.expireModeLabel")} selectedKeys={[linkExpireMode]} onSelectionChange={(keys) => setLinkExpireMode(pickSingleSelectKey(keys as "all" | Set<string | number>) as "countdown" | "datetime")}>
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
            <Select label={t("shares.expireModeLabel")} selectedKeys={[roleExpireMode]} onSelectionChange={(keys) => setRoleExpireMode(pickSingleSelectKey(keys as "all" | Set<string | number>) as "countdown" | "datetime")}>
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
        <div className="space-y-1">
          <label className="text-sm text-default-700 dark:text-default-300">{t("shares.roleColumn")}</label>
          <Select size="sm" selectedKeys={editingRoleId ? [editingRoleId] : []} onSelectionChange={(keys) => setEditingRoleId(pickSingleSelectKey(keys as "all" | Set<string | number>))}>
            {grantedRoleOptions.map((r) => <SelectItem key={r.roleId}>{`${r.name} (ID:${r.roleId})`}</SelectItem>)}
          </Select>
        </div>
        <div className="space-y-1">
          <label className="text-sm text-default-700 dark:text-default-300">{t("shares.roleExpireAtColumn")}</label>
          <Input
            type="datetime-local"
            size="sm"
            radius="sm"
            variant="bordered"
            value={editingRoleExpireAt}
            onChange={(event) => setEditingRoleExpireAt(event.target.value)}
            classNames={{
              inputWrapper: "h-9 min-h-9",
              input: "text-sm",
            }}
          />
        </div>
      </FormModal>
      <FormModal
        isOpen={detailOpen}
        onClose={() => setDetailOpen(false)}
        title={t("shares.linkDetailTitle")}
        submitText={t("common.close")}
        cancelText={t("common.cancel")}
        hideCancelButton
        hideSubmitButton={isMobileViewport()}
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
        <div className="flex gap-2 md:hidden">
          <Button size="sm" color="danger" variant="flat" className="flex-1" isDisabled={detailLink?.state === "revoked" || !!detailLink?.revoked_at} onPress={() => { if (!detailLink) return; onRevokeShare(detailLink.id); setDetailOpen(false); }}>
            {t("shares.revokeButton")}
          </Button>
          <Button size="sm" variant="flat" className="flex-1" onPress={() => { if (!detailLink) return; onDeleteShare(detailLink.id); setDetailOpen(false); }}>
            {t("shares.deleteLinkButton")}
          </Button>
        </div>
      </FormModal>
    </>
  );
}
