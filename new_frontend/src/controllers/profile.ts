import i18n from "../i18n";

async function parseErrorMessage(resp: Response): Promise<string> {
  const text = await resp.text();
  const requestId = resp.headers.get("X-Request-Id") || "";
  const withRequestId = (message: string): string => (requestId ? `${message} (requestId: ${requestId})` : message);
  try {
    const json = JSON.parse(text) as { error?: string | { code?: string; message?: string } };
    if (typeof json.error === "string") return withRequestId(json.error);
    if (json.error && typeof json.error === "object") {
      return withRequestId(`${json.error.code ? `[${json.error.code}] ` : ""}${json.error.message ?? text}`);
    }
    return withRequestId(text);
  } catch {
    if (text) return withRequestId(text);
    if (resp.status >= 500) return withRequestId(i18n.t("common.serverError"));
    return withRequestId(`HTTP ${resp.status}`);
  }
}

async function request(path: string, init?: RequestInit): Promise<Response> {
  const resp = await fetch(path, init);
  if (!resp.ok) throw new Error(await parseErrorMessage(resp));
  return resp;
}

function withIfMatchHeader(headers?: HeadersInit): Headers {
  const merged = new Headers(headers || {});
  // 后端并发门禁要求 PUT/PATCH/DELETE 必带 If-Match；当前用户资料接口先使用通配占位。
  if (!merged.has("If-Match")) {
    merged.set("If-Match", "*");
  }
  return merged;
}

type MeApi = {
  userId: string;
  username: string;
  displayName: string;
  email?: string;
  phone?: string;
  avatarUrl?: string;
  language?: string;
  fileViewMode?: string;
  qosProfile?: string;
  role: string;
  status?: string;
};

type SearchRaw = { userId: string; username: string; displayName: string; avatarUrl?: string; role: string; status: string; qosProfile?: string };

type ProfileMeView = {
  user_id: string;
  nickname: string;
  is_root: boolean;
  bio: string;
  avatar_url: string;
  activities: Array<{ id: string; type: string; title: string; created_at: string }>;
  userId: string;
  username: string;
  displayName: string;
  role: string;
  status?: string;
  qosProfile?: string;
  email?: string;
  phone?: string;
  language?: string;
  fileViewMode?: string;
};

type ProfileSearchItem = { user_id: string; nickname: string; is_root: boolean; avatar_url: string; username: string; role: string; status: string; qosProfile?: string };

/** 统一个人信息输出模型，兼容历史字段并补齐页面依赖的派生字段。 */
function toProfileMeView(me: MeApi): ProfileMeView {
  return {
    user_id: me.userId,
    nickname: me.displayName,
    is_root: me.role.toLowerCase() === "root",
    bio: "",
    avatar_url: me.avatarUrl || "",
    activities: [],
    ...me,
  };
}

/** 统一搜索结果映射，避免不同调用点重复拼装 user_id/nickname/is_root 字段。 */
function toProfileSearchItem(row: SearchRaw): ProfileSearchItem {
  return {
    user_id: row.userId,
    nickname: row.displayName,
    is_root: row.role.toLowerCase() === "root",
    avatar_url: row.avatarUrl || "",
    username: row.username,
    role: row.role,
    status: row.status,
    qosProfile: row.qosProfile,
  };
}

/** 当前用户资料域控制器：负责个人资料、偏好、会话与头像等接口调用，并落实 If-Match 并发门禁头。 */
export default class ProfileController {
  static async me(): Promise<{
    user_id: string;
    nickname: string;

    is_root: boolean;
    bio: string;
    avatar_url: string;
    activities: Array<{ id: string; type: string; title: string; created_at: string }>;
    userId: string;
    username: string;
    displayName: string;
    role: string;
    status?: string;
    qosProfile?: string;
    email?: string;
    phone?: string;
    language?: string;
    fileViewMode?: string;
  }> {
    const resp = await request("/api/v1/users/me");
    const me = (await resp.json()) as MeApi;
    return toProfileMeView(me);
  }

  static async updateMe(payload: { nickname?: string; displayName?: string; bio?: string; email?: string; phone?: string }): Promise<void> {
    await request("/api/v1/users/me/profile", {
      method: "PUT",
      headers: withIfMatchHeader({ "Content-Type": "application/json" }),
      body: JSON.stringify({ displayName: payload.displayName || payload.nickname || "", email: payload.email, phone: payload.phone }),
    });
  }

  static async updatePreferences(payload: { language: string; fileViewMode: string }): Promise<void> {
    await request("/api/v1/users/me/preferences", {
      method: "PUT",
      headers: withIfMatchHeader({ "Content-Type": "application/json" }),
      body: JSON.stringify(payload),
    });
  }

  static async changeCredential(payload: { oldCredential: string; newCredential: string }): Promise<{ action: string; status: string; revokedSessions: number }> {
    const resp = await request("/api/v1/users/me/change-credential", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return (await resp.json()) as { action: string; status: string; revokedSessions: number };
  }

  static async searchUsers(params: { q?: string; username?: string; displayName?: string; status?: string; cursor?: string; limit?: number }): Promise<{
    items: ProfileSearchItem[];
    next_cursor: string;
  }> {
    const query = new URLSearchParams();
    if (params.username || params.q) query.set("username", params.username || params.q || "");
    if (params.displayName || params.q) query.set("displayName", params.displayName || params.q || "");
    if (params.status) query.set("status", params.status);
    const qs = query.toString();
    const resp = await request(`/api/v1/users/me/search${qs ? `?${qs}` : ""}`);
    const list = (await resp.json()) as SearchRaw[];
    return {
      items: list.map(toProfileSearchItem),
      next_cursor: "",
    };
  }

  static async uploadAvatar(file: File): Promise<{ avatar_url: string; content_type: string }> {
    const asDataUrl = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result || ""));
      reader.onerror = () => reject(new Error(i18n.t("avatar.readFileFailed")));
      reader.readAsDataURL(file);
    });
    const resp = await request("/api/v1/users/me/avatar", {
      method: "PUT",
      headers: withIfMatchHeader({ "Content-Type": "application/json" }),
      body: JSON.stringify({ avatarUrl: asDataUrl }),
    });
    const me = (await resp.json()) as MeApi;
    return { avatar_url: me.avatarUrl || "", content_type: "image/jpeg" };
  }

  static async sessions(): Promise<Array<{ sessionId: string; status: string; expiresAt: string; clientIp?: string; userAgent?: string; deviceLabel?: string }>> {
    const resp = await request("/api/v1/users/me/sessions");
    return (await resp.json()) as Array<{ sessionId: string; status: string; expiresAt: string; clientIp?: string; userAgent?: string; deviceLabel?: string }>;
  }

  static async revokeSession(sessionId: string): Promise<{ action: string; status: string; revokedSessions: number }> {
    const resp = await request(`/api/v1/users/me/sessions/${encodeURIComponent(sessionId)}/revoke`, { method: "POST" });
    return (await resp.json()) as { action: string; status: string; revokedSessions: number };
  }
}

