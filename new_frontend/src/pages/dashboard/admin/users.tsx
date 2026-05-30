import { Button } from "@heroui/button";
import { Select, SelectItem } from "@heroui/select";
import { Switch } from "@heroui/switch";
import { TableCell, TableColumn, TableRow } from "@heroui/table";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { FiEdit2, FiPlus, FiRefreshCw, FiSearch, FiSettings } from "react-icons/fi";
import toast from "react-hot-toast";
import { useNavigate } from "react-router-dom";
import FormModal from "../../../components/common/FormModal";
import PaginatedTableShell from "../../../components/common/PaginatedTableShell";
import RootOnlyNoticeCard from "../../../components/common/RootOnlyNoticeCard";
import ShadowTooltip from "../../../components/common/ShadowTooltip";
import { LargeGlassInput } from "../../../components/common/LargeGlassField";
import UsersController, { type UserInfo } from "../../../controllers/users";
import { useAuth } from "../../../hooks/useAuth";

/** 用户管理页：聚焦用户列表与治理动作，QoS 策略配置迁移至独立页面。 */
export default function UsersPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [users, setUsers] = useState<UserInfo[]>([]);
  const [msg, setMsg] = useState("");
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [total, setTotal] = useState(0);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editing, setEditing] = useState<UserInfo | null>(null);
  const [formUsername, setFormUsername] = useState("");
  const [formPassword, setFormPassword] = useState("");
  const [formDisplayName, setFormDisplayName] = useState("");
  const [formRole, setFormRole] = useState<"user" | "admin">("user");
  const [formStatus, setFormStatus] = useState<"active" | "disabled">("active");
  const [formQosProfile, setFormQosProfile] = useState("default");
  const [formCustomUploadMbps, setFormCustomUploadMbps] = useState("0");
  const [formCustomDownloadMbps, setFormCustomDownloadMbps] = useState("0");
  const [formUploadPaused, setFormUploadPaused] = useState(false);
  const [formDownloadPaused, setFormDownloadPaused] = useState(false);
  const [qosOptions, setQosOptions] = useState<Array<{ id: string; name: string; maxUploadBps: number; maxDownloadBps: number }>>([]);
  const [isCustomModalOpen, setIsCustomModalOpen] = useState(false);
  const [customTarget, setCustomTarget] = useState<UserInfo | null>(null);
  const [customUploadMbps, setCustomUploadMbps] = useState("0");
  const [customDownloadMbps, setCustomDownloadMbps] = useState("0");

  const loadUsers = useCallback(async () => {
    if (!user?.is_root) return;
    setLoading(true);
    setMsg("");
    try {
      const usersPayload = await UsersController.list({ q: keyword, page, pageSize });
      setUsers(usersPayload.users ?? []);
      setTotal(usersPayload.page.total);
      const policies = await UsersController.listQosPolicies().catch(() => []);
      setQosOptions(policies.map((policy) => ({
        id: policy.id,
        name: policy.name || policy.id,
        maxUploadBps: policy.maxUploadBps,
        maxDownloadBps: policy.maxDownloadBps,
      })));
    } catch (err) {
      setMsg(err instanceof Error ? err.message : t("users.loadFailed"));
      toast.error(err instanceof Error ? err.message : t("users.loadFailed"));
    } finally {
      setLoading(false);
    }
  }, [keyword, page, pageSize, t, user?.is_root]);

  useEffect(() => {
    void loadUsers();
  }, [loadUsers]);

  function openCreate() {
    setEditing(null);
    setFormUsername("");
    setFormPassword("");
    setFormDisplayName("");
    setFormRole("user");
    setFormStatus("active");
    setFormQosProfile("default");
    setFormCustomUploadMbps("0");
    setFormCustomDownloadMbps("0");
    setFormUploadPaused(false);
    setFormDownloadPaused(false);
    setIsModalOpen(true);
  }

  async function openEdit(target: UserInfo) {
    setEditing(target);
    setFormUsername(target.username);
    setFormPassword("");
    setFormDisplayName(target.displayName || target.username);
    setFormRole(target.role.toLowerCase() === "admin" ? "admin" : "user");
    setFormStatus(target.status.toLowerCase() === "disabled" ? "disabled" : "active");
    setFormQosProfile(target.qosCustomEnabled ? "custom" : (target.qosProfile || "default"));
    setFormUploadPaused(!!target.uploadPaused);
    setFormDownloadPaused(!!target.downloadPaused);
    if (target.qosCustomEnabled) {
      setFormCustomUploadMbps(String(Math.round((target.qosCustomUploadBps ?? 0) / 1024 / 1024)));
      setFormCustomDownloadMbps(String(Math.round((target.qosCustomDownloadBps ?? 0) / 1024 / 1024)));
    } else {
      setFormCustomUploadMbps("0");
      setFormCustomDownloadMbps("0");
    }
    setIsModalOpen(true);
  }

  async function openCustomLimitModal(target: UserInfo) {
    setCustomTarget(target);
    try {
      const custom = await UsersController.getUserCustomLimit(target.username);
      if (custom.customized) {
        setCustomUploadMbps(String(Math.round(custom.maxUploadBps / 1024 / 1024)));
        setCustomDownloadMbps(String(Math.round(custom.maxDownloadBps / 1024 / 1024)));
      } else {
        setCustomUploadMbps("0");
        setCustomDownloadMbps("0");
      }
    } catch {
      setCustomUploadMbps("0");
      setCustomDownloadMbps("0");
    }
    setIsCustomModalOpen(true);
  }

  async function submitCustomLimit() {
    if (!customTarget) return;
    const upload = Number(customUploadMbps);
    const download = Number(customDownloadMbps);
    if (!Number.isFinite(upload) || upload <= 0 || !Number.isFinite(download) || download <= 0) {
      toast.error(t("common.saveFailed"));
      return;
    }
    try {
      const updated = await UsersController.update(customTarget.userId, {
        displayName: customTarget.displayName || customTarget.username,
        role: customTarget.role.toLowerCase() === "admin" ? "admin" : "user",
        status: customTarget.status.toLowerCase() === "disabled" ? "disabled" : "active",
        qosProfile: customTarget.qosProfile || "default",
        customUploadBps: Math.round(upload * 1024 * 1024),
        customDownloadBps: Math.round(download * 1024 * 1024),
        qosCustomEnabled: true,
        uploadPaused: !!customTarget.uploadPaused,
        downloadPaused: !!customTarget.downloadPaused,
      });
      setUsers((prev) => prev.map((item) => (item.userId === updated.userId ? { ...item, ...updated } : item)));
      toast.success(t("common.saved"));
      setIsCustomModalOpen(false);
      setCustomTarget(null);
      void loadUsers();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("common.saveFailed"));
    }
  }

  async function applyRowQosProfile(target: UserInfo, nextProfile: string) {
    if (nextProfile === "custom") {
      await openCustomLimitModal(target);
      return;
    }
    try {
      const updated = await UsersController.update(target.userId, {
        displayName: target.displayName || target.username,
        role: target.role.toLowerCase() === "admin" ? "admin" : "user",
        status: target.status.toLowerCase() === "disabled" ? "disabled" : "active",
        qosProfile: nextProfile || "default",
        qosCustomEnabled: false,
        customUploadBps: target.qosCustomUploadBps ?? 0,
        customDownloadBps: target.qosCustomDownloadBps ?? 0,
        uploadPaused: !!target.uploadPaused,
        downloadPaused: !!target.downloadPaused,
      });
      setUsers((prev) => prev.map((item) => (item.userId === updated.userId ? { ...item, ...updated } : item)));
      toast.success(t("users.qos.governSaved"));
      void loadUsers();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("common.saveFailed"));
    }
  }

  /**
   * 用户提交入口：统一处理新增与编辑两条分支，并在失败时回填表单消息与 toast 双通道提示。
   * 前置条件：用户名、显示名（新增还需密码）通过校验；root 角色禁止在此流程降级或改写。
   */
  async function submitUser() {
    const perfStart = performance.now();
    console.info("[users] submitUser.click", { at: perfStart, editing: !!editing });
    setMsg("");
    if (!formUsername.trim()) return setMsg(t("users.usernameRequired"));
    if (!formDisplayName.trim()) return setMsg(t("users.displayNameRequired"));
    if (editing && editing.role.toLowerCase() === "root") return setMsg(t("users.rootImmutable"));
    const submittingToast = toast.loading(t("common.save"));
    setIsModalOpen(false);
    try {
      if (editing) {
        const beforeUpdate = performance.now();
        console.info("[users] submitUser.beforeUpdate", { deltaMs: Math.round(beforeUpdate - perfStart) });
        const updated = await UsersController.update(editing.userId, {
          displayName: formDisplayName.trim(),
          role: formRole,
          status: formStatus,
          qosProfile: formQosProfile === "custom" ? (editing.qosProfile || "default") : (formQosProfile.trim() || "default"),
          customUploadBps: formQosProfile === "custom" ? Math.round(Number(formCustomUploadMbps || "0") * 1024 * 1024) : (editing.qosCustomUploadBps ?? 0),
          customDownloadBps: formQosProfile === "custom" ? Math.round(Number(formCustomDownloadMbps || "0") * 1024 * 1024) : (editing.qosCustomDownloadBps ?? 0),
          qosCustomEnabled: formQosProfile === "custom",
          uploadPaused: formUploadPaused,
          downloadPaused: formDownloadPaused,
        });
        const afterUpdate = performance.now();
        console.info("[users] submitUser.afterUpdate", { apiAndNetworkMs: Math.round(afterUpdate - beforeUpdate), totalMs: Math.round(afterUpdate - perfStart) });
        setUsers((prev) => prev.map((item) => (item.userId === updated.userId ? { ...item, ...updated } : item)));
      } else {
        if (!formPassword.trim()) return setMsg(t("users.passwordRequiredOnCreate"));
        await UsersController.create({
          username: formUsername.trim(),
          password: formPassword,
          displayName: formDisplayName.trim(),
          role: formRole,
          qosProfile: formQosProfile.trim() || "default",
        });
      }
      toast.success(t("common.saved"), { id: submittingToast });
      // await loadUsers();
      console.info("[users] submitUser.scheduleReload", { totalMs: Math.round(performance.now() - perfStart) });
      void loadUsers();
    } catch (err) {
      toast.dismiss(submittingToast);
      setMsg(err instanceof Error ? err.message : t("common.saveFailed"));
      toast.error(err instanceof Error ? err.message : t("common.saveFailed"));
      void loadUsers();
    }
  }

  async function applyRowRole(target: UserInfo, nextRole: "user" | "admin") {
    try {
      if (target.role.toLowerCase() === nextRole) return;
      if (target.role.toLowerCase() === "root") {
        toast.error(t("users.rootImmutable"));
        return;
      }
      const updated = await UsersController.update(target.userId, {
        displayName: target.displayName || target.username,
        role: nextRole,
        status: target.status.toLowerCase() === "disabled" ? "disabled" : "active",
        qosProfile: target.qosProfile || "default",
      });
      setUsers((prev) => prev.map((item) => (item.userId === updated.userId ? { ...item, ...updated } : item)));
      toast.success(t("common.saved"));
      void loadUsers();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("common.saveFailed"));
    }
  }

  if (!user?.is_root) return <RootOnlyNoticeCard message={t("auth.rootOnlyUsers")} />;

  const formatBps = (bps?: number) => {
    const value = Math.max(0, Number(bps ?? 0));
    if (value >= 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB/s`;
    if (value >= 1024) return `${(value / 1024).toFixed(1)} KB/s`;
    return `${Math.round(value)} B/s`;
  };

  return (
    <div className="h-full w-full overflow-y-auto p-2 md:p-4">
      <div className="relative flex min-h-full flex-col gap-4">
        <div className="mb-2 flex flex-wrap items-center gap-2 rounded-sm border border-white/40 bg-white/60 px-4 py-2 shadow-sm backdrop-blur-sm dark:border-white/10 dark:bg-black/40">
          <ShadowTooltip content={t("common.refresh")}>
            <Button aria-label={t("common.refresh")} radius="sm" color="primary" size="sm" isIconOnly variant="flat" onPress={() => void loadUsers()} isLoading={loading}><FiRefreshCw /></Button>
          </ShadowTooltip>
          <ShadowTooltip content={t("users.createUser")}><Button aria-label={t("users.createUser")} radius="sm" color="primary" size="sm" isIconOnly variant="flat" onPress={openCreate}><FiPlus /></Button></ShadowTooltip>
          <ShadowTooltip content={t("users.qos.gotoCenter")}><Button aria-label={t("users.qos.gotoCenter")} radius="sm" color="secondary" size="sm" isIconOnly variant="flat" onPress={() => navigate("/app/qos")}><FiSettings /></Button></ShadowTooltip>
          <div className="ml-auto flex items-center gap-2">
            <LargeGlassInput
              size="sm"
              className="w-56"
              value={keyword}
              startContent={<FiSearch />}
              onValueChange={(value) => {
                setKeyword(value);
                setPage(1);
              }}
              placeholder={t("common.search")}
            />
          </div>
        </div>
        {msg && <p className="mb-2 text-sm text-default-600 dark:text-default-300">{msg}</p>}

        <PaginatedTableShell
          ariaLabel="users"
          rows={users.map((u) => ({ ...u, key: u.userId }))}
          totalLabel={() => t("fileManager.totalLabel", { count: total })}
          controlledPage={page}
          controlledPages={Math.max(1, Math.ceil(total / pageSize))}
          onPageChange={setPage}
          disableInternalSlice
          enablePageSizeInput
          defaultPageSize={pageSize}
          onPageSizeChange={(nextSize) => {
            const safe = nextSize <= 0 ? 200 : Math.max(1, Math.min(200, nextSize));
            setPageSize(safe);
            setPage(1);
          }}
          pageSizeLabel={t("fileManager.pageSizeLabel")}
          emptyContent={t("users.empty")}
          loading={loading}
          header={
            <>
              <TableColumn className="w-[14%]">{t("users.columns.username")}</TableColumn>
              <TableColumn className="w-[12%]">{t("users.columns.displayName")}</TableColumn>
              <TableColumn className="w-[14%]">{t("users.columns.role")}</TableColumn>
              <TableColumn className="w-[10%]">{t("users.columns.status")}</TableColumn>
              <TableColumn className="w-[40%]">{t("users.columns.qos")}</TableColumn>
              <TableColumn className="w-[10%]">{t("common.actions")}</TableColumn>
            </>
          }
          renderRow={(u) => (
            <TableRow key={u.key}>
              <TableCell>
                <span className="block max-w-full truncate" title={u.username}>{u.username}</span>
              </TableCell>
              <TableCell>
                <span className="block max-w-full truncate" title={u.displayName || ""}>{u.displayName}</span>
              </TableCell>
              <TableCell>
                {u.role.toLowerCase() === "root" ? (
                  <span className="inline-flex h-7 items-center rounded-md border border-default-300 bg-default-50 px-2 text-xs text-default-700 dark:border-default-600 dark:bg-default-100/20 dark:text-default-200">
                    root
                  </span>
                ) : (
                  <Select
                    aria-label={t("users.fields.role")}
                    disableAnimation
                    size="sm"
                    disallowEmptySelection
                    selectedKeys={[u.role.toLowerCase() === "admin" ? "admin" : "user"]}
                    className="max-w-[140px]"
                    classNames={{ trigger: "h-7 min-h-7", value: "text-xs" }}
                    onSelectionChange={(keys) => {
                      const value = String(Array.from(keys)[0] || "user");
                      void applyRowRole(u, value === "admin" ? "admin" : "user");
                    }}
                  >
                    <SelectItem key="user">{t("users.roles.user")}</SelectItem>
                    <SelectItem key="admin">{t("users.roles.admin")}</SelectItem>
                  </Select>
                )}
              </TableCell>
              <TableCell><span className="block max-w-full truncate" title={u.status}>{u.status}</span></TableCell>
              <TableCell>
                <div className="relative pt-4">
                  <span className="pointer-events-none absolute left-0 top-0 text-[10px] leading-3 text-default-500 dark:text-default-400">
                    {u.qosCustomEnabled
                      ? `(↑${u.uploadPaused ? t("common.disabled") : formatBps(u.qosCustomUploadBps)} / ↓${u.downloadPaused ? t("common.disabled") : formatBps(u.qosCustomDownloadBps)})`
                      : (() => {
                          const policy = qosOptions.find((item) => item.id === (u.qosProfile || "default"));
                          if (!policy) return "(↑- / ↓-)";
                          return `(↑${u.uploadPaused ? t("common.disabled") : formatBps(policy.maxUploadBps)} / ↓${u.downloadPaused ? t("common.disabled") : formatBps(policy.maxDownloadBps)})`;
                        })()}
                  </span>
                  <Select
                    aria-label={t("users.fields.qosProfile")}
                    disableAnimation
                    size="sm"
                    disallowEmptySelection
                    selectedKeys={[u.qosCustomEnabled ? "custom" : (u.qosProfile || "default")]}
                    className="max-w-[132px]"
                    classNames={{ trigger: "h-7 min-h-7", value: "text-xs" }}
                    onSelectionChange={(keys) => {
                      const value = String(Array.from(keys)[0] || "default");
                      void applyRowQosProfile(u, value);
                    }}
                  >
                    {[...qosOptions, { id: "custom", name: t("users.qos.custom"), maxUploadBps: 0, maxDownloadBps: 0 }].map((item) => (
                      <SelectItem key={item.id}>{item.name}</SelectItem>
                    ))}
                  </Select>
                </div>
              </TableCell>
              <TableCell>
                <div className="flex w-full items-center justify-start gap-1">
                  <ShadowTooltip content={t("users.editUser")}><Button aria-label={t("users.editUser")} size="sm" isIconOnly variant="flat" onPress={() => void openEdit(u)}><FiEdit2 /></Button></ShadowTooltip>
                </div>
              </TableCell>
            </TableRow>
          )}
        />

        <FormModal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={editing ? t("users.editUserTitle") : t("users.createUserTitle")} onSubmit={() => void submitUser()}>
          <LargeGlassInput label={t("users.fields.username")} size="sm" value={formUsername} onValueChange={setFormUsername} commitMode="blur" isReadOnly={!!editing} />
          <LargeGlassInput label={t("users.fields.displayName")} size="sm" value={formDisplayName} onValueChange={setFormDisplayName} commitMode="blur" />
          {!editing && <LargeGlassInput label={t("users.fields.password")} size="sm" type="password" value={formPassword} onValueChange={setFormPassword} commitMode="blur" />}
          <label className="flex flex-col gap-1">
            <span className="text-sm text-default-700 dark:text-default-300">{t("users.fields.role")}</span>
            {editing && editing.role.toLowerCase() === "root" ? (
              <div className="rounded-md border border-default-300 bg-default-50 px-3 py-2 text-sm text-default-700 dark:border-default-600 dark:bg-default-100/20 dark:text-default-200">
                root
              </div>
            ) : (
              <select
                className="rounded-md border border-default-300 bg-default-50 px-3 py-2 text-sm dark:border-default-600 dark:bg-default-100/20"
                value={formRole}
                onChange={(event) => setFormRole((event.target.value as "user" | "admin") || "user")}
              >
                <option value="user">{t("users.roles.user")}</option>
                <option value="admin">{t("users.roles.admin")}</option>
              </select>
            )}
          </label>
          {editing && (
            <label className="flex flex-col gap-1">
              <span className="text-sm text-default-700 dark:text-default-300">{t("users.fields.status")}</span>
              <select
                className="rounded-md border border-default-300 bg-default-50 px-3 py-2 text-sm dark:border-default-600 dark:bg-default-100/20"
                value={formStatus}
                onChange={(event) => setFormStatus((event.target.value as "active" | "disabled") || "active")}
              >
                <option value="active">{t("users.status.active")}</option>
                <option value="disabled">{t("users.status.disabled")}</option>
              </select>
            </label>
          )}
          {editing && (
            <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
              <Switch size="sm" isSelected={formUploadPaused} onValueChange={setFormUploadPaused}>
                {t("users.qos.pauseUpload")}
              </Switch>
              <Switch size="sm" isSelected={formDownloadPaused} onValueChange={setFormDownloadPaused}>
                {t("users.qos.pauseDownload")}
              </Switch>
            </div>
          )}
          <label className="flex flex-col gap-1">
            <span className="text-sm text-default-700 dark:text-default-300">{t("users.fields.qosProfile")}</span>
            <select
              className="rounded-md border border-default-300 bg-default-50 px-3 py-2 text-sm dark:border-default-600 dark:bg-default-100/20"
              value={formQosProfile}
              onChange={(event) => setFormQosProfile(event.target.value || "default")}
            >
              {(qosOptions.length > 0 ? qosOptions : [{ id: "default", name: "default", maxUploadBps: 0, maxDownloadBps: 0 }]).map((item) => (
                <option key={item.id} value={item.id}>{item.name}</option>
              ))}
              <option value="custom">{t("users.qos.custom")}</option>
            </select>
          </label>
          {editing && formQosProfile === "custom" && (
            <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
              <LargeGlassInput
                label={t("users.qos.maxUploadMbps")}
                size="sm"
                value={formCustomUploadMbps}
                onValueChange={setFormCustomUploadMbps}
                commitMode="blur"
              />
              <LargeGlassInput
                label={t("users.qos.maxDownloadMbps")}
                size="sm"
                value={formCustomDownloadMbps}
                onValueChange={setFormCustomDownloadMbps}
                commitMode="blur"
              />
            </div>
          )}
        </FormModal>

        <FormModal
          isOpen={isCustomModalOpen}
          onClose={() => setIsCustomModalOpen(false)}
          title={t("users.qos.custom")}
          onSubmit={() => void submitCustomLimit()}
          submitText={t("common.save")}
          cancelText={t("common.cancel")}
        >
          <LargeGlassInput
            label={t("users.qos.maxUploadMbps")}
            size="sm"
            value={customUploadMbps}
            onValueChange={setCustomUploadMbps}
            commitMode="blur"
          />
          <LargeGlassInput
            label={t("users.qos.maxDownloadMbps")}
            size="sm"
            value={customDownloadMbps}
            onValueChange={setCustomDownloadMbps}
            commitMode="blur"
          />
        </FormModal>
      </div>
    </div>
  );
}
