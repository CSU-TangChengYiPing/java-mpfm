export type PlatformRole = "ROOT" | "ADMIN" | "USER";

export type ShareAction =
  | "view_mount"
  | "create_link"
  | "revoke_link"
  | "manage_role_template"
  | "manage_path_policy";

/** 平台角色归一化：优先信任 isRoot，再兼容 role 字符串，避免前后端字段不一致导致越权或误拒绝。 */
export function resolvePlatformRole(role: string | undefined, isRoot: boolean | undefined): PlatformRole {
  if (isRoot) return "ROOT";
  const normalized = (role || "user").trim().toLowerCase();
  if (normalized === "root") return "ROOT";
  if (normalized === "admin") return "ADMIN";
  return "USER";
}

/** 共享操作权限矩阵：ROOT 全放行，ADMIN/USER 需满足挂载所有者或可管理权限。 */
export function canDoShareAction(action: ShareAction, platformRole: PlatformRole, isMountOwner: boolean, canManageMount: boolean): boolean {
  if (platformRole === "ROOT") return true;
  if (platformRole === "ADMIN") return canManageMount || isMountOwner;
  if (platformRole === "USER") return canManageMount || isMountOwner;
  return false;
}
