import type { FileInfo } from "./file_manager";

export type MountInfo = {
  id: string;
  name: string;
  protocol: string;
  root: string;
  shared_enabled?: boolean;
  enabled: boolean;
  endpoint?: string;
  username?: string;
  last_error?: string;
  owner_user?: string;
  effective_permissions?: string[];
  can_manage?: boolean;
  owner_display_name?: string;
};

type MountResponse = {
  id?: string;
  mountId?: string;
  name?: string;
  protocol?: string;
  type?: string;
  root?: string;
  virtualPath?: string;
  enabled?: boolean;
  state?: "enabled" | "disabled";
  shared_enabled?: boolean;
  endpoint?: string;
  username?: string;
  last_error?: string;
  owner_user?: string;
  effective_permissions?: string[];
  can_manage?: boolean;
  owner_display_name?: string;
};

type MountsResponse = { mounts?: MountResponse[] };
type FileEntryV5 = {
  path?: string;
  name?: string;
  type?: string;
  sizeBytes?: number;
  mtime?: string;
  visible?: boolean;
  readable?: boolean;
  writable?: boolean;
  etag?: string;
  version?: string;
};
type FileItemsV5Response = { items?: FileEntryV5[] };

export type CreateMountPayload = {
  name: string;
  protocol: "local" | "sftp" | "webdav";
  enabled: boolean;
  local_root?: string;
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  remote_root?: string;
  shared_enabled?: boolean;
};
export type TestMountConnectionPayload = {
  protocol: "local" | "sftp" | "webdav";
  local_root?: string;
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  remote_root?: string;
};

export type UpdateMountPayload = {
  name?: string;
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  remote_root?: string;
  shared_enabled?: boolean;
};

export type ShareInfo = {
  id: string;
  token?: string;
  mountId: string;
  mount_id?: string;
  roleId: string;
  role?: string;
  created_by: string;
  created_at: string;
  expires_at?: string;
  revoked_at?: string;
  state?: string;
  startAt?: string;
  expireAt?: string;
  maxUses?: number;
  usedCount?: number;
  createdByUserId?: string;
  shareUrl?: string;
};

export type ShareMyRoleInfo = {
  mountId: string;
  roleId: string;
  roleName: string;
  roleState: string;
  roleStartAt?: string;
  roleExpireAt?: string;
  grantedAt?: string;
  granteeUserId?: string;
  granteeUsername?: string;
};

export type ShareMyRoleSummaryInfo = {
  mountId: string;
  mountName: string;
  mountOwner: string;
  roleId: string;
  roleName: string;
  roleState: string;
  roleStartAt?: string;
  roleExpireAt?: string;
  grantedAt?: string;
};

export type ShareRoleTemplate = {
  id: string;
  templateId?: string;
  roleId?: string;
  mountId: string;
  mount_id?: string;
  name: string;
  permissions: string[];
  defaultVisible?: boolean;
  defaultRead?: boolean;
  defaultWrite?: boolean;
  path_scopes?: string[];
  builtin: boolean;
};

export type ShareGrantInfo = {
  id: string;
  grantId?: string;
  mountId: string;
  mount_id?: string;
  role: string;
  path_scopes?: string[];
  pathScopes?: string[];
  permissions?: string[];
  created_by?: string;
  created_at?: string;
  updated_at?: string;
};

export type ShareTemplatePrivilegeInfo = {
  privilegeId: string;
  templateId: string;
  targetPath: string;
  allowVisible: boolean;
  allowRead: boolean;
  allowWrite: boolean;
  version: number;
};

export type ShareTemplateEffectiveInfo = {
  path: string;
  canVisible: boolean;
  canRead: boolean;
  canWrite: boolean;
};

export type SharePreviewNode = {
  path: string;
  is_dir: boolean;
  permissions: string[];
  visible: boolean;
};

async function parseErrorMessage(resp: Response): Promise<string> {
  const text = await resp.text();
  try {
    const json = JSON.parse(text) as { error?: string | { code?: string; message?: string } };
    if (typeof json.error === "string") return json.error;
    if (json.error && typeof json.error === "object") return `${json.error.code ? `[${json.error.code}] ` : ""}${json.error.message ?? text}`;
    return text;
  } catch {
    return text || `HTTP ${resp.status}`;
  }
}

async function request(path: string, init?: RequestInit): Promise<Response> {
  const resp = await fetch(path, init);
  if (!resp.ok) throw new Error(await parseErrorMessage(resp));
  return resp;
}

type ListEnvelope<T> = { items?: T[]; page?: unknown };

function asList<T>(body: unknown): T[] {
  if (Array.isArray(body)) return body as T[];
  if (body && typeof body === "object" && Array.isArray((body as ListEnvelope<T>).items)) {
    return (body as ListEnvelope<T>).items as T[];
  }
  return [];
}

function normalizeShare(item: ShareInfo): ShareInfo {
  const row = item as ShareInfo & { linkId?: string };
  return {
    ...row,
    id: row.id || row.linkId || "",
    mountId: item.mountId || item.mount_id || "",
    roleId: item.roleId || item.role || "",
    expires_at: item.expires_at || row.expireAt,
    created_by: item.created_by || row.createdByUserId || "",
  };
}

function normalizeRole(item: ShareRoleTemplate): ShareRoleTemplate {
  return {
    ...item,
    id: item.id || item.roleId || "",
    mountId: item.mountId || item.mount_id || "",
  };
}

function normalizeGrant(item: ShareGrantInfo): ShareGrantInfo {
  return {
    ...item,
    id: item.id || item.grantId || "",
    mountId: item.mountId || item.mount_id || "",
    path_scopes: item.path_scopes || item.pathScopes || [],
  };
}

function normalizeMount(item: MountResponse): MountInfo {
  return {
    id: item.id ?? item.mountId ?? "",
    name: item.name ?? "",
    protocol: item.protocol ?? item.type ?? "local",
    root: item.root ?? item.virtualPath ?? "",
    enabled: item.enabled ?? item.state === "enabled",
    shared_enabled: item.shared_enabled ?? false,
    endpoint: item.endpoint,
    username: item.username,
    last_error: item.last_error ?? "",
    owner_user: item.owner_user ?? "",
    owner_display_name: item.owner_display_name ?? "",
    effective_permissions: item.effective_permissions ?? [],
    can_manage: item.can_manage ?? false,
  };
}

/** 挂载与共享域控制器：统一处理挂载管理、共享角色/链接及权限预览相关接口编排。 */
export default class MountsController {
  static async list(): Promise<MountInfo[]> {
    const resp = await request("/api/v1/mounts");
    const body = (await resp.json()) as MountsResponse | MountResponse[];
    const rows = Array.isArray(body) ? body : (body.mounts ?? []);
    return rows.map(normalizeMount).filter((item) => item.id);
  }

  static async create(payload: CreateMountPayload): Promise<void> {
    await request("/api/v1/mounts", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
  }
  static async testConnection(payload: TestMountConnectionPayload): Promise<{ protocol: string; health: string; reason?: string }> {
    const resp = await request("/api/v1/mounts/test-connection", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return (await resp.json()) as { protocol: string; health: string; reason?: string };
  }

  static updatePermission(id: string, ownerUser: string, ownerGroup: string, mode: string): Promise<void> {
    void id;
    void ownerUser;
    void ownerGroup;
    void mode;
    return Promise.reject(new Error("UGO mount permission API has been removed"));
  }

  static async action(action: "enable" | "disable", id: string): Promise<void> {
    await request(`/api/v1/mounts/${encodeURIComponent(id)}/${action}`, {
      method: "POST",
    });
  }

  static async update(mountId: string, payload: UpdateMountPayload): Promise<void> {
    await request(`/api/v1/mounts/${encodeURIComponent(mountId)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
  }

  static async deleteMount(mountId: string): Promise<void> {
    await request(`/api/v1/mounts/${encodeURIComponent(mountId)}`, {
      method: "DELETE",
    });
  }

  static async createRole(payload: { mountId: string; name: string; roleExpiresAt?: string | null }): Promise<ShareRoleTemplate> {
    const resp = await request(`/api/v1/mounts/${encodeURIComponent(payload.mountId)}/share-roles`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: payload.name, roleExpiresAt: payload.roleExpiresAt ?? null }),
    });
    return normalizeRole((await resp.json()) as ShareRoleTemplate);
  }

  static async listRoles(mountId: string): Promise<ShareRoleTemplate[]> {
    const resp = await request(`/api/v1/mounts/${encodeURIComponent(mountId)}/share-roles`);
    return asList<ShareRoleTemplate>(await resp.json()).map(normalizeRole);
  }

  static async updateRole(roleId: string, payload: { name?: string; roleExpiresAt?: string | null }): Promise<ShareRoleTemplate> {
    const resp = await request(`/api/v1/share-roles/${encodeURIComponent(roleId)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return normalizeRole((await resp.json()) as ShareRoleTemplate);
  }

  static async disableRole(roleId: string): Promise<void> {
    await request(`/api/v1/share-roles/${encodeURIComponent(roleId)}/disable`, { method: "POST" });
  }

  static async deleteRole(roleId: string): Promise<void> {
    await request(`/api/v1/share-roles/${encodeURIComponent(roleId)}`, { method: "DELETE" });
  }

  static async updatePathPolicies(roleId: string, items: Array<{ pathPattern: string; canVisible: boolean; canRead: boolean; canWrite: boolean }>): Promise<void> {
    await request(`/api/v1/share-roles/${encodeURIComponent(roleId)}/path-policies`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ items }),
    });
  }

  static async createLink(payload: { mountId: string; roleId: string; maxUses?: number; expiresAt?: string | null; roleExpireAt?: string | null }): Promise<ShareInfo> {
    const resp = await request(`/api/v1/mounts/${encodeURIComponent(payload.mountId)}/share-links`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ roleId: payload.roleId, maxUses: payload.maxUses, expiresAt: payload.expiresAt ?? null, roleExpireAt: payload.roleExpireAt ?? null }),
    });
    return normalizeShare((await resp.json()) as ShareInfo);
  }

  static async listRoleTemplatesV5(mountId: string): Promise<ShareRoleTemplate[]> {
    const resp = await request(`/api/v5/mounts/${encodeURIComponent(mountId)}/role-templates`);
    const rows = asList<{
      templateId?: string;
      template_id?: string;
      mountId?: string;
      mount_id?: string;
      roleId?: string;
      role_id?: string;
      name: string;
      state: string;
      defaultVisible?: boolean;
      default_visible?: boolean;
      defaultRead?: boolean;
      default_read?: boolean;
      defaultWrite?: boolean;
      default_write?: boolean;
    }>(await resp.json());
    return rows.map((row) => {
      const defaultVisible = row.defaultVisible ?? row.default_visible ?? false;
      const defaultRead = row.defaultRead ?? row.default_read ?? false;
      const defaultWrite = row.defaultWrite ?? row.default_write ?? false;
      const roleId = row.roleId ?? row.role_id ?? "";
      const templateId = row.templateId ?? row.template_id ?? "";
      const normalizedMountId = row.mountId ?? row.mount_id ?? mountId;
      const permissions: string[] = [];
      if (defaultVisible) permissions.push("visible");
      if (defaultRead) permissions.push("read");
      if (defaultWrite) permissions.push("write");
      return {
        id: roleId,
        templateId,
        roleId,
        mountId: normalizedMountId,
        name: row.name,
        permissions,
        defaultVisible,
        defaultRead,
        defaultWrite,
        builtin: ["owner", "visitor", "collaborator"].includes((row.name || "").trim().toLowerCase()),
      };
    });
  }

  static async createRoleTemplateV5(payload: {
    mountId: string;
    name: string;
    defaultVisible: boolean;
    defaultRead: boolean;
    defaultWrite: boolean;
  }): Promise<ShareRoleTemplate> {
    const resp = await request(`/api/v5/mounts/${encodeURIComponent(payload.mountId)}/role-templates`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "If-Match": "\"m-7\"" },
      body: JSON.stringify({
        name: payload.name,
        defaultVisible: payload.defaultVisible,
        defaultRead: payload.defaultRead,
        defaultWrite: payload.defaultWrite,
      }),
    });
    const row = (await resp.json()) as {
      templateId: string;
      mountId: string;
      roleId: string;
      name: string;
      state: string;
      defaultVisible: boolean;
      defaultRead: boolean;
      defaultWrite: boolean;
    };
    const permissions: string[] = [];
    if (row.defaultVisible) permissions.push("visible");
    if (row.defaultRead) permissions.push("read");
    if (row.defaultWrite) permissions.push("write");
    return {
      id: row.roleId,
      templateId: row.templateId,
      roleId: row.roleId,
      mountId: row.mountId,
      name: row.name,
      permissions,
      defaultVisible: row.defaultVisible,
      defaultRead: row.defaultRead,
      defaultWrite: row.defaultWrite,
      builtin: ["owner", "visitor", "collaborator"].includes((row.name || "").trim().toLowerCase()),
    };
  }

  static async deleteRoleTemplateV5(templateId: string): Promise<void> {
    await request(`/api/v5/role-templates/${encodeURIComponent(templateId)}`, {
      method: "DELETE",
      headers: { "If-Match": "\"m-7\"" },
    });
  }

  static async updateRoleTemplateV5(payload: {
    templateId: string;
    name: string;
    defaultVisible: boolean;
    defaultRead: boolean;
    defaultWrite: boolean;
  }): Promise<ShareRoleTemplate> {
    const resp = await request(`/api/v5/role-templates/${encodeURIComponent(payload.templateId)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", "If-Match": "\"m-7\"" },
      body: JSON.stringify({
        name: payload.name,
        defaultVisible: payload.defaultVisible,
        defaultRead: payload.defaultRead,
        defaultWrite: payload.defaultWrite,
      }),
    });
    const row = (await resp.json()) as {
      templateId: string;
      mountId: string;
      roleId: string;
      name: string;
      state: string;
      defaultVisible: boolean;
      defaultRead: boolean;
      defaultWrite: boolean;
    };
    const permissions: string[] = [];
    if (row.defaultVisible) permissions.push("visible");
    if (row.defaultRead) permissions.push("read");
    if (row.defaultWrite) permissions.push("write");
    return {
      id: row.roleId,
      templateId: row.templateId,
      roleId: row.roleId,
      mountId: row.mountId,
      name: row.name,
      permissions,
      defaultVisible: row.defaultVisible,
      defaultRead: row.defaultRead,
      defaultWrite: row.defaultWrite,
      builtin: ["owner", "visitor", "collaborator"].includes((row.name || "").trim().toLowerCase()),
    };
  }

  static async listRoleTemplatePrivilegesV5(templateId: string): Promise<ShareTemplatePrivilegeInfo[]> {
    const resp = await request(`/api/v5/role-templates/${encodeURIComponent(templateId)}/privileges`);
    return asList<ShareTemplatePrivilegeInfo>(await resp.json());
  }

  static async upsertRoleTemplatePrivilegeV5(
    templateId: string,
    payload: { targetPath: string; allowVisible: boolean; allowRead: boolean; allowWrite: boolean }
  ): Promise<ShareTemplatePrivilegeInfo> {
    const resp = await request(`/api/v5/role-templates/${encodeURIComponent(templateId)}/privileges`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", "If-Match": "\"m-7\"" },
      body: JSON.stringify(payload),
    });
    return (await resp.json()) as ShareTemplatePrivilegeInfo;
  }

  static async upsertRoleTemplatePrivilegesBatchV5(
    templateId: string,
    payload: { targetPaths: string[]; allowVisible: boolean; allowRead: boolean; allowWrite: boolean }
  ): Promise<ShareTemplatePrivilegeInfo[]> {
    const resp = await request(`/api/v5/role-templates/${encodeURIComponent(templateId)}/privileges/batch`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", "If-Match": "\"m-7\"" },
      body: JSON.stringify(payload),
    });
    return asList<ShareTemplatePrivilegeInfo>(await resp.json());
  }

  static async deleteRoleTemplatePrivilegeV5(templateId: string, privilegeId: string): Promise<void> {
    await request(`/api/v5/role-templates/${encodeURIComponent(templateId)}/privileges/${encodeURIComponent(privilegeId)}`, {
      method: "DELETE",
      headers: { "If-Match": "\"m-7\"" },
    });
  }

  static async effectiveByTemplateBatchV5(
    mountId: string,
    templateId: string,
    paths: string[]
  ): Promise<Record<string, ShareTemplateEffectiveInfo>> {
    const resp = await request(`/api/v5/mounts/${encodeURIComponent(mountId)}/permissions/template-preview-batch`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ templateId, paths }),
    });
    return (await resp.json()) as Record<string, ShareTemplateEffectiveInfo>;
  }

  static async listTemplateFilesV5(mountId: string, templateId: string, virtualPath: string): Promise<FileInfo[]> {
    const qs = new URLSearchParams({ templateId, virtualPath });
    const resp = await request(`/api/v5/mounts/${encodeURIComponent(mountId)}/permissions/template-files?${qs.toString()}`);
    const rows = asList<FileEntryV5>(((await resp.json()) as FileItemsV5Response).items);
    return rows.map((row) => ({
      path: row.path || "",
      name: row.name || "",
      isDirectory: (row.type || "file") === "directory",
      size: Number.isFinite(row.sizeBytes) ? Number(row.sizeBytes) : 0,
      mtime: row.mtime || new Date(0).toISOString(),
      visible: row.visible ?? false,
      readable: row.readable ?? false,
      writable: row.writable ?? false,
      etag: row.etag,
      version: row.version,
    }));
  }

  static async createLinkV5(payload: { mountId: string; roleId: string; maxUses?: number; expiresAt?: string | null; roleExpireAt?: string | null }): Promise<ShareInfo> {
    const resp = await request(`/api/v5/mounts/${encodeURIComponent(payload.mountId)}/share-links`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "If-Match": "\"m-7\"" },
      body: JSON.stringify({ roleId: payload.roleId, maxUses: payload.maxUses, expireAt: payload.expiresAt ?? null, roleExpireAt: payload.roleExpireAt ?? null }),
    });
    return normalizeShare((await resp.json()) as ShareInfo);
  }

  static async listLinksByMount(mountId: string): Promise<ShareInfo[]> {
    const resp = await request(`/api/v5/mounts/${encodeURIComponent(mountId)}/share-links`);
    return asList<ShareInfo>(await resp.json()).map((row) => {
      const normalized = normalizeShare(row);
      return {
        ...normalized,
        shareUrl: normalized.token
          ? `${window.location.origin}/app/shares/shared-users?shareToken=${encodeURIComponent(normalized.token)}&mountId=${encodeURIComponent(normalized.mountId)}`
          : undefined,
      };
    });
  }

  static async listLinks(page = 1, pageSize = 20): Promise<ShareInfo[]> {
    const query = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
    const resp = await request(`/api/v1/share-links?${query.toString()}`);
    return asList<ShareInfo>(await resp.json()).map(normalizeShare);
  }

  static async getLink(linkId: string): Promise<ShareInfo> {
    const resp = await request(`/api/v1/share-links/${encodeURIComponent(linkId)}`);
    return normalizeShare((await resp.json()) as ShareInfo);
  }

  static async updateLink(linkId: string, payload: { maxUses?: number; expiresAt?: string | null }): Promise<ShareInfo> {
    const resp = await request(`/api/v1/share-links/${encodeURIComponent(linkId)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return normalizeShare((await resp.json()) as ShareInfo);
  }

  static async revokeLink(linkId: string): Promise<void> {
    await request(`/api/v1/share-links/${encodeURIComponent(linkId)}/revoke`, { method: "POST" });
  }

  static async revokeLinkV5(linkId: string): Promise<void> {
    await request(`/api/v5/share-links/${encodeURIComponent(linkId)}/revoke`, {
      method: "POST",
      headers: { "If-Match": "\"m-7\"" },
    });
  }

  static async deleteLinkV5(linkId: string): Promise<void> {
    await request(`/api/v5/share-links/${encodeURIComponent(linkId)}`, {
      method: "DELETE",
      headers: { "If-Match": "\"m-7\"" },
    });
  }

  static async deleteLink(linkId: string): Promise<void> {
    await request(`/api/v1/share-links/${encodeURIComponent(linkId)}`, { method: "DELETE" });
  }

  static async resolveLink(token: string): Promise<{ mountId: string; roleId: string }> {
    const resp = await request("/api/v1/share-links/resolve", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token }),
    });
    return (await resp.json()) as { mountId: string; roleId: string };
  }

  static async resolveLinkV5(token: string): Promise<{ mountId: string; roleId: string }> {
    const resp = await request("/api/v5/share-links/resolve", {
      method: "POST",
      headers: { "Content-Type": "application/json", "If-Match": "\"m-7\"" },
      body: JSON.stringify({ token }),
    });
    return (await resp.json()) as { mountId: string; roleId: string };
  }

  static async listMyRolesV5(mountId: string): Promise<ShareMyRoleInfo[]> {
    const resp = await request(`/api/v5/mounts/${encodeURIComponent(mountId)}/my-roles`);
    const rows = asList<ShareMyRoleInfo>(await resp.json());
    return rows.map((r) => ({ ...r, mountId: r.mountId || mountId }));
  }

  static async listGrantedRolesV5(mountId: string): Promise<ShareMyRoleInfo[]> {
    const resp = await request(`/api/v5/mounts/${encodeURIComponent(mountId)}/granted-roles`);
    const rows = asList<ShareMyRoleInfo>(await resp.json());
    return rows.map((r) => ({ ...r, mountId: r.mountId || mountId }));
  }

  static async updateGrantedRoleV5(payload: {
    mountId: string;
    granteeUserId: string;
    currentRoleId: string;
    roleId: string;
    roleExpireAt?: string | null;
  }): Promise<ShareMyRoleInfo> {
    const resp = await request(
      `/api/v5/mounts/${encodeURIComponent(payload.mountId)}/granted-roles/${encodeURIComponent(payload.granteeUserId)}/roles/${encodeURIComponent(payload.currentRoleId)}`,
      {
        method: "PUT",
        headers: { "Content-Type": "application/json", "If-Match": "\"m-7\"" },
        body: JSON.stringify({ roleId: payload.roleId, roleExpireAt: payload.roleExpireAt ?? null }),
      }
    );
    return (await resp.json()) as ShareMyRoleInfo;
  }

  static async revokeGrantedRoleV5(payload: { mountId: string; granteeUserId: string; roleId: string }): Promise<void> {
    await request(
      `/api/v5/mounts/${encodeURIComponent(payload.mountId)}/granted-roles/${encodeURIComponent(payload.granteeUserId)}/roles/${encodeURIComponent(payload.roleId)}`,
      {
        method: "DELETE",
        headers: { "If-Match": "\"m-7\"" },
      }
    );
  }

  static async listMyRoleSummariesV5(): Promise<ShareMyRoleSummaryInfo[]> {
    const resp = await request("/api/v5/share-links/my-roles");
    return asList<ShareMyRoleSummaryInfo>(await resp.json());
  }

  static async switchRole(mountId: string, roleId: string): Promise<void> {
    await request(`/api/v1/shared-mounts/${encodeURIComponent(mountId)}/switch-role`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ roleId }),
    });
  }

  static async effectivePermission(mountId: string, path: string): Promise<string[]> {
    const query = new URLSearchParams({ mountId, path });
    const resp = await request(`/api/v1/permissions/effective?${query.toString()}`);
    const body = (await resp.json()) as { permissions?: string[] };
    return body.permissions ?? [];
  }

  static async listShareGrants(mountId: string): Promise<ShareGrantInfo[]> {
    const resp = await request(`/api/v1/mounts/${encodeURIComponent(mountId)}/share-grants`);
    return asList<ShareGrantInfo>(await resp.json()).map(normalizeGrant);
  }

  static async upsertShareGrant(payload: { mountId: string; role: string; path_scopes?: string[]; permissions?: string[] }): Promise<ShareGrantInfo> {
    const resp = await request(`/api/v1/mounts/${encodeURIComponent(payload.mountId)}/share-grants`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        role: payload.role,
        pathScopes: payload.path_scopes ?? [],
        permissions: payload.permissions ?? [],
      }),
    });
    return normalizeGrant((await resp.json()) as ShareGrantInfo);
  }

  static async deleteShareGrant(mountId: string, id: string): Promise<void> {
    await request(`/api/v1/mounts/${encodeURIComponent(mountId)}/share-grants/${encodeURIComponent(id)}`, { method: "DELETE" });
  }

  static async previewSharePermissions(mountId: string, role: string, path = "/", maxDepth = 3): Promise<SharePreviewNode[]> {
    const query = new URLSearchParams({
      roleId: role,
      path,
      maxDepth: String(maxDepth),
    });
    const resp = await request(`/api/v1/mounts/${encodeURIComponent(mountId)}/permissions/preview?${query.toString()}`);
    return asList<SharePreviewNode>(await resp.json());
  }
}
