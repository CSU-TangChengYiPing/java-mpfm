import { Button, ButtonGroup } from "@heroui/button";
import { Checkbox } from "@heroui/checkbox";
import { Chip } from "@heroui/chip";
import { LargeGlassInput } from "../../../components/common/LargeGlassField";
import { ModalBody, ModalContent, ModalFooter, ModalHeader } from "@heroui/modal";
import { type SortDescriptor, TableCell, TableColumn, TableRow } from "@heroui/table";
import { Tab, Tabs } from "@heroui/tabs";
import { Tooltip } from "@heroui/tooltip";
import clsx from "clsx";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import toast from "react-hot-toast";
import { FiAlertCircle, FiCheck, FiCheckSquare, FiEdit2, FiLink2, FiPlus, FiRefreshCw, FiTrash2, FiX } from "react-icons/fi";
import { useNavigate } from "react-router-dom";
import key from "../../../const/key";
import PaginatedTableShell from "../../../components/common/PaginatedTableShell";
import ShadowTooltip from "../../../components/common/ShadowTooltip";
import MountsController, { type MountInfo } from "../../../controllers/mounts";
import { useAuth } from "../../../hooks/useAuth";
import BlurModal from "../../../components/common/BlurModal";

function protocolLabel(protocol: string, t: (k: string, o?: Record<string, unknown>) => string): string {
  const normalized = protocol.toLowerCase();
  if (normalized === "local") return t("mounts.local");
  if (normalized === "webdav") return t("mounts.webdavProtocol");
  return t("mounts.sftp");
}

type RemoteAddressParseResult = {
  host: string;
  portFromAddress?: number;
  remoteRootFromAddress?: string;
  scheme?: string;
};

/**
 * 远程地址兼容解析：支持纯 host，也支持 http(s)://host[:port]/path 的完整地址输入。
 */
function parseRemoteAddressInput(raw: string): RemoteAddressParseResult {
  const value = raw.trim();
  if (!value) return { host: "" };
  const maybeUrl = /^https?:\/\//i.test(value) ? value : null;
  if (!maybeUrl) {
    return { host: value };
  }
  try {
    const parsed = new URL(maybeUrl);
    const port = parsed.port ? Number.parseInt(parsed.port, 10) : undefined;
    const remoteRoot = parsed.pathname && parsed.pathname !== "/" ? parsed.pathname : undefined;
    return {
      host: parsed.hostname,
      portFromAddress: Number.isFinite(port) ? port : undefined,
      remoteRootFromAddress: remoteRoot,
      scheme: parsed.protocol.replace(":", "").toLowerCase(),
    };
  } catch {
    return { host: value };
  }
}

function shouldToastAddressParsed(rawHost: string, parsed: RemoteAddressParseResult): boolean {
  const value = rawHost.trim();
  return /^https?:\/\//i.test(value) && !!parsed.host;
}

/**
 * 推导后端真实地址：优先读取 VITE_BACKEND_ORIGIN；未配置时回落到 https://localhost:8443。
 */
function resolveBackendOrigin(): string {
  const envOrigin = (import.meta.env.VITE_BACKEND_ORIGIN ?? "").trim();
  if (envOrigin) {
    return envOrigin.replace(/\/+$/, "");
  }
  return "https://localhost:8443";
}

function ConfigHelpTip({ content }: { content: string }) {
  return (
    <Tooltip content={<div className="max-w-[320px] whitespace-pre-wrap text-xs">{content}</div>} showArrow>
      <span className="inline-flex h-5 w-5 cursor-help items-center justify-center rounded-full border border-default-300 bg-default-100 text-xs font-semibold text-default-700">?</span>
    </Tooltip>
  );
}

/** 挂载管理页：统一挂载列表、创建编辑、批量选择与共享入口。 */
export default function MountsPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [mounts, setMounts] = useState<MountInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());
  const [selectionMode, setSelectionMode] = useState(false);
  const [sortDescriptor, setSortDescriptor] = useState<SortDescriptor>({ column: "name", direction: "ascending" });

  const [mountName, setMountName] = useState("");
  const [mountProtocol, setMountProtocol] = useState<"local" | "sftp" | "webdav">("local");
  const [mountHost, setMountHost] = useState("");
  const [mountPort, setMountPort] = useState("22");
  const [mountUser, setMountUser] = useState("");
  const [mountPass, setMountPass] = useState("");
  const [mountRemoteRoot, setMountRemoteRoot] = useState("");
  const [mountEnabled, setMountEnabled] = useState(true);
  const [mountSharedEnabled, setMountSharedEnabled] = useState(false);
  const [createFieldErrors, setCreateFieldErrors] = useState<{ name?: string }>({});
  const [createFormError, setCreateFormError] = useState("");
  const [createConnectionVerified, setCreateConnectionVerified] = useState(false);
  const [checkingConnection, setCheckingConnection] = useState(false);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [editId, setEditId] = useState("");
  const [editName, setEditName] = useState("");
  const [editProtocol, setEditProtocol] = useState<"local" | "sftp" | "webdav">("sftp");
  const [editHost, setEditHost] = useState("");
  const [editPort, setEditPort] = useState("22");
  const [editUser, setEditUser] = useState("");
  const [editPass, setEditPass] = useState("");
  const [editRemoteRoot, setEditRemoteRoot] = useState("");
  const [editSharedEnabled, setEditSharedEnabled] = useState(false);
  const [editFieldErrors, setEditFieldErrors] = useState<{ name?: string }>({});
  const [editFormError, setEditFormError] = useState("");


  const inputClassNames = {
    inputWrapper: "h-12 bg-white/40 dark:bg-black/25 border border-white/30 dark:border-white/10 shadow-none group-data-[focus=true]:bg-white/45 dark:group-data-[focus=true]:bg-black/30 group-data-[focus=true]:border-primary/40",
  };

  function webdavPreviewPath(m: MountInfo): string {
    const root = (m.root ?? "").trim();
    if (root.startsWith("./")) {
      return `/${root.slice(2)}`;
    }
    const mount = (m.name ?? "").trim() || m.id;
    return `/personal/${mount}`;
  }

  function webdavWindowsHintPath(m: MountInfo): string {
    const basePath = webdavPreviewPath(m).split("/").join("\\");
    const backend = new URL(resolveBackendOrigin());
    const protocol = backend.protocol.replace(":", "").toLowerCase();
    const host = backend.hostname || "localhost";
    const port = backend.port || (protocol === "https" ? "443" : "80");
    if (protocol === "https") {
      return `\\\\${host}@SSL@${port}\\DavWWWRoot${basePath}`;
    }
    return `\\\\${host}@${port}\\DavWWWRoot${basePath}`;
  }

  function webdavHttpUrl(m: MountInfo): string {
    return `${resolveBackendOrigin()}/dav${webdavPreviewPath(m)}`;
  }

  function canManage(m: MountInfo) {
    if (user?.is_root) return true;
    if (m.can_manage) return true;
    return !!user && user.user_id === (m.owner_user ?? "");
  }

  async function fetchMounts() {
    setLoading(true);
    try {
      setMounts(await MountsController.list());
      if (!selectionMode) setSelectedKeys(new Set());
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("mounts.fetchFailed"));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const id = window.setTimeout(async () => {
      setLoading(true);
      try {
        setMounts(await MountsController.list());
        if (!selectionMode) setSelectedKeys(new Set());
      } catch (err) {
        toast.error(err instanceof Error ? err.message : t("mounts.fetchFailed"));
      } finally {
        setLoading(false);
      }
    }, 0);
    return () => window.clearTimeout(id);
  }, [selectionMode, t]);

  useEffect(() => {
    setCreateConnectionVerified(false);
  }, [mountProtocol, mountHost, mountPort, mountUser, mountPass, mountRemoteRoot]);

  function buildCreatePayload() {
    const payload: {
      name: string;
      protocol: "local" | "sftp" | "webdav";
      enabled: boolean;
      shared_enabled?: boolean;
      host?: string;
      port?: number;
      username?: string;
      password?: string;
      remote_root?: string;
    } = { name: mountName.trim(), protocol: mountProtocol, enabled: mountEnabled };
    payload.shared_enabled = mountSharedEnabled;

    if (!payload.name) {
      setCreateFieldErrors({ name: t("mounts.nameRequired") });
      return null;
    }
    if (mountProtocol !== "local") {
      const parsedAddress = parseRemoteAddressInput(mountHost);
      const resolvedHost = parsedAddress.host.trim();
      const resolvedRemoteRoot = mountRemoteRoot.trim() || parsedAddress.remoteRootFromAddress || "";
      const resolvedPortRaw = mountPort.trim() || (parsedAddress.portFromAddress ? String(parsedAddress.portFromAddress) : "");
      const portValue = Number.parseInt(resolvedPortRaw, 10);
      if (!resolvedHost || !mountUser.trim() || !mountPass.trim() || !resolvedRemoteRoot) {
        const message = t("mounts.sftpRequired");
        setCreateFormError(message);
        toast.error(message);
        return null;
      }
      if (!Number.isFinite(portValue) || portValue <= 0) {
        const message = t("mounts.invalidPort");
        setCreateFormError(message);
        toast.error(message);
        return null;
      }
      if (mountProtocol === "webdav" && shouldToastAddressParsed(mountHost, parsedAddress)) {
        toast.success(t("mounts.addressAutoParsed"));
      }
      payload.host = resolvedHost;
      payload.port = portValue;
      payload.username = mountUser.trim();
      payload.password = mountPass;
      payload.remote_root = resolvedRemoteRoot;
    }
    return payload;
  }

  const sortedMounts = useMemo(() => {
    const copy = [...mounts];
    copy.sort((a, b) => {
        const dir = sortDescriptor.direction === "descending" ? -1 : 1;
      switch (sortDescriptor.column) {
        case "protocol":
          return dir * a.protocol.localeCompare(b.protocol);
        case "status":
          return dir * (Number(b.enabled) - Number(a.enabled));
        default:
          return dir * a.name.localeCompare(b.name, "zh-Hans-CN", { sensitivity: "base", numeric: true });
      }
    });
    return copy;
  }, [mounts, sortDescriptor]);

  /** 创建挂载流程：执行字段校验、能力门禁与失败分层提示。 */
  async function createMount() {
    setCreateFieldErrors({});
    setCreateFormError("");
    const payload = buildCreatePayload();
    if (!payload) {
      return;
    }
    if (!createConnectionVerified) {
      const message = t("mounts.mustTestBeforeCreate");
      setCreateFormError(message);
      toast.error(message);
      return;
    }

    try {
      await MountsController.create(payload);
      toast.success(t("mounts.createSuccess"));
      setIsCreateModalOpen(false);
      setMountName("");
      setMountHost("");
      setMountPort("22");
      setMountUser("");
      setMountPass("");
      setMountRemoteRoot("");
      setMountEnabled(true);
      setMountSharedEnabled(false);
      setCreateFieldErrors({});
      setCreateFormError("");
      setCreateConnectionVerified(false);
      await fetchMounts();
    } catch (err) {
      const message = err instanceof Error ? err.message : t("mounts.createFailed");
      setCreateFormError(message);
      toast.error(message);
    }
  }

  async function mountAction(action: "enable" | "disable", id: string) {
    try {
      await MountsController.action(action, id);
      toast.success(t("mounts.actionDone", { action: action === "enable" ? t("common.enable") : t("common.disable") }));
      await fetchMounts();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("mounts.actionFailed", { action }));
    }
  }

  async function batchAction(action: "enable" | "disable") {
    const ids = Array.from(selectedKeys);
    let fail = 0;
    for (const id of ids) {
      try {
        await MountsController.action(action, id);
      } catch {
        fail += 1;
      }
    }
    await fetchMounts();
    if (fail > 0) toast.error(t("mounts.batchFailed", { count: fail }));
    else toast.success(t("mounts.batchDone"));
  }

  async function batchDeleteMounts() {
    const ids = Array.from(selectedKeys);
    if (ids.length === 0) return;
    if (!window.confirm(t("mounts.batchDeleteConfirm", { count: ids.length }))) {
      return;
    }
    let fail = 0;
    for (const id of ids) {
      try {
        await MountsController.deleteMount(id);
      } catch {
        fail += 1;
      }
    }
    await fetchMounts();
    if (fail > 0) toast.error(t("mounts.batchFailed", { count: fail }));
    else toast.success(t("mounts.batchDone"));
  }

  async function toggleMountEnabled(m: MountInfo) {
    await mountAction(m.enabled ? "disable" : "enable", m.id);
  }

  function openEditModal(m: MountInfo) {
    const root = (m.root ?? "").trim();
    let hostPart = "";
    let portPart = m.protocol === "sftp" ? "22" : "443";
    let userPart = "";
    let remoteRootPart: string;
    try {
      const url = new URL(root);
      hostPart = url.hostname;
      portPart = url.port || portPart;
      userPart = decodeURIComponent(url.username || "");
      remoteRootPart = decodeURIComponent(url.pathname || "");
    } catch {
      remoteRootPart = root;
    }
    setEditId(m.id);
    setEditName(m.name);
    setEditProtocol((m.protocol === "local" ? "local" : m.protocol === "webdav" ? "webdav" : "sftp"));
    setEditHost(hostPart || "");
    setEditPort(portPart || "22");
    setEditUser(userPart || m.username || "");
    setEditPass("");
    setEditRemoteRoot(remoteRootPart || "/");
    setEditSharedEnabled(!!m.shared_enabled);
    setIsEditModalOpen(true);
  }

  async function testConnectionBeforeCreate() {
    setCreateFieldErrors({});
    setCreateFormError("");
    const payload = buildCreatePayload();
    if (!payload) {
      return;
    }
    setCheckingConnection(true);
    try {
      await MountsController.testConnection({
        protocol: payload.protocol,
        host: payload.host,
        port: payload.port,
        username: payload.username,
        password: payload.password,
        remote_root: payload.remote_root,
      });
      setCreateConnectionVerified(true);
      toast.success(t("mounts.connectionTestPassed"));
    } catch (err) {
      setCreateConnectionVerified(false);
      const message = err instanceof Error ? err.message : t("mounts.connectionTestFailed");
      setCreateFormError(message);
      toast.error(message);
    } finally {
      setCheckingConnection(false);
    }
  }

  /**
   * 编辑挂载提交流程：先做前端字段校验，再按协议组装最小更新载荷，失败统一落到表单级错误区并补充 toast。
   */
  async function submitEditMount() {
    setEditFieldErrors({});
    setEditFormError("");
    try {
      if (!editId) return;
      if (!editName.trim()) {
        setEditFieldErrors({ name: t("mounts.nameRequired") });
        return;
      }
      const payload: {
        name: string;
        shared_enabled: boolean;
        host?: string;
        port?: number;
        username?: string;
        password?: string;
        remote_root?: string;
      } = { name: editName.trim(), shared_enabled: editSharedEnabled };
      if (editProtocol !== "local") {
        const parsedAddress = parseRemoteAddressInput(editHost);
        const resolvedHost = parsedAddress.host.trim();
        const resolvedRemoteRoot = editRemoteRoot.trim() || parsedAddress.remoteRootFromAddress || "";
        const resolvedPortRaw = editPort.trim() || (parsedAddress.portFromAddress ? String(parsedAddress.portFromAddress) : "");
        const portValue = Number.parseInt(resolvedPortRaw, 10);
        if (!resolvedHost || !editUser.trim() || !resolvedRemoteRoot) {
          const message = t("mounts.sftpRequired");
          setEditFormError(message);
          toast.error(message);
          return;
        }
        if (!Number.isFinite(portValue) || portValue <= 0) {
          const message = t("mounts.invalidPort");
          setEditFormError(message);
          toast.error(message);
          return;
        }
        if (editProtocol === "webdav" && shouldToastAddressParsed(editHost, parsedAddress)) {
          toast.success(t("mounts.addressAutoParsed"));
        }
        payload.host = resolvedHost;
        payload.port = portValue;
        payload.username = editUser.trim();
        payload.remote_root = resolvedRemoteRoot;
        if (editPass.trim()) {
          payload.password = editPass;
        }
      }
      await MountsController.update(editId, payload);
      setIsEditModalOpen(false);
      toast.success(t("mounts.updated"));
      await fetchMounts();
    } catch (err) {
      const message = err instanceof Error ? err.message : t("mounts.updateFailed");
      setEditFormError(message);
      toast.error(message);
    }
  }

  async function deleteMount(m: MountInfo) {
    if (!window.confirm(t("mounts.deleteConfirm", { name: m.name }))) {
      return;
    }
    try {
      await MountsController.deleteMount(m.id);
      toast.success(t("mounts.deleteSuccess"));
      await fetchMounts();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("mounts.deleteFailed"));
    }
  }

  const selectedCountLabel = selectedKeys.size;
  const hasBackground = !!(localStorage.getItem(key.backgroundImage) ?? "");

  return (
    <div className="h-full w-full p-2 md:p-4">
      <div
        className={clsx(
          "mb-4 flex flex-col md:flex-row items-stretch md:items-center gap-4 sticky top-14 z-10 backdrop-blur-sm shadow-sm py-2 px-4 rounded-sm transition-colors",
          hasBackground ? "bg-white/20 dark:bg-black/10 border border-white/40 dark:border-white/10" : "bg-white/60 dark:bg-black/40 border border-white/40 dark:border-white/10"
        )}
      >
        <div className="flex items-center gap-2 overflow-x-auto pb-1 md:pb-0">
          <ShadowTooltip content={t("common.refresh")}><Button aria-label={t("common.refresh")} radius="sm" color="primary" size="sm" isIconOnly variant="flat" onPress={() => void fetchMounts()} isLoading={loading} className="text-lg min-w-8">
            <FiRefreshCw />
          </Button></ShadowTooltip>
          <ShadowTooltip content={selectionMode ? t("mounts.exitMultiSelect") : t("mounts.enterMultiSelect")}><Button
            aria-label={selectionMode ? t("mounts.exitMultiSelect") : t("mounts.enterMultiSelect")}
            radius="sm"
            color="primary"
            size="sm"
            isIconOnly
            variant={selectionMode ? "solid" : "flat"}
            onPress={() => {
              setSelectionMode((prev) => {
                if (prev) setSelectedKeys(new Set());
                return !prev;
              });
            }}
            className="text-lg min-w-8"
          >
            {selectionMode ? <FiX /> : <FiCheckSquare />}
          </Button></ShadowTooltip>
          {selectedCountLabel > 0 && (
            <>
              <Button radius="sm" color="success" size="sm" variant="flat" className="text-sm px-2 min-w-fit" startContent={<FiCheck className="text-lg" />} onPress={() => void batchAction("enable")}>({selectedCountLabel})</Button>
              <Button radius="sm" color="warning" size="sm" variant="flat" className="text-sm px-2 min-w-fit" startContent={<FiX className="text-lg" />} onPress={() => void batchAction("disable")}>({selectedCountLabel})</Button>
              <Button radius="sm" color="danger" size="sm" variant="flat" className="text-sm px-2 min-w-fit" startContent={<FiTrash2 className="text-lg" />} onPress={() => void batchDeleteMounts()}>({selectedCountLabel})</Button>
            </>
          )}
        </div>
        <div className="ml-auto">
          <span className="mr-3 text-xs text-default-500 align-middle">{t("mounts.sessionLabel")}: {user?.is_root ? t("mounts.sessionRoot") : t("mounts.sessionUser")}</span>
          <Button radius="sm" color="primary" onPress={() => {
            setCreateConnectionVerified(false);
            setIsCreateModalOpen(true);
          }} startContent={<FiPlus />}>{t("mounts.create")}</Button>
        </div>
      </div>

      <PaginatedTableShell
        ariaLabel={t("mounts.table")}
        rows={sortedMounts.map((m) => ({ ...m, key: m.id }))}
        loading={loading}
        selectionMode={selectionMode ? "multiple" : "none"}
        selectedKeys={selectedKeys}
        sortDescriptor={sortDescriptor}
        onSortChange={(d) => setSortDescriptor(d)}
        onSelectionChange={setSelectedKeys}
        totalLabel={(total) => t("mounts.total", { count: total })}
        defaultPageSize={12}
        emptyContent={t("mounts.empty")}
        header={
          <>
              <TableColumn key="name" allowsSorting>{t("mounts.name")}</TableColumn>
              <TableColumn key="shared">{t("mounts.shared")}</TableColumn>
              <TableColumn key="status" allowsSorting>{t("mounts.status")}</TableColumn>
              <TableColumn key="webdav_path" className="hidden lg:table-cell">{t("mounts.webdav")}</TableColumn>
              <TableColumn key="actions">{t("common.actions")}</TableColumn>
          </>
        }
        renderRow={(m) => (
                <TableRow key={m.key}>
                  <TableCell>
                    <div className="flex flex-col gap-1">
                      <span className="font-medium">{m.name}</span>
                      <div className="text-xs text-default-500">{protocolLabel(m.protocol, t)}</div>
                    </div>
                  </TableCell>
                  <TableCell>
                    <Chip size="sm" variant="flat" color={m.shared_enabled ? "success" : "default"}>
                      {m.shared_enabled ? t("mounts.on") : t("mounts.off")}
                    </Chip>
                  </TableCell>
                  <TableCell>
                    <Chip size="sm" variant="flat" color={m.enabled ? "success" : "warning"}>
                      {m.enabled ? t("common.enabled") : t("common.disabled")}
                    </Chip>
                  </TableCell>
                  <TableCell className="hidden lg:table-cell">
                    <div className="text-xs">
                      <div className="font-medium">{webdavPreviewPath(m)}</div>
                      <div className="text-default-500">{webdavHttpUrl(m)}</div>
                      <div className="text-default-500">{webdavWindowsHintPath(m)}</div>
                    </div>
                  </TableCell>
                  <TableCell>
                    <ButtonGroup radius="sm" size="sm" variant="light">
                      {m.last_error && (
                        <Tooltip
                          content={<div className="max-w-[280px] whitespace-pre-wrap break-words text-xs">{m.last_error}</div>}
                          color="danger"
                          showArrow
                          classNames={{ content: "bg-danger-500/90 text-white backdrop-blur-md" }}
                        >
                          <span className="inline-flex h-8 w-8 items-center justify-center rounded-md text-danger-500">
                            <FiAlertCircle />
                          </span>
                        </Tooltip>
                      )}
                      {canManage(m) && (
                        <>
                          <Button
                            size="sm"
                            color={m.enabled ? "warning" : "success"}
                            onPress={() => void toggleMountEnabled(m)}
                          >
                            {m.enabled ? t("common.disable") : t("common.enable")}
                          </Button>
                          <ShadowTooltip content={t("mounts.editMount")}><Button aria-label={t("mounts.editMount")} size="sm" onPress={() => openEditModal(m)} isIconOnly><FiEdit2 /></Button></ShadowTooltip>
                          <ShadowTooltip content={t("common.delete")}><Button aria-label={t("common.delete")} size="sm" color="danger" variant="flat" onPress={() => void deleteMount(m)} isIconOnly><FiTrash2 /></Button></ShadowTooltip>
                          <Button
                            aria-label={t("mounts.manageShare")}
                            title={t("mounts.manageShare")}
                            size="sm"
                            color="secondary"
                            variant="flat"
                            isIconOnly
                            isDisabled={!m.shared_enabled}
                            onPress={() => navigate(`/app/shares/shared-users?mountId=${encodeURIComponent(m.id)}`)}
                          >
                            <FiLink2 />
                          </Button>
                        </>
                      )}
                    </ButtonGroup>
                  </TableCell>
                </TableRow>
              )}
      />

      <BlurModal isOpen={isCreateModalOpen} onClose={() => {
        setCreateConnectionVerified(false);
        setIsCreateModalOpen(false);
      }} radius="sm">
        <ModalContent>
          <ModalHeader>{t("mounts.create")}</ModalHeader>
          <ModalBody className="gap-3">
            {createFormError && <div className="rounded-md border border-danger-300 bg-danger-50 px-3 py-2 text-sm text-danger-700">{createFormError}</div>}
              <LargeGlassInput
                label={t("mounts.name")}
                size="md"
                value={mountName}
                onValueChange={(value) => {
                  setMountName(value);
                  setCreateFieldErrors((prev) => ({ ...prev, name: undefined }));
                }}
                commitMode="blur"
                classNames={inputClassNames}
                isInvalid={!!createFieldErrors.name}
                errorMessage={createFieldErrors.name}
              />
            <Tabs
              selectedKey={mountProtocol}
              onSelectionChange={(k) => setMountProtocol(String(k) as "local" | "sftp" | "webdav")}
              size="sm"
              color="primary"
              variant="solid"
            >
              <Tab key="sftp" title="SFTP" />
              <Tab key="webdav" title="WebDAV" />
              <Tab key="local" title={t("mounts.localShort")} />
            </Tabs>
            <div className="space-y-2">
              <fieldset
                className={clsx(
                  "overflow-hidden transition-[max-height,opacity] duration-300 ease-out",
                  mountProtocol === "local" ? "max-h-20 opacity-100" : "max-h-0 opacity-0",
                )}
              >
                <div className="rounded-md border border-success-300/70 bg-success-50/55 px-3 py-2 text-xs text-success-800 backdrop-blur-sm">{t("mounts.localRootAutoHint")}</div>
              </fieldset>
              <fieldset
                className={clsx(
                  "space-y-2 overflow-hidden transition-[max-height,opacity] duration-300 ease-out",
                  mountProtocol === "local" ? "max-h-0 opacity-0" : "max-h-[420px] opacity-100",
                )}
              >
                {mountProtocol === "webdav" && (
                  <div className="flex items-center gap-2 rounded-md border border-default-200/60 bg-white/35 px-3 py-2 text-xs text-default-700 backdrop-blur-sm">
                    <span>{t("mounts.webdavConfigHelpTitle")}</span>
                    <ConfigHelpTip content={t("mounts.webdavConfigHelpDetail")} />
                  </div>
                )}
                <LargeGlassInput label={t("mounts.host")} size="md" value={mountHost} onValueChange={setMountHost} commitMode="blur" classNames={inputClassNames} />
                <LargeGlassInput label={t("mounts.port")} size="md" value={mountPort} onValueChange={setMountPort} commitMode="blur" classNames={inputClassNames} />
                <LargeGlassInput label={t("mounts.username")} size="md" value={mountUser} onValueChange={setMountUser} commitMode="blur" classNames={inputClassNames} />
                <LargeGlassInput label={t("mounts.password")} size="md" type="password" value={mountPass} onValueChange={setMountPass} commitMode="blur" classNames={inputClassNames} />
                <LargeGlassInput label={t("mounts.remoteRoot")} size="md" value={mountRemoteRoot} onValueChange={setMountRemoteRoot} commitMode="blur" classNames={inputClassNames} />
              </fieldset>
            </div>
            <Checkbox isSelected={mountSharedEnabled} onValueChange={setMountSharedEnabled}>{t("mounts.sharedEnabledSwitch")}</Checkbox>
            <Checkbox isSelected={mountEnabled} onValueChange={setMountEnabled}>{t("mounts.enableAfterCreate")}</Checkbox>
          </ModalBody>
          <ModalFooter>
            <Button variant="flat" onPress={() => setIsCreateModalOpen(false)}>{t("common.cancel")}</Button>
            <Button variant="flat" color="secondary" isLoading={checkingConnection} onPress={() => void testConnectionBeforeCreate()}>{t("mounts.testConnection")}</Button>
            <Button color="primary" onPress={() => void createMount()} isDisabled={!createConnectionVerified}>{t("common.create")}</Button>
          </ModalFooter>
        </ModalContent>
      </BlurModal>

      <BlurModal isOpen={isEditModalOpen} onClose={() => setIsEditModalOpen(false)} radius="sm">
        <ModalContent>
          <ModalHeader>{t("mounts.edit")}</ModalHeader>
          <ModalBody className="gap-3">
            {editFormError && <div className="rounded-md border border-danger-300 bg-danger-50 px-3 py-2 text-sm text-danger-700">{editFormError}</div>}
              <LargeGlassInput
                label={t("mounts.name")}
                size="md"
                value={editName}
                onValueChange={(value) => {
                  setEditName(value);
                  setEditFieldErrors((prev) => ({ ...prev, name: undefined }));
                }}
                commitMode="blur"
                classNames={inputClassNames}
                isInvalid={!!editFieldErrors.name}
                errorMessage={editFieldErrors.name}
              />
            <div className="rounded-md border border-default-200 bg-default-50 px-3 py-2 text-xs text-default-700">{t("mounts.protocolFixedHint")}</div>
            <div className="space-y-2">
              <fieldset
                className={clsx(
                  "overflow-hidden transition-[max-height,opacity] duration-300 ease-out",
                  editProtocol === "local" ? "max-h-20 opacity-100" : "max-h-0 opacity-0",
                )}
              >
                <div className="rounded-md border border-success-300/70 bg-success-50/55 px-3 py-2 text-xs text-success-800 backdrop-blur-sm">{t("mounts.localRootAutoHint")}</div>
              </fieldset>
              <fieldset
                className={clsx(
                  "space-y-2 overflow-hidden transition-[max-height,opacity] duration-300 ease-out",
                  editProtocol === "local" ? "max-h-0 opacity-0" : "max-h-[420px] opacity-100",
                )}
              >
                {editProtocol === "webdav" && (
                  <div className="flex items-center gap-2 rounded-md border border-default-200/60 bg-white/35 px-3 py-2 text-xs text-default-700 backdrop-blur-sm">
                    <span>{t("mounts.webdavConfigHelpTitle")}</span>
                    <ConfigHelpTip content={t("mounts.webdavConfigHelpDetail")} />
                  </div>
                )}
                <LargeGlassInput label={t("mounts.host")} size="md" value={editHost} onValueChange={setEditHost} commitMode="blur" classNames={inputClassNames} />
                <LargeGlassInput label={t("mounts.port")} size="md" value={editPort} onValueChange={setEditPort} commitMode="blur" classNames={inputClassNames} />
                <LargeGlassInput label={t("mounts.username")} size="md" value={editUser} onValueChange={setEditUser} commitMode="blur" classNames={inputClassNames} />
                <LargeGlassInput label={t("mounts.passwordOptional")} size="md" type="password" value={editPass} onValueChange={setEditPass} commitMode="blur" classNames={inputClassNames} />
                <LargeGlassInput label={t("mounts.remoteRoot")} size="md" value={editRemoteRoot} onValueChange={setEditRemoteRoot} commitMode="blur" classNames={inputClassNames} />
              </fieldset>
            </div>
            <Checkbox isSelected={editSharedEnabled} onValueChange={setEditSharedEnabled}>{t("mounts.sharedEnabledSwitch")}</Checkbox>
          </ModalBody>
          <ModalFooter>
            <Button variant="flat" onPress={() => setIsEditModalOpen(false)}>{t("common.cancel")}</Button>
            <Button color="primary" onPress={() => void submitEditMount()}>{t("common.save")}</Button>
          </ModalFooter>
        </ModalContent>
      </BlurModal>
    </div>
  );
}


