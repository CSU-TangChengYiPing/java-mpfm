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

export type UserInfo = {
  userId: string;
  username: string;
  displayName: string;
  role: string;
  status: string;
  qosProfile?: string;
  qosCustomEnabled?: boolean;
  qosCustomUploadBps?: number;
  qosCustomDownloadBps?: number;
  uploadPaused?: boolean;
  downloadPaused?: boolean;
  uploadBps?: number;
  downloadBps?: number;
  activeUploadTasks?: number;
  activeDownloadTasks?: number;
};

export type QosPolicyInfo = {
  id: string;
  name: string;
  maxUploadBps: number;
  maxDownloadBps: number;
};

export type QosUserCustomLimit = {
  username: string;
  maxUploadBps: number;
  maxDownloadBps: number;
  customized: boolean;
};

export type UserTransferTimelinePoint = {
  timestamp: string;
  uploadBps: number;
  downloadBps: number;
};

export type UserGovernanceResponse = {
  username: string;
  uploadPaused: boolean;
  downloadPaused: boolean;
  kickedTasks: number;
  action: string;
  status: string;
};

/** 用户管理控制器：面向管理端封装用户列表、创建、更新、禁用与重置凭据接口。 */
export default class UsersController {
  static async list(params?: { q?: string; page?: number; pageSize?: number }): Promise<{ users: UserInfo[]; page: { page: number; pageSize: number; total: number } }> {
    const query = new URLSearchParams();
    if (params?.q?.trim()) query.set("q", params.q.trim());
    query.set("page", String(params?.page ?? 1));
    query.set("pageSize", String(params?.pageSize ?? 20));
    const resp = await request(`/api/v1/admin/users?${query.toString()}`);
    const raw = (await resp.json()) as {
      items: Array<{
        userId: string; username: string; displayName: string; role: string; status: string; qosProfile?: string;
        customUploadBps?: number; customDownloadBps?: number; qosCustomEnabled?: boolean; uploadPaused?: boolean; downloadPaused?: boolean;
      }>;
      page: { page: number; pageSize: number; total: number };
    };
    return {
      users: (raw.items ?? []).map((x) => ({
        userId: x.userId,
        username: x.username,
        displayName: x.displayName,
        role: x.role,
        status: x.status,
        qosProfile: x.qosProfile,
        qosCustomEnabled: !!x.qosCustomEnabled,
        qosCustomUploadBps: x.customUploadBps ?? 0,
        qosCustomDownloadBps: x.customDownloadBps ?? 0,
        uploadPaused: !!x.uploadPaused,
        downloadPaused: !!x.downloadPaused,
      })),
      page: raw.page ?? { page: 1, pageSize: 20, total: 0 },
    };
  }

  static async listQosPolicies(): Promise<QosPolicyInfo[]> {
    const resp = await request("/api/v1/admin/qos/policies");
    return (await resp.json()) as QosPolicyInfo[];
  }

  static async createQosPolicy(payload: Omit<QosPolicyInfo, "id">): Promise<QosPolicyInfo> {
    const resp = await request("/api/v1/admin/qos/policies", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return (await resp.json()) as QosPolicyInfo;
  }

  static async upsertQosPolicy(policyId: string, payload: Omit<QosPolicyInfo, "id">): Promise<QosPolicyInfo> {
    const resp = await request(`/api/v1/admin/qos/policies/${encodeURIComponent(policyId)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return (await resp.json()) as QosPolicyInfo;
  }

  static async deleteQosPolicy(policyId: string): Promise<{ removedCount: number }> {
    const resp = await request(`/api/v1/admin/qos/policies/${encodeURIComponent(policyId)}`, { method: "DELETE" });
    return (await resp.json()) as { removedCount: number };
  }

  static async batchDeleteQosPolicy(policyIds: string[]): Promise<{ removedCount: number }> {
    const resp = await request("/api/v1/admin/qos/policies/batch-delete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ policyIds }),
    });
    return (await resp.json()) as { removedCount: number };
  }

  static async getUserCustomLimit(username: string): Promise<QosUserCustomLimit> {
    const resp = await request(`/api/v1/admin/qos/users/${encodeURIComponent(username)}/custom-limit`);
    return (await resp.json()) as QosUserCustomLimit;
  }

  static async updateUserCustomLimit(username: string, maxUploadBps: number, maxDownloadBps: number): Promise<QosUserCustomLimit> {
    const resp = await request(`/api/v1/admin/qos/users/${encodeURIComponent(username)}/custom-limit`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ maxUploadBps, maxDownloadBps }),
    });
    return (await resp.json()) as QosUserCustomLimit;
  }

  static async listUserTransferStats(): Promise<Array<{
    username: string;
    uploadBps: number;
    downloadBps: number;
    activeUploadTasks: number;
    activeDownloadTasks: number;
  }>> {
    const resp = await request("/api/v1/admin/telemetry/users/transfer");
    return (await resp.json()) as Array<{
      username: string;
      uploadBps: number;
      downloadBps: number;
      activeUploadTasks: number;
      activeDownloadTasks: number;
    }>;
  }

  static async userTransferTimeline(username: string, minutes: 1 | 5 | 15): Promise<UserTransferTimelinePoint[]> {
    const resp = await request(`/api/v1/admin/telemetry/users/${encodeURIComponent(username)}/history?minutes=${minutes}`);
    return (await resp.json()) as UserTransferTimelinePoint[];
  }

  static async governUserTransfer(username: string, action: string, policyId?: string): Promise<UserGovernanceResponse> {
    const resp = await request(`/api/v1/admin/telemetry/users/${encodeURIComponent(username)}/governance`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action, policyId }),
    });
    return (await resp.json()) as UserGovernanceResponse;
  }

  static async getUserGovernanceState(username: string): Promise<UserGovernanceResponse> {
    const resp = await request(`/api/v1/admin/telemetry/users/${encodeURIComponent(username)}/governance`);
    return (await resp.json()) as UserGovernanceResponse;
  }

  static async create(payload: {
    username: string;
    password: string;
    displayName: string;
    email?: string;
    phone?: string;
    role: "user" | "admin";
    qosProfile?: string;
  }): Promise<void> {
    await request("/api/v1/admin/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
  }

  /**
   * 用户更新写操作：统一走 admin users PUT 契约，并回收后端治理字段到页面模型。
   * 前置条件：userId 有效且 payload 已完成前端校验；请求失败由 request 抛出业务错误供 UI 映射提示。
   */
  static async update(userId: string, payload: {
    displayName: string;
    email?: string;
    phone?: string;
    role: "user" | "admin";
    status: "active" | "disabled";
    qosProfile?: string;
    customUploadBps?: number;
    customDownloadBps?: number;
    qosCustomEnabled?: boolean;
    uploadPaused?: boolean;
    downloadPaused?: boolean;
  }): Promise<UserInfo> {
    const start = performance.now();
    console.info("[users] update.request.start", { userId, at: start });
    const resp = await request(`/api/v1/admin/users/${encodeURIComponent(userId)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const responseAt = performance.now();
    console.info("[users] update.request.done", { userId, networkMs: Math.round(responseAt - start) });
    const raw = (await resp.json()) as {
      userId: string; username: string; displayName: string; role: string; status: string; qosProfile?: string;
      customUploadBps?: number; customDownloadBps?: number; qosCustomEnabled?: boolean; uploadPaused?: boolean; downloadPaused?: boolean;
    };
    return {
      userId: raw.userId,
      username: raw.username,
      displayName: raw.displayName,
      role: raw.role,
      status: raw.status,
      qosProfile: raw.qosProfile,
      qosCustomEnabled: !!raw.qosCustomEnabled,
      qosCustomUploadBps: raw.customUploadBps ?? 0,
      qosCustomDownloadBps: raw.customDownloadBps ?? 0,
      uploadPaused: !!raw.uploadPaused,
      downloadPaused: !!raw.downloadPaused,
    };
  }

  static async disable(userId: string): Promise<void> {
    await request(`/api/v1/admin/users/${encodeURIComponent(userId)}/disable`, { method: "POST" });
  }

  static async resetCredential(userId: string, newCredential: string): Promise<void> {
    await request(`/api/v1/admin/users/${encodeURIComponent(userId)}/reset-credential`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ newCredential }),
    });
  }
}
