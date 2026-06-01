import { BreadcrumbItem, Breadcrumbs } from "@heroui/breadcrumbs";
import { Button } from "@heroui/button";
import { Input } from "@heroui/input";
import { Select, SelectItem } from "@heroui/select";
import { Tab, Tabs } from "@heroui/tabs";
import { type SortDescriptor } from "@heroui/table";
import { FiCheck, FiCheckSquare, FiPlus, FiShield, FiUpload, FiX } from "react-icons/fi";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { MdRefresh } from "react-icons/md";
import { TiArrowBack } from "react-icons/ti";
import toast from "react-hot-toast";
import FormModal from "../../../components/common/FormModal";
import PermissionLevelSelector from "../../../components/common/PermissionLevelSelector";
import FileTable from "../../../components/file_manage/file_table";
import ShadowTooltip from "../../../components/common/ShadowTooltip";
import type { FileInfo } from "../../../controllers/file_manager";
import type { MountInfo } from "../../../controllers/mounts";

type Selection = Set<string | number> | "all";
type Row = { key: string; role: string; path_scopes?: string[]; permissions?: string[] };

/** 角色路径权限面板：统一编辑授权、预览授权与路径级规则变更入口。 */
export default function RolePermissionsPanel(props: {
  selectedMountID: string;
  manageableMounts: MountInfo[];
  grantRole: string;
  presetRoleList: string[];
  roleNameMap: Map<string, string>;
  browsePath: string;
  browseFiles: FileInfo[];
  loading: boolean;
  sortDescriptor: SortDescriptor;
  selectedFiles: Selection;
  currentIsRoot?: boolean;
  grantRows: Row[];
  mode: "edit" | "preview";
  previewSortDescriptor: SortDescriptor;
  selectionModeEnabled: boolean;
  onMountChange: (v: string) => void;
  onGrantRoleChange: (v: string) => void;
  onModeChange: (v: "edit" | "preview") => void;
  onGoParentDir: () => void;
  onApplyPermissionsToPaths: (relativePaths: string[], permissions: string[], mountId: string) => void;
  onToggleSelectionMode: () => void;
  onSortChange: (v: SortDescriptor) => void;
  onPreviewSortChange: (v: SortDescriptor) => void;
  onSelectionChange: (v: Selection) => void;
  onDirectoryClick: (dirPath: string) => void;
  onBrowsePathChange: (v: string) => void;
  onPreviewJumpPathRequest: (v: string) => void;
  onClearSpecialPermissions: (relativePath: string) => void;
  onRefreshPreview: () => void;
}) {
  const p = props;
  const { t } = useTranslation();
  const [isPathEditing, setIsPathEditing] = useState(false);
  const [jumpPath, setJumpPath] = useState("");
  const [permModalOpen, setPermModalOpen] = useState(false);
  const [permCursor, setPermCursor] = useState<number>(2);
  const [targetPaths, setTargetPaths] = useState<string[]>([]);
  const [targetRelativePaths, setTargetRelativePaths] = useState<string[]>([]);
  const [singleTargetPath, setSingleTargetPath] = useState<string>("");
  const [singleHasOverride, setSingleHasOverride] = useState(false);
  const selectedMountName = useMemo(() => {
    const hit = p.manageableMounts.find((m) => m.id === p.selectedMountID);
    return (hit?.name || "").trim();
  }, [p.manageableMounts, p.selectedMountID]);
  const logicPrefix = useMemo(() => (p.selectedMountID ? `/personal/${p.selectedMountID}` : ""), [p.selectedMountID]);
  const displayPath = useMemo(() => {
    if (!logicPrefix) return ".";
    let normalizedLogic = (p.browsePath || ".").replace(/\\/g, "/").replace(/\/+/g, "/");
    if (normalizedLogic !== "." && !normalizedLogic.startsWith("/")) normalizedLogic = `/${normalizedLogic}`;
    const namePrefix = selectedMountName ? `/personal/${selectedMountName}` : "";
    let suffix = "";
    if (normalizedLogic.startsWith(logicPrefix)) {
      suffix = normalizedLogic.slice(logicPrefix.length);
    } else if (namePrefix && normalizedLogic.startsWith(namePrefix)) {
      suffix = normalizedLogic.slice(namePrefix.length);
    } else if (normalizedLogic.startsWith("/personal/")) {
      const parts = normalizedLogic.split("/").filter(Boolean);
      suffix = parts.length > 2 ? `/${parts.slice(2).join("/")}` : "/";
    } else {
      return ".";
    }
    if (!suffix || suffix === "/") return "/";
    return suffix.startsWith("/") ? suffix : `/${suffix}`;
  }, [logicPrefix, p.browsePath, selectedMountName]);
  const selectedCount = useMemo(() => (p.selectedFiles instanceof Set ? p.selectedFiles.size : p.browseFiles.length), [p.browseFiles.length, p.selectedFiles]);
  const canGoParent = useMemo(() => {
    const normalized = (p.browsePath || ".").replace(/\\/g, "/").replace(/\/+/g, "/");
    if (!normalized || normalized === "." || normalized === "/") return false;
    const parts = normalized.split("/").filter(Boolean);
    if (parts.length === 0) return false;
    const scope = parts[0];
    if ((scope === "personal" || scope === "shared") && parts.length <= 2) return false;
    return true;
  }, [p.browsePath]);

  useEffect(() => {
    if (!isPathEditing) {
      setJumpPath(displayPath);
    }
  }, [displayPath, isPathEditing]);

  function normalizeVirtualPathInput(raw: string, current: string): string {
    const cleaned = (raw || "").trim().replace(/\\/g, "/");
    if (!cleaned || cleaned === ".") return current || "/";
    if (/^\.\/(personal|shared)\//i.test(cleaned)) {
      const withoutDot = cleaned.slice(1);
      const parts = withoutDot.split("/").filter(Boolean);
      const relative = parts.length > 2 ? `/${parts.slice(2).join("/")}` : "/";
      return relative;
    }
    if (cleaned.startsWith("/personal/")) {
      const parts = cleaned.split("/").filter(Boolean);
      const relative = parts.length > 2 ? `/${parts.slice(2).join("/")}` : "/";
      return relative;
    }
    const base = (current || ".").replace(/\\/g, "/");
    let merged = cleaned.startsWith("/") ? cleaned : `${base.replace(/\/+$/, "")}/${cleaned}`;
    merged = merged.replace(/\/+/g, "/");
    while (merged.includes("/./")) merged = merged.replace("/./", "/");
    const parts = merged.split("/");
    const stack: string[] = [];
    for (const part of parts) {
      if (!part || part === ".") continue;
      if (part === "..") {
        if (stack.length > 0) stack.pop();
        continue;
      }
      stack.push(part);
    }
    const normalized = `/${stack.join("/")}`;
    return normalized || "/";
  }

  function displayToLogicPath(inputPath: string): string {
    if (!logicPrefix) return ".";
    const relative = normalizeVirtualPathInput(inputPath, displayPath);
    const suffix = relative === "/" ? "" : relative;
    return `${logicPrefix}${suffix}`.replace(/\/+/g, "/");
  }

  function resolveJumpPath(input: string): string {
    const next = displayToLogicPath(input);
    return next;
  }

  function confirmJumpPath() {
    const next = resolveJumpPath(jumpPath);
    setIsPathEditing(false);
    if (p.mode === "preview") {
      p.onPreviewJumpPathRequest(next);
    } else {
      p.onBrowsePathChange(next);
    }
  }

  function toAbsPath(name: string): string {
    if (name.startsWith("/")) return name;
    if (p.browsePath === "." || p.browsePath === "/") return `/${name}`;
    return `/${p.browsePath.replace(/^\.?\/*/, "")}/${name}`.replace(/\/+/g, "/");
  }

  /** 逻辑路径转显示路径：移除 personal/mountId 前缀，统一显示为挂载内相对路径。 */
  function logicToDisplayRelative(pathValue: string): string {
    const raw = (pathValue || "/").replace(/\\/g, "/").replace(/\/+/g, "/");
    const partsRaw = raw.split("/");
    const stack: string[] = [];
    for (const part of partsRaw) {
      if (!part || part === ".") continue;
      if (part === "..") {
        if (stack.length > 0) stack.pop();
        continue;
      }
      stack.push(part);
    }
    const normalized = `/${stack.join("/")}` || "/";
    const personalIndex = (() => {
      for (let i = stack.length - 2; i >= 0; i -= 1) {
        if (stack[i] === "personal") return i;
      }
      return -1;
    })();
    if (personalIndex >= 0) {
      const suffix = stack.slice(personalIndex + 2);
      return suffix.length > 0 ? `/${suffix.join("/")}` : "/";
    }
    if (!logicPrefix) return normalized.startsWith("/") ? normalized : `/${normalized}`;
    if (normalized === logicPrefix) return "/";
    if (normalized.startsWith(`${logicPrefix}/`)) {
      return normalized.slice(logicPrefix.length) || "/";
    }
    const parts = normalized.split("/").filter(Boolean);
    if (parts.length >= 2 && parts[0] === "personal") {
      return parts.length > 2 ? `/${parts.slice(2).join("/")}` : "/";
    }
    return normalized.startsWith("/") ? normalized : `/${normalized}`;
  }

  /** 挂载内相对路径用于 UI/回调时不带前导斜杠，根目录返回空串。 */
  function toRelativePathValue(pathValue: string): string {
    const normalized = logicToDisplayRelative(pathValue);
    if (normalized === "/") return "";
    return normalized.replace(/^\/+/, "").replace(/^personal\/[^/]+\//, "");
  }

  /** 清理回调入参：确保传给后端协议层的是无 personal 前缀、无前导斜杠的相对路径。 */
  function toProtocolRelativePath(pathValue: string): string {
    return toRelativePathValue(pathValue);
  }

  function openBulkPermissionModal() {
    const selection = p.selectedFiles === "all" ? new Set(p.browseFiles.map((f) => f.name)) : p.selectedFiles;
    const relativePaths = Array.from(selection)
      .map((k) => p.browseFiles.find((f) => f.name === String(k)))
      .filter((f): f is FileInfo => !!f)
      .map((f) => toRelativePathValue(toAbsPath(f.name)));
    if (relativePaths.length === 0) return;
    setSingleTargetPath("");
    setSingleHasOverride(false);
    setTargetRelativePaths(relativePaths);
    setTargetPaths(relativePaths);
    setPermModalOpen(true);
  }

  function openSinglePermissionModal(filePath: string) {
    const abs = toAbsPath(filePath);
    const relative = toRelativePathValue(abs);
    const row = p.browseFiles.find((f) => f.name === filePath.split("/").filter(Boolean).slice(-1)[0]);
    const permSet = new Set((row?.effective_permissions ?? []).map((x) => x.trim().toLowerCase()));
    const canRead = permSet.has("read");
    const canWrite = permSet.has("write");
    const canVisible = permSet.has("visible");
    const nextLevel = canWrite ? 3 : canRead ? 2 : canVisible ? 1 : 0;
    setPermCursor(nextLevel);
    setSingleTargetPath(relative);
    setSingleHasOverride(!!row?.share_override);
    setTargetRelativePaths([relative]);
    setTargetPaths([relative]);
    setPermModalOpen(true);
  }

  function submitPermissionChange() {
    const level = Math.max(0, Math.min(3, Math.floor(permCursor))) as 0 | 1 | 2 | 3;
    const permissions: string[] = level === 3
      ? ["visible", "read", "write"]
      : level === 2
        ? ["visible", "read"]
        : level === 1
          ? ["visible"]
          : [];
    if (targetRelativePaths.length === 0) return;
    p.onApplyPermissionsToPaths(targetRelativePaths, permissions, p.selectedMountID);
    setPermModalOpen(false);
  }

  function canPreviewOperate(filePath: string): boolean {
    const normalized = filePath.split("/").filter(Boolean);
    const name = normalized[normalized.length - 1] || "";
    const target = p.browseFiles.find((f) => f.name === name);
    if (!target) return false;
    const permSet = new Set((target.effective_permissions ?? []).map((x) => x.trim().toLowerCase()));
    return permSet.has("read");
  }

  function handlePreviewDirectoryClick(dirPath: string) {
    if (dirPath === "..") {
      if (!canGoParent) return;
      p.onDirectoryClick(dirPath);
      return;
    }
    const normalized = dirPath.split("/").filter(Boolean);
    const name = normalized[normalized.length - 1] || dirPath;
    const target = p.browseFiles.find((f) => f.name === name);
    const permSet = new Set((target?.effective_permissions ?? []).map((x) => x.trim().toLowerCase()));
    if (!permSet.has("read")) {
      toast.error(t("shares.previewReadDenied"));
      return;
    }
    p.onDirectoryClick(dirPath);
  }

  return (
    <>
      <div className="rounded-sm border border-white/40 bg-white/60 px-4 py-3 backdrop-blur-sm dark:border-white/10 dark:bg-black/40 flex items-end justify-between gap-3">
        <div className="flex items-end gap-3">
          <Select disableAnimation className="w-[320px]" label={t("shares.mountLabel")} selectedKeys={p.selectedMountID ? [p.selectedMountID] : []} onSelectionChange={(keys) => p.onMountChange(String(Array.from(keys)[0] ?? ""))}>
            {p.manageableMounts.map((m) => <SelectItem key={m.id}>{`${m.name} (${m.id})`}</SelectItem>)}
          </Select>
          <Select disableAnimation className="w-[240px]" label={t("shares.targetRoleLabel")} selectedKeys={p.grantRole ? [p.grantRole] : []} onSelectionChange={(keys) => p.onGrantRoleChange(String(Array.from(keys)[0] ?? ""))}>
            {p.presetRoleList.map((r) => <SelectItem key={r}>{`${p.roleNameMap.get(r) || r} (ID:${r})`}</SelectItem>)}
          </Select>
        </div>
        <Tabs selectedKey={p.mode} onSelectionChange={(k) => p.onModeChange(String(k) as "edit" | "preview")} size="sm">
          <Tab key="edit" title={t("shares.modeEdit")} />
          <Tab key="preview" title={t("shares.modePreview")} />
        </Tabs>
      </div>

      {p.mode === "edit" ? (
        <>
          <div className="mb-0 flex flex-col md:flex-row items-stretch md:items-center gap-4 sticky top-0 z-0 backdrop-blur-sm shadow-sm py-2 px-4 rounded-sm transition-colors bg-white/60 dark:bg-black/40 border border-white/40 dark:border-white/10">
            <div className="flex items-center gap-2 overflow-x-auto hide-scrollbar pb-1 md:pb-0">
              {canGoParent ? (
                <ShadowTooltip content={t("shares.goParent")}><Button aria-label={t("shares.goParent")} radius="sm" color="primary" size="sm" isIconOnly variant="flat" onPress={p.onGoParentDir} className="text-lg min-w-8"><TiArrowBack /></Button></ShadowTooltip>
              ) : null}
              <ShadowTooltip content={p.selectionModeEnabled ? t("shares.exitMultiSelect") : t("shares.enterMultiSelect")}>
                <Button
                  aria-label={p.selectionModeEnabled ? t("shares.exitMultiSelect") : t("shares.enterMultiSelect")}
                  radius="sm"
                  color="primary"
                  size="sm"
                  isIconOnly
                  variant={p.selectionModeEnabled ? "solid" : "flat"}
                  onPress={p.onToggleSelectionMode}
                  className="min-w-8"
                >
                  <FiCheckSquare />
                </Button>
              </ShadowTooltip>
              <Button
                radius="sm"
                color="primary"
                size="sm"
                variant="solid"
                className="text-sm px-2 min-w-fit"
                title={t("shares.changePermissionSelected")}
                onPress={openBulkPermissionModal}
                isDisabled={!p.selectedMountID || selectedCount === 0}
              >
                {t("shares.changePermissionButton")} {selectedCount > 0 ? `(${selectedCount})` : ""}
              </Button>
            </div>
            <div className="flex flex-1 gap-2 overflow-hidden items-stretch md:items-center">
              <div className="flex-1 rounded-sm border border-white/20 bg-white/40 px-2 py-1 shadow-sm backdrop-blur-md dark:bg-black/20">
                {isPathEditing ? (
                  <Input
                    autoFocus
                    radius="sm"
                    type="text"
                    value={jumpPath}
                    onChange={(e) => setJumpPath(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") confirmJumpPath();
                      if (e.key === "Escape") {
                        setIsPathEditing(false);
                        setJumpPath(displayPath);
                      }
                    }}
                    onBlur={() => {
                      setIsPathEditing(false);
                      setJumpPath(displayPath);
                    }}
                    className="w-full mpfm-path-jump-input"
                    classNames={{
                      inputWrapper: "bg-white/40 dark:bg-black/20 backdrop-blur-md px-2",
                      input: "text-sm text-default-700 dark:text-default-200",
                    }}
                  />
                ) : (
                  <div onClick={() => { setIsPathEditing(true); setJumpPath(displayPath); }}>
                    <Breadcrumbs radius="sm" className="overflow-x-auto hide-scrollbar whitespace-nowrap">
                      {displayPath.split("/").map((part, index, parts) => (
                        <BreadcrumbItem
                          key={`${part}-${index}`}
                          isCurrent={false}
                          onPress={() => {
                            const newPath = parts.slice(0, index + 1).join("/");
                            const logicPath = displayToLogicPath(newPath || ".");
                            p.onBrowsePathChange(logicPath || ".");
                          }}
                        >
                          {part || "."}
                        </BreadcrumbItem>
                      ))}
                    </Breadcrumbs>
                  </div>
                )}
              </div>
              <Button
                radius="sm"
                color="primary"
                size="sm"
                variant="flat"
                onPress={() => {
                  if (isPathEditing) confirmJumpPath();
                  else {
                    setIsPathEditing(true);
                    setJumpPath(displayPath);
                  }
                }}
                startContent={<FiCheck />}
                className="shrink-0"
              >
                {t("shares.confirmButton")}
              </Button>
            </div>
          </div>
      <FileTable
        files={p.browseFiles}
        currentPath={p.browsePath}
        wrapperClassName="h-[calc(100vh-400px)]"
        loading={p.loading}
        sortDescriptor={p.sortDescriptor}
        onSortChange={p.onSortChange}
        selectedFiles={p.selectedFiles}
        onSelectionChange={p.onSelectionChange}
        selectionMode={p.selectionModeEnabled}
        onDirectoryClick={p.onDirectoryClick}
        onEdit={() => {}}
        onPreview={() => {}}
        onRenameRequest={() => {}}
        onMoveRequest={() => {}}
        onCopyPath={() => {}}
        onDelete={() => {}}
        onDownload={() => {}}
        canOperatePath={() => true}
        currentIsRoot={p.currentIsRoot}
        resolveDownloadUrl={() => null}
        showDefaultActions={false}
        extraAction={{
          icon: <FiShield />,
          title: t("shares.pathPermissionConfig"),
          onPress: (filePath) => openSinglePermissionModal(filePath),
          disabled: () => false,
        }}
      />
        </>
      ) : (
        <>
          <div className="mb-0 flex flex-col md:flex-row items-stretch md:items-center gap-4 sticky top-14 z-10 backdrop-blur-sm shadow-sm py-2 px-4 rounded-sm transition-colors bg-white/60 dark:bg-black/40 border border-white/40 dark:border-white/10">
            <div className="flex items-center gap-2 overflow-x-auto hide-scrollbar pb-1 md:pb-0">
              {canGoParent ? (
                <ShadowTooltip content={t("shares.goParent")}><Button aria-label={t("shares.goParent")} radius="sm" color="primary" size="sm" isIconOnly variant="flat" onPress={p.onGoParentDir} className="text-lg min-w-8"><TiArrowBack /></Button></ShadowTooltip>
              ) : null}
              <ShadowTooltip content={t("shares.createDisabledInPreview")}><Button aria-label={t("shares.createDisabledInPreview")} radius="sm" color="primary" size="sm" isIconOnly variant="flat" isDisabled className="text-lg min-w-8"><FiPlus /></Button></ShadowTooltip>
              <ShadowTooltip content={t("shares.refreshPreviewButton")}><Button aria-label={t("shares.refreshPreviewButton")} radius="sm" color="primary" isLoading={p.loading} size="sm" isIconOnly variant="flat" onPress={p.onRefreshPreview} className="text-lg min-w-8"><MdRefresh /></Button></ShadowTooltip>
              <ShadowTooltip content={t("shares.uploadDisabledInPreview")}><Button aria-label={t("shares.uploadDisabledInPreview")} radius="sm" color="primary" size="sm" isIconOnly variant="flat" isDisabled className="text-lg min-w-8"><FiUpload /></Button></ShadowTooltip>
              <ShadowTooltip content={t("shares.multiselectDisabledInPreview")}><Button aria-label={t("shares.multiselectDisabledInPreview")} radius="sm" color="primary" size="sm" isIconOnly variant="flat" isDisabled className="text-lg min-w-8">
                <FiCheckSquare />
              </Button></ShadowTooltip>
              <ShadowTooltip content={t("shares.clearSelectionDisabledInPreview")}><Button aria-label={t("shares.clearSelectionDisabledInPreview")} radius="sm" color="primary" size="sm" isIconOnly variant="flat" isDisabled className="text-lg min-w-8">
                <FiX />
              </Button></ShadowTooltip>
            </div>
            <div className="flex flex-1 gap-2 overflow-hidden items-stretch md:items-center">
              <div className="flex-1 rounded-sm border border-white/20 bg-white/40 px-2 py-1 shadow-sm backdrop-blur-md dark:bg-black/20">
                {isPathEditing ? (
                  <Input
                    autoFocus
                    radius="sm"
                    type="text"
                    value={jumpPath}
                    onChange={(e) => setJumpPath(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") confirmJumpPath();
                      if (e.key === "Escape") {
                        setIsPathEditing(false);
                        setJumpPath(displayPath);
                      }
                    }}
                    onBlur={() => {
                      setIsPathEditing(false);
                      setJumpPath(displayPath);
                    }}
                    className="w-full mpfm-path-jump-input"
                    classNames={{
                      inputWrapper: "bg-white/40 dark:bg-black/20 backdrop-blur-md px-2",
                      input: "text-sm text-default-700 dark:text-default-200",
                    }}
                  />
                ) : (
                  <div onClick={() => { setIsPathEditing(true); setJumpPath(displayPath); }}>
                    <Breadcrumbs radius="sm" className="overflow-x-auto hide-scrollbar whitespace-nowrap">
                      {displayPath.split("/").map((part, index, parts) => (
                        <BreadcrumbItem
                          key={`${part}-${index}`}
                          isCurrent={false}
                          onPress={() => {
                            const newPath = parts.slice(0, index + 1).join("/");
                            const logicPath = displayToLogicPath(newPath || ".");
                            p.onBrowsePathChange(logicPath || ".");
                          }}
                        >
                          {part || "."}
                        </BreadcrumbItem>
                      ))}
                    </Breadcrumbs>
                  </div>
                )}
              </div>
              <Button
                radius="sm"
                color="primary"
                size="sm"
                variant="flat"
                onPress={() => {
                  if (isPathEditing) confirmJumpPath();
                  else {
                    setIsPathEditing(true);
                    setJumpPath(displayPath);
                  }
                }}
                startContent={<FiCheck />}
                className="shrink-0"
              >
                {t("shares.confirmButton")}
              </Button>
            </div>
          </div>
          <FileTable
            files={p.browseFiles}
            currentPath={p.browsePath}
            wrapperClassName="h-[calc(100vh-400px)]"
            loading={p.loading}
            sortDescriptor={p.previewSortDescriptor}
            onSortChange={p.onPreviewSortChange}
            selectedFiles={new Set()}
            onSelectionChange={() => {}}
            selectionMode={false}
            onDirectoryClick={handlePreviewDirectoryClick}
            onEdit={() => {}}
            onPreview={() => {}}
            onRenameRequest={() => {}}
            onMoveRequest={() => {}}
            onCopyPath={() => {}}
            onDelete={() => {}}
            onDownload={() => {}}
            canOperatePath={canPreviewOperate}
            currentIsRoot={p.currentIsRoot}
            resolveDownloadUrl={() => null}
            showDefaultActions={false}
            extraAction={{
              icon: <FiShield />,
              title: t("shares.previewOnly"),
              onPress: () => {},
              disabled: () => true,
            }}
          />
        </>
      )}
      <FormModal
        isOpen={permModalOpen}
        onClose={() => setPermModalOpen(false)}
        title={targetPaths.length > 1 ? t("shares.changePermissionN", { count: targetPaths.length }) : t("shares.changePermissionButton")}
        submitText={t("shares.applyButton")}
        onSubmit={submitPermissionChange}
      >
        <div className="text-xs text-default-500 break-all">
          {targetPaths.length > 1 ? targetPaths.join(", ") : singleTargetPath}
        </div>
        {targetPaths.length === 1 && (
          <div className="flex items-center justify-between rounded-sm border border-default-200 px-3 py-2 text-xs">
            <span>{singleHasOverride ? t("shares.overrideExists") : t("shares.overrideDefault")}</span>
            <Button
              size="sm"
              color="danger"
              variant="flat"
              isDisabled={!singleHasOverride}
              onPress={() => {
                p.onClearSpecialPermissions(toProtocolRelativePath(singleTargetPath));
                setPermModalOpen(false);
              }}
            >
              {t("shares.clearOverrideButton")}
            </Button>
          </div>
        )}
        <PermissionLevelSelector level={Math.max(0, Math.min(3, Math.floor(permCursor))) as 0 | 1 | 2 | 3} onChange={(level) => setPermCursor(level)} />
      </FormModal>
    </>
  );
}
