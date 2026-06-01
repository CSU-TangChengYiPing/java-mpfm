import { Button } from "@heroui/button";
import { type SortDescriptor } from "@heroui/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import type { FileInfo } from "../../controllers/file_manager";
import MountsController, {
  type MountInfo,
  type ShareInfo,
  type ShareRoleTemplate,
  type ShareMyRoleInfo,
  type ShareTemplatePrivilegeInfo,
} from "../../controllers/mounts";
import { useAuth } from "../../hooks/useAuth";
import { siteConfig } from "../../config/site";
import SharedUsersPanel from "./shares/SharedUsersPanel";
import RoleTemplatesPanel from "./shares/RoleTemplatesPanel";
import RolePermissionsPanel from "./shares/RolePermissionsPanel";
import { canDoShareAction, resolvePlatformRole } from "./shares/guardMatrix";
import { resolveShareError } from "./shares/errorPresentation";

type Selection = Set<string | number> | "all";
type SubPage = "roles" | "role-perm" | "shares";
type SharesTransientSelection = {
  selectedMountID: string;
  grantRole: string;
};

const SHARES_SELECTION_SESSION_KEY = "shares.selection.v1";

const sharesTransientSelectionMemory: SharesTransientSelection = {
  selectedMountID: "",
  grantRole: "",
};

function readSharesSelectionFromSession(): SharesTransientSelection {
  if (typeof window === "undefined") return { selectedMountID: "", grantRole: "" };
  try {
    const raw = window.sessionStorage.getItem(SHARES_SELECTION_SESSION_KEY);
    if (!raw) return { selectedMountID: "", grantRole: "" };
    const parsed = JSON.parse(raw) as Partial<SharesTransientSelection>;
    return {
      selectedMountID: typeof parsed.selectedMountID === "string" ? parsed.selectedMountID : "",
      grantRole: typeof parsed.grantRole === "string" ? parsed.grantRole : "",
    };
  } catch {
    return { selectedMountID: "", grantRole: "" };
  }
}

function writeSharesSelectionToSession(selection: SharesTransientSelection) {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.setItem(SHARES_SELECTION_SESSION_KEY, JSON.stringify(selection));
  } catch {
    // ignore write failure
  }
}

const subPages: SubPage[] = ["roles", "role-perm", "shares"];

function resolveConfiguredSubPages(): SubPage[] {
  const sharesNav = siteConfig.navItems.find((item) => item.label === "Shares Management");
  const configured = (sharesNav?.items ?? []).map((item) => {
    if (item.href?.endsWith("/shares/roles")) return "roles";
    if (item.href?.endsWith("/shares/role-permissions")) return "role-perm";
    if (item.href?.endsWith("/shares/shared-users")) return "shares";
    return undefined;
  }).filter((item): item is SubPage => !!item);
  return configured.length > 0 ? configured : subPages;
}

export function pickInitialMountId(
  mounts: MountInfo[],
  requestedMountId: string,
  username: string,
  fallbackMyRoleMountId: string
): string {
  const manageable = mounts.filter((m) => !!m.shared_enabled && (m.owner_user ?? "").trim() === username);
  const requested = manageable.find((m) => m.id === requestedMountId);
  if (requested) return requested.id;
  if (manageable.length > 0) return manageable[0].id;
  return fallbackMyRoleMountId;
}

export function buildRoleNameMap(roleTemplates: ShareRoleTemplate[], myRoles: ShareMyRoleInfo[]): Map<string, string> {
  const map = new Map<string, string>();
  for (const role of roleTemplates) {
    const roleName = (role.name || role.id || "").trim();
    const templateKey = (role.templateId || "").trim();
    const roleKey = (role.roleId || role.id || "").trim();
    if (templateKey && roleName) map.set(templateKey, roleName);
    if (roleKey && roleName) map.set(roleKey, roleName);
  }
  for (const item of myRoles) {
    const key = (item.roleId || "").trim();
    const value = (item.roleName || "").trim();
    if (!key || !value || map.has(key)) continue;
    map.set(key, value);
  }
  return map;
}

function normalizeAbsPath(p: string): string {
  const x = (p || "/").trim().replace(/\\/g, "/");
  const y = x.startsWith("/") ? x : `/${x}`;
  return y.replace(/\/+/g, "/");
}

function stripDotPrefix(pathValue: string): string {
  const v = (pathValue || "").trim();
  if (!v) return "/";
  if (v.startsWith("./")) return `/${v.slice(2)}`.replace(/\/+/g, "/");
  return v.startsWith("/") ? v.replace(/\/+/g, "/") : `/${v}`.replace(/\/+/g, "/");
}

function normalizeMountRelativePath(pathValue: string): string {
  const raw = (pathValue || "/").trim().replace(/\\/g, "/");
  if (!raw || raw === ".") return "/";
  const partsRaw = raw.replace(/\/+/g, "/").split("/");
  const stack: string[] = [];
  for (const part of partsRaw) {
    if (!part || part === ".") continue;
    if (part === "..") {
      if (stack.length > 0) stack.pop();
      continue;
    }
    stack.push(part);
  }
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
  if (stack.length === 0) return "/";
  return `/${stack.join("/")}`.replace(/\/+/g, "/");
}

function normalizeVirtualBrowsePath(raw: string, current: string): string {
  const cleaned = (raw || "").trim().replace(/\\/g, "/");
  if (!cleaned || cleaned === ".") return current || ".";
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
  let normalized = `/${stack.join("/")}`;
  const personalIdx = normalized.lastIndexOf("/personal/");
  const sharedIdx = normalized.lastIndexOf("/shared/");
  const anchor = Math.max(personalIdx, sharedIdx);
  if (anchor > 0) normalized = normalized.slice(anchor);
  return normalized || ".";
}

function normalizeRolePermLogicalPath(raw: string, mountId: string, current?: string): string {
  const root = mountId ? `/personal/${mountId}` : "";
  if (!root) return ".";
  const fallback = current && current !== "." ? normalizeAbsPath(current) : root;
  let input = (raw || "").trim().replace(/\\/g, "/");
  if (!input || input === ".") return fallback;

  if (!input.startsWith("/")) {
    input = `${fallback.replace(/\/+$/, "")}/${input}`;
  }
  input = input.replace(/\/+/g, "/");
  while (input.includes("/./")) input = input.replace("/./", "/");

  const parts = input.split("/");
  const stack: string[] = [];
  for (const part of parts) {
    if (!part || part === ".") continue;
    if (part === "..") {
      if (stack.length > 0) stack.pop();
      continue;
    }
    stack.push(part);
  }
  const normalized = `/${stack.join("/")}` || "/";
  const rootParts = root.split("/").filter(Boolean);
  const normalizedParts = normalized.split("/").filter(Boolean);
  const isRootedOnMount =
    normalizedParts.length >= 2 &&
    normalizedParts[0] === rootParts[0] &&
    normalizedParts[1] === rootParts[1];
  if (isRootedOnMount) return normalized;

  const personalIdx = normalizedParts.findIndex((s) => s === "personal");
  if (personalIdx >= 0 && normalizedParts.length > personalIdx + 2) {
    const suffix = normalizedParts.slice(personalIdx + 2).join("/");
    return `${root}/${suffix}`.replace(/\/+/g, "/");
  }
  return root;
}

/** 共享管理主页面：编排角色、链接、路径授权与审计子流程。 */
export default function SharesPage({ forcedSubPage }: { forcedSubPage?: SubPage }) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const sessionSelection = readSharesSelectionFromSession();
  const activeSubPages = useMemo(resolveConfiguredSubPages, []);
  const [subPage, setSubPage] = useState<SubPage>(() => forcedSubPage ?? activeSubPages[0] ?? "shares");

  const [mounts, setMounts] = useState<MountInfo[]>([]);
  const [selectedMountID, setSelectedMountID] = useState(
    sharesTransientSelectionMemory.selectedMountID || sessionSelection.selectedMountID
  );

  const [loading, setLoading] = useState(false);
  const [shares, setShares] = useState<ShareInfo[]>([]);
  const [roleTemplates, setRoleTemplates] = useState<ShareRoleTemplate[]>([]);
  const [templatePrivileges, setTemplatePrivileges] = useState<ShareTemplatePrivilegeInfo[]>([]);

  const [shareRole, setShareRole] = useState("visitor");
  const [expiresAt, setExpiresAt] = useState("");
  const [roleExpiresAt, setRoleExpiresAt] = useState("");
  const [maxUses, setMaxUses] = useState("100");
  const [resolveToken, setResolveToken] = useState("");
  const [myRoles, setMyRoles] = useState<ShareMyRoleInfo[]>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [grantRole, setGrantRole] = useState(sharesTransientSelectionMemory.grantRole || sessionSelection.grantRole);
  const [rolePermMode, setRolePermMode] = useState<"edit" | "preview">("edit");
  const [selectionModeEnabled, setSelectionModeEnabled] = useState(false);
  const [formError, setFormError] = useState("");
  const [fieldError, setFieldError] = useState("");


  const [browsePath, setBrowsePath] = useState(".");
  const [browseFiles, setBrowseFiles] = useState<FileInfo[]>([]);
  const [selectedFiles, setSelectedFiles] = useState<Selection>(new Set());
  const [sortDescriptor, setSortDescriptor] = useState<SortDescriptor>({ column: "name", direction: "ascending" });

  const [previewSortDescriptor, setPreviewSortDescriptor] = useState<SortDescriptor>({ column: "name", direction: "ascending" });

  function sortBrowseFiles(input: FileInfo[]): FileInfo[] {
    return [...input].sort((a, b) => {
      if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1;
      return a.name.localeCompare(b.name);
    });
  }

  function logicalRootPrefix(): string {
    return selectedMountID ? `/personal/${selectedMountID}` : "";
  }

  function toTemplatePath(logicalPath: string): string {
    const normalized = normalizeAbsPath(logicalPath);
    const root = logicalRootPrefix();
    if (!root) return normalized;
    if (normalized === root) return "/";
    if (normalized.startsWith(`${root}/`)) {
      const suffix = normalized.slice(root.length);
      return suffix || "/";
    }
    return normalized;
  }

  function toLogicalPathFromTemplate(templatePath: string): string {
    const normalizedTemplate = stripDotPrefix(templatePath);
    const root = logicalRootPrefix();
    if (!root) return normalizedTemplate;
    if (normalizedTemplate === "/") return root;
    return `${root}${normalizedTemplate}`.replace(/\/+/g, "/");
  }

  const platformRole = useMemo(() => resolvePlatformRole(user?.role, user?.is_root), [user?.is_root, user?.role]);
  const manageableMounts = useMemo(
    () => mounts.filter((m) => !!m.shared_enabled && (m.owner_user ?? "").trim() === (user?.username ?? "").trim()),
    [mounts, user?.username]
  );

  const presetRoleList = useMemo(() => {
    return roleTemplates
      .filter((r) => (r.name || "").trim().toLowerCase() !== "owner" && (r.name || "").trim() !== "所有者")
      .map((r) => (r.templateId || "").trim())
      .filter((id) => !!id);
  }, [roleTemplates]);
  const grantedRoleOptions = useMemo(
    () => roleTemplates.map((r) => ({ roleId: (r.roleId || r.id || "").trim(), name: (r.name || r.id || "").trim() })).filter((r) => !!r.roleId),
    [roleTemplates]
  );
  const roleNameMap = useMemo(() => buildRoleNameMap(roleTemplates, myRoles), [myRoles, roleTemplates]);

  function canOperateSelectedMount(action: "create_link" | "revoke_link" | "manage_role_template" | "manage_path_policy"): boolean {
    const mount = mounts.find((m) => m.id === selectedMountID);
    if (!mount) return false;
    return canDoShareAction(action, platformRole, mount.owner_user === user?.user_id, !!mount.can_manage);
  }

  useEffect(() => {
    if (!forcedSubPage) return;
    setSubPage(forcedSubPage);
  }, [forcedSubPage]);

  /** 共享域错误统一分流：字段级就近提示，表单级持久提示，其余回退 toast。 */
  function presentShareError(error: unknown, fallbackMessage: string) {
    const resolved = resolveShareError(error, fallbackMessage);
    if (resolved.level === "field") {
      setFieldError(resolved.message);
      return;
    }
    if (resolved.level === "form") {
      setFormError(resolved.message);
      return;
    }
    toast.error(resolved.message);
  }

  /** 加载流程统一错误提示：读取类失败默认走 toast，避免污染表单输入态。 */
  function presentShareLoadError(error: unknown, fallbackMessage: string) {
    const resolved = resolveShareError(error, fallbackMessage);
    toast.error(resolved.message);
  }

  const loadMounts = useCallback(async () => {
    try {
      const list = await MountsController.list();
      setMounts(list);
      if (!selectedMountID) {
        const nextMountId = pickInitialMountId(
          list,
          "",
          (user?.username ?? "").trim(),
          ""
        );
        if (nextMountId) {
          setSelectedMountID(nextMountId);
          return;
        }
        const summary = await MountsController.listMyRoleSummariesV5();
        if (summary.length > 0) {
          setSelectedMountID(summary[0].mountId);
        }
      }
    } catch (err) {
      presentShareLoadError(err, t("mounts.fetchFailed"));
    }
  }, [selectedMountID, t, user?.username]);

  const loadShareData = useCallback(async (mountID: string) => {
    if (!mountID) return;
    setLoading(true);
    try {
      const mount = mounts.find((m) => m.id === mountID);
      const canManage = !!mount && (mount.owner_user ?? "").trim() === (user?.username ?? "").trim();
      if (!canManage) {
        setShares([]);
        setRoleTemplates([]);
        setTemplatePrivileges([]);
        setMyRoles(await MountsController.listMyRolesV5(mountID));
        return;
      }
      const [shareList, roleList, myRoleList] = await Promise.all([
        MountsController.listLinksByMount(mountID),
        MountsController.listRoleTemplatesV5(mountID),
        MountsController.listGrantedRolesV5(mountID),
      ]);
      setShares(shareList);
      setRoleTemplates(roleList);
      setMyRoles(myRoleList);
      const nonOwnerTemplate = roleList.find((r) => (r.name || "").trim().toLowerCase() !== "owner");
      const nextTemplateId = nonOwnerTemplate?.templateId || "";
      setGrantRole((prev) => (prev && roleList.some((r) => r.templateId === prev) ? prev : nextTemplateId));
      if (nextTemplateId) {
        const privileges = await MountsController.listRoleTemplatePrivilegesV5(nextTemplateId);
        setTemplatePrivileges(privileges);
      } else {
        setTemplatePrivileges([]);
      }
    } catch (err) {
      presentShareLoadError(err, t("shares.loadFailed"));
      setShares([]);
      setRoleTemplates([]);
      setTemplatePrivileges([]);
      setMyRoles([]);
    } finally {
      setLoading(false);
    }
  }, [mounts, platformRole, t, user?.username]);

  const loadBrowseFiles = useCallback(async () => {
    if (!selectedMountID || !grantRole) return;
    try {
      const list = await MountsController.listTemplateFilesV5(selectedMountID, grantRole, browsePath);
      const privilegeLogicalPathSet = new Set(
        templatePrivileges.map((p) => toLogicalPathFromTemplate(stripDotPrefix(p.targetPath)))
      );
      const merged = list.map((f) => {
        const abs = normalizeAbsPath(browsePath === "." ? `/${f.name}` : `/${browsePath}/${f.name}`);
        const effective: string[] = [];
        if (f.visible) effective.push("visible");
        if (f.readable) effective.push("read");
        if (f.writable) effective.push("write");
        return {
          ...f,
          effective_permissions: effective,
          share_override: privilegeLogicalPathSet.has(abs),
          share_override_id: privilegeLogicalPathSet.has(abs) ? abs : undefined,
          permission_source: (privilegeLogicalPathSet.has(abs) ? "override" : "default") as "override" | "default",
        };
      });
      if (rolePermMode === "preview") {
        setBrowseFiles(sortBrowseFiles(merged.filter((f) => {
          return (f.effective_permissions ?? []).includes("visible");
        })));
      } else {
        setBrowseFiles(sortBrowseFiles(merged));
      }
    } catch {
      setBrowseFiles([]);
    }
  }, [browsePath, grantRole, rolePermMode, selectedMountID, templatePrivileges]);

  useEffect(() => {
    const id = window.setTimeout(() => {
      void loadMounts();
    }, 0);
    return () => window.clearTimeout(id);
  }, [loadMounts]);

  useEffect(() => {
    const id = window.setTimeout(() => {
      if (selectedMountID) {
        void loadShareData(selectedMountID);
      }
    }, 0);
    return () => window.clearTimeout(id);
  }, [loadShareData, selectedMountID]);

  useEffect(() => {
    if (!selectedMountID) {
      setBrowsePath(".");
      return;
    }
    setBrowsePath(`/personal/${selectedMountID}`);
  }, [selectedMountID]);

  useEffect(() => {
    const id = window.setTimeout(() => {
      if (subPage === "role-perm") {
        void loadBrowseFiles();
      }
    }, 0);
    return () => window.clearTimeout(id);
  }, [loadBrowseFiles, rolePermMode, subPage]);

  useEffect(() => {
    if (grantRole === "undefined" || grantRole === "null") {
      setGrantRole("");
      return;
    }
    if (!grantRole) {
      setTemplatePrivileges([]);
      return;
    }
    void (async () => {
      try {
        const list = await MountsController.listRoleTemplatePrivilegesV5(grantRole);
        setTemplatePrivileges(list);
      } catch {
        setTemplatePrivileges([]);
      }
    })();
  }, [grantRole]);

  useEffect(() => {
    sharesTransientSelectionMemory.selectedMountID = selectedMountID;
    sharesTransientSelectionMemory.grantRole = grantRole;
    writeSharesSelectionToSession({
      selectedMountID,
      grantRole,
    });
  }, [grantRole, selectedMountID]);

  function updateSubPage(next: SubPage) {
    setSubPage(next);
  }

  function moveSubPage(direction: "prev" | "next") {
    const index = activeSubPages.indexOf(subPage);
    if (index < 0) return;
    const nextIndex = direction === "prev" ? index - 1 : index + 1;
    if (nextIndex < 0 || nextIndex >= activeSubPages.length) return;
    updateSubPage(activeSubPages[nextIndex]);
  }

  function absPath(name: string): string {
    if (name.startsWith("/")) return name.replace(/\/+/g, "/");
    if (browsePath === "." || browsePath === "/") return `/${name}`;
    return `/${browsePath.replace(/^\.?\/*/, "")}/${name}`.replace(/\/+/g, "/");
  }

  function onDirectoryClick(dirPath: string) {
    const root = logicalRootPrefix();
    if (!root) {
      setBrowsePath(".");
      return;
    }
    if (dirPath === "..") {
      const current = normalizeRolePermLogicalPath(browsePath, selectedMountID, root);
      if (current === root) {
        setBrowsePath(root);
        return;
      }
      const parts = current.split("/").filter(Boolean);
      parts.pop();
      const parent = parts.length ? `/${parts.join("/")}` : root;
      setBrowsePath(parent.startsWith(root) ? parent : root);
      return;
    }
    const normalizedInput = (dirPath || "").trim().replace(/\\/g, "/");
    const current = normalizeRolePermLogicalPath(browsePath, selectedMountID, root);
    const childName = normalizedInput.split("/").filter(Boolean).slice(-1)[0] || "";
    if (!childName || childName === ".") {
      setBrowsePath(current);
      return;
    }
    const merged = `${current.replace(/\/+$/, "")}/${childName}`;
    const next = normalizeRolePermLogicalPath(merged, selectedMountID, current);
    setBrowsePath(next || root);
  }

  /** 权限前置守卫：无权限时统一写入表单级拒绝提示并阻断后续动作。 */
  function denyIfNoPermission(action: "create_link" | "revoke_link" | "manage_role_template" | "manage_path_policy"): boolean {
    if (canOperateSelectedMount(action)) return false;
    setFormError(t("shares.guardDenied"));
    return true;
  }

  /** 表单参数前置守卫：条件不满足时统一 toast 并阻断动作。 */
  function denyIfInvalid(condition: boolean, message: string): boolean {
    if (!condition) return false;
    toast.error(message);
    return true;
  }

  async function createShare() {
    if (!selectedMountID) return;
    if (denyIfNoPermission("create_link")) return;
    try {
      setFormError("");
      setFieldError("");
      await MountsController.createLinkV5({
        mountId: selectedMountID,
        roleId: shareRole,
        expiresAt: expiresAt.trim() || undefined,
        roleExpireAt: roleExpiresAt.trim() || undefined,
        maxUses: Number.isFinite(Number(maxUses)) ? Number(maxUses) : undefined,
      });
      toast.success(t("shares.createLinkSuccess"));
      setExpiresAt("");
      setRoleExpiresAt("");
      await loadShareData(selectedMountID);
    } catch (err) {
      presentShareError(err, t("shares.createLinkFailed"));
    }
  }

  async function applyLink() {
    if (!resolveToken.trim()) return;
    try {
      setFormError("");
      setFieldError("");
      const resolved = await MountsController.resolveLinkV5(resolveToken.trim());
      setResolveToken("");
      setSelectedMountID(resolved.mountId);
      toast.success(t("shares.applyLinkSuccess"));
      await loadShareData(resolved.mountId);
      setCreateOpen(false);
    } catch (err) {
      presentShareError(err, t("shares.applyLinkFailed"));
    }
  }

  async function revokeShare(shareID: string) {
    if (!selectedMountID) return;
    if (denyIfNoPermission("revoke_link")) return;
    try {
      setFormError("");
      await MountsController.revokeLinkV5(shareID);
      toast.success(t("shares.revokeLinkSuccess"));
      await loadShareData(selectedMountID);
    } catch (err) {
      presentShareError(err, t("shares.revokeLinkFailed"));
    }
  }

  async function deleteShare(shareID: string) {
    if (!selectedMountID) return;
    if (denyIfNoPermission("revoke_link")) return;
    try {
      setFormError("");
      await MountsController.deleteLinkV5(shareID);
      toast.success(t("common.deleteSuccess"));
      await loadShareData(selectedMountID);
    } catch (err) {
      presentShareError(err, t("common.deleteFailed"));
    }
  }

  function isRoleNameDuplicated(name: string, excludeTemplateId?: string): boolean {
    const normalized = name.trim().toLowerCase();
    if (!normalized) return false;
    return roleTemplates.some((item) => {
      const currentTemplateId = (item.templateId || item.id || "").trim();
      if (excludeTemplateId && currentTemplateId === excludeTemplateId.trim()) return false;
      return (item.name || "").trim().toLowerCase() === normalized;
    });
  }

  async function updateRoleTemplate(roleID: string, roleName: string, permissions: string[]) {
    if (!selectedMountID) return;
    if (denyIfNoPermission("manage_role_template")) return;
    if (denyIfInvalid(permissions.length === 0, t("shares.permissionRequired"))) return;
    try {
      setFormError("");
      setFieldError("");
      const target = roleTemplates.find((item) => item.id === roleID || item.roleId === roleID);
      const templateId = (target?.templateId || "").trim();
      if (!templateId) {
        toast.error(t("shares.roleUpdateFailed"));
        return;
      }
      const nextName = roleName.trim();
      if (denyIfInvalid(!nextName, t("shares.roleNameRequired"))) return;
      if (denyIfInvalid(isRoleNameDuplicated(nextName, templateId), t("shares.roleNameDuplicated"))) return;
      await MountsController.updateRoleTemplateV5({
        templateId,
        name: nextName,
        defaultVisible: permissions.includes("visible"),
        defaultRead: permissions.includes("read"),
        defaultWrite: permissions.includes("write"),
      });
      toast.success(t("shares.roleUpdated"));
      await loadShareData(selectedMountID);
    } catch (err) {
      presentShareError(err, t("shares.roleUpdateFailed"));
    }
  }

  async function createRoleTemplate(roleName: string, permissions: string[]) {
    if (!selectedMountID) return;
    if (denyIfNoPermission("manage_role_template")) return;
    const finalName = roleName.trim();
    if (denyIfInvalid(!finalName, t("shares.roleNameRequired"))) return;
    if (denyIfInvalid(isRoleNameDuplicated(finalName), t("shares.roleNameDuplicated"))) return;
    if (denyIfInvalid(permissions.length === 0, t("shares.permissionRequired"))) return;
    try {
      setFormError("");
      setFieldError("");
      await MountsController.createRoleTemplateV5({
        mountId: selectedMountID,
        name: finalName,
        defaultVisible: permissions.includes("visible"),
        defaultRead: permissions.includes("read"),
        defaultWrite: permissions.includes("write"),
      });
      toast.success(t("shares.roleCreated"));
      await loadShareData(selectedMountID);
    } catch (err) {
      presentShareError(err, t("shares.roleCreateFailed"));
    }
  }

  async function deleteRoleTemplate(roleID: string) {
    if (!selectedMountID) return;
    if (denyIfNoPermission("manage_role_template")) return;
    try {
      await MountsController.deleteRoleTemplateV5(roleID);
      toast.success(t("shares.roleDeleted"));
      await loadShareData(selectedMountID);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("shares.roleDeleteFailed"));
    }
  }

  async function applyPermissionsToPaths(relativePaths: string[], permissions: string[], mountId: string) {
    if (!selectedMountID || !grantRole) return;
    if (denyIfNoPermission("manage_path_policy")) return;
    const normalized = Array.from(new Set(relativePaths.map((p) => normalizeMountRelativePath(p)).filter(Boolean)));
    if (denyIfInvalid(normalized.length === 0, t("shares.pathRequired"))) return;
    try {
      setFormError("");
      setFieldError("");
      const root = mountId ? `/personal/${mountId}` : `/personal/${selectedMountID}`;
      const targetPaths = normalized.map((path) => {
        const logicalPath = `${root}${path === "/" ? "" : path}`.replace(/\/+/g, "/");
        return toTemplatePath(logicalPath);
      });
      await MountsController.upsertRoleTemplatePrivilegesBatchV5(grantRole, {
        targetPaths,
        allowVisible: permissions.includes("visible"),
        allowRead: permissions.includes("read"),
        allowWrite: permissions.includes("write"),
      });
      const refreshed = await MountsController.listRoleTemplatePrivilegesV5(grantRole);
      setTemplatePrivileges(refreshed);
      toast.success(t("shares.pathPermissionUpdated"));
      setSelectedFiles(new Set());
      await loadBrowseFiles();
    } catch (err) {
      presentShareError(err, t("shares.pathPermissionUpdateFailed"));
    }
  }

  async function clearSpecialPermissionsForPath(relativePath: string) {
    if (!selectedMountID || !grantRole) return;
    if (denyIfNoPermission("manage_path_policy")) return;
    const target = normalizeMountRelativePath(relativePath);
    const matched = templatePrivileges.filter((p) => normalizeMountRelativePath(p.targetPath) === target);
    if (matched.length === 0) {
      toast(t("shares.noOverrideRule"));
      return;
    }
    try {
      for (const item of matched) {
        await MountsController.deleteRoleTemplatePrivilegeV5(grantRole, item.privilegeId);
      }
      const refreshed = await MountsController.listRoleTemplatePrivilegesV5(grantRole);
      setTemplatePrivileges(refreshed);
      toast.success(t("shares.clearOverrideSuccess"));
      await loadBrowseFiles();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("shares.clearOverrideFailed"));
    }
  }

  async function updateGrantedRole(item: ShareMyRoleInfo, nextRoleId: string, nextRoleExpireAt?: string) {
    if (!selectedMountID) return;
    if (denyIfNoPermission("manage_role_template")) return;
    if (!item.granteeUserId) return;
    if (nextRoleExpireAt && new Date(nextRoleExpireAt).getTime() <= Date.now()) {
      toast.error(t("shares.expireAtMustBeFuture"));
      return;
    }
    try {
      await MountsController.updateGrantedRoleV5({
        mountId: selectedMountID,
        granteeUserId: item.granteeUserId,
        currentRoleId: item.roleId,
        roleId: nextRoleId,
        roleExpireAt: nextRoleExpireAt || null,
      });
      toast.success(t("shares.grantedRoleUpdated"));
      await loadShareData(selectedMountID);
    } catch (err) {
      presentShareError(err, t("shares.grantedRoleUpdateFailed"));
    }
  }

  async function revokeGrantedRole(item: ShareMyRoleInfo) {
    if (!selectedMountID) return;
    if (denyIfNoPermission("manage_role_template")) return;
    if (!item.granteeUserId) return;
    try {
      await MountsController.revokeGrantedRoleV5({
        mountId: selectedMountID,
        granteeUserId: item.granteeUserId,
        roleId: item.roleId,
      });
      toast.success(t("shares.grantedRoleRevoked"));
      await loadShareData(selectedMountID);
    } catch (err) {
      presentShareError(err, t("shares.grantedRoleRevokeFailed"));
    }
  }

  async function jumpPreviewPathWithFallback(targetPath: string) {
    if (!selectedMountID || !grantRole) return;
    const norm = targetPath === "." ? "/" : normalizeAbsPath(targetPath);
    const segments = norm.split("/").filter(Boolean);
    const candidates: string[] = [];
    for (let i = segments.length; i >= 0; i -= 1) {
      candidates.push(`/${segments.slice(0, i).join("/")}`.replace(/\/+/g, "/") || "/");
    }
    let nearestVisible = ".";
    for (const c of candidates) {
      try {
        const result = await MountsController.effectiveByTemplateBatchV5(selectedMountID, grantRole, [c]);
        const self = result[c];
        const ok = !!self && self.canVisible && self.canRead;
        if (ok) {
          nearestVisible = c === "/" ? "." : c.replace(/^\/+/, "");
          if (normalizeAbsPath(c) === norm) {
            setBrowsePath(nearestVisible);
            return;
          }
          break;
        }
      } catch {
        // continue fallback search
      }
    }
    const confirmFallback = window.confirm(t("shares.previewPathFallbackConfirm", { path: norm, fallback: nearestVisible }));
    if (confirmFallback) setBrowsePath(nearestVisible);
  }

  const shareRows = shares.map((s) => ({ ...s, key: s.id }));
  const roleRows = roleTemplates.map((r) => ({ ...r, key: r.id }));
  const grantRows = templatePrivileges.map((p) => {
    const permissions: string[] = [];
    if (p.allowVisible) permissions.push("visible");
    if (p.allowRead) permissions.push("read");
    if (p.allowWrite) permissions.push("write");
    const relativePath = (() => {
      const normalized = normalizeMountRelativePath(p.targetPath);
      if (normalized === "/") return "/";
      return normalized.replace(/^\/+/, "");
    })();
    return { key: p.privilegeId, role: grantRole, mount_id: selectedMountID, path_scopes: [relativePath], permissions };
  });
  return (
    <div className="h-full w-full min-h-0 overflow-y-auto p-2 md:p-4 flex flex-col gap-4">
      {!!formError && <div className="rounded-sm border border-danger/40 bg-danger-50 px-3 py-2 text-sm text-danger-700">{formError}</div>}
      {!!fieldError && <div className="rounded-sm border border-warning/40 bg-warning-50 px-3 py-2 text-sm text-warning-700">{fieldError}</div>}

      {subPage === "shares" && (
        <SharedUsersPanel
          selectedMountID={selectedMountID}
          manageableMounts={manageableMounts}
          shareRole={shareRole}
          expiresAt={expiresAt}
          roleExpiresAt={roleExpiresAt}
          maxUses={maxUses}
          resolveToken={resolveToken}
          myRoles={myRoles}
          grantedRoleOptions={grantedRoleOptions}
          presetRoleList={presetRoleList}
          roleNameMap={roleNameMap}
          loading={loading}
          rows={shareRows}
          onMountChange={setSelectedMountID}
          onShareRoleChange={setShareRole}
          onExpiresAtChange={setExpiresAt}
          onRoleExpiresAtChange={setRoleExpiresAt}
          onCreateShare={() => void createShare()}
          onRevokeShare={(shareID) => void revokeShare(shareID)}
          onDeleteShare={(shareID) => void deleteShare(shareID)}
          onMaxUsesChange={setMaxUses}
          onResolveTokenChange={setResolveToken}
          onResolveLink={() => void applyLink()}
          onUpdateGrantedRole={(item, roleId, roleExpireAt) => void updateGrantedRole(item, roleId, roleExpireAt)}
          onRevokeGrantedRole={(item) => void revokeGrantedRole(item)}
          createOpen={createOpen}
          onCreateOpenChange={setCreateOpen}
        />
      )}

      {subPage === "roles" && (
        <RoleTemplatesPanel
          selectedMountID={selectedMountID}
          manageableMounts={manageableMounts}
          loading={loading}
          rows={roleRows}
          onMountChange={setSelectedMountID}
          onCreateRoleTemplate={(name, perms) => void createRoleTemplate(name, perms)}
          onUpdateRoleTemplate={(id, name, perms) => void updateRoleTemplate(id, name, perms)}
          onDeleteRoleTemplate={(id) => void deleteRoleTemplate(id)}
        />
      )}

      {subPage === "role-perm" && (
        <RolePermissionsPanel
          selectedMountID={selectedMountID}
          manageableMounts={manageableMounts}
          grantRole={grantRole}
          presetRoleList={presetRoleList}
          roleNameMap={roleNameMap}
          browsePath={browsePath}
          mode={rolePermMode}
          previewSortDescriptor={previewSortDescriptor}
          selectionModeEnabled={selectionModeEnabled}
          browseFiles={browseFiles}
          loading={loading}
          sortDescriptor={sortDescriptor}
          selectedFiles={selectedFiles}
          currentIsRoot={user?.is_root}
          grantRows={grantRows}
          onGrantRoleChange={(v) => setGrantRole(v)}
          onMountChange={setSelectedMountID}
          onModeChange={setRolePermMode}
          onGoParentDir={() => onDirectoryClick("..")}
          onApplyPermissionsToPaths={(paths, permissions, mountId) => void applyPermissionsToPaths(paths, permissions, mountId)}
          onToggleSelectionMode={() => {
            setSelectionModeEnabled((v) => {
              const next = !v;
              if (!next) setSelectedFiles(new Set());
              return next;
            });
          }}
          onSortChange={setSortDescriptor}
          onPreviewSortChange={setPreviewSortDescriptor}
          onSelectionChange={setSelectedFiles}
          onDirectoryClick={onDirectoryClick}
          onBrowsePathChange={setBrowsePath}
          onPreviewJumpPathRequest={(path) => void jumpPreviewPathWithFallback(path)}
          onClearSpecialPermissions={(path) => void clearSpecialPermissionsForPath(path)}
          onRefreshPreview={() => void loadBrowseFiles()}
        />
      )}

      <div className="pt-2 flex items-center justify-between">
        <Button variant="flat" onPress={() => moveSubPage("prev")} isDisabled={activeSubPages.indexOf(subPage) <= 0}>{t("shares.prevStep")}</Button>
        <Button color="primary" variant="flat" onPress={() => moveSubPage("next")} isDisabled={activeSubPages.indexOf(subPage) >= activeSubPages.length - 1}>{t("shares.nextStep")}</Button>
      </div>
    </div>
  );
}


