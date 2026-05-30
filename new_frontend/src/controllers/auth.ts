import type { AuthUser } from "../hooks/authStorage";

type AuthPayload = {
  token?: {
    tokenType?: string;
    accessToken?: string;
    refreshToken?: string;
    sessionId?: string;
  };
  user?: {
    userId?: string;
    username?: string;
    displayName?: string;
    role?: string;
    status?: string;
    qosProfile?: string;
  };
};

type CaptchaPayload = {
  captchaId: string;
  scene: string;
  message: string;
  expiresInSeconds: number;
  imageDataUrl: string;
};

function toErrorText(payload: unknown, fallback: string): string {
  if (!payload || typeof payload !== "object") return fallback;
  const body = payload as { error?: string | { code?: string; message?: string } };
  if (typeof body.error === "string") return body.error;
  if (body.error && typeof body.error === "object") {
    return `${body.error.code ? `[${body.error.code}] ` : ""}${body.error.message || fallback}`;
  }
  return fallback;
}

async function parseResponseError(resp: Response): Promise<string> {
  const text = await resp.text();
  if (!text) return `HTTP ${resp.status}`;
  try {
    return toErrorText(JSON.parse(text), text);
  } catch {
    return text;
  }
}

async function request(path: string, init?: RequestInit): Promise<Response> {
  const resp = await fetch(path, init);
  if (!resp.ok) throw new Error(await parseResponseError(resp));
  return resp;
}

function normalizeAuthUser(payload: AuthPayload): AuthUser {
  const u = payload.user ?? {};
  const t = payload.token ?? {};
  return {
    userId: u.userId || "",
    username: u.username || "",
    displayName: u.displayName || u.username || "",
    role: u.role || "user",
    status: u.status,
    qosProfile: u.qosProfile,
    tokenType: t.tokenType,
    accessToken: t.accessToken,
    refreshToken: t.refreshToken,
    sessionId: t.sessionId,
  };
}

/**
 * 执行登录并把后端 token/user 结构归一化为前端会话模型。
 * 失败时抛出已解析的业务错误文本（可能包含错误码前缀），供表单层做字段/表单级提示分流。
 */
export async function login(username: string, password: string, captchaId?: string, captchaAnswer?: string): Promise<AuthUser> {
  const resp = await request("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password, captchaId, captchaAnswer }),
  });
  const payload = (await resp.json()) as AuthPayload;
  return normalizeAuthUser(payload);
}

/**
 * 执行注册并返回可直接写入本地会话的用户信息。
 * 当注册命中验证码、用户名冲突等业务失败时，异常消息会保留后端错误语义用于前端映射。
 */
export async function register(payload: {
  username: string;
  displayName: string;
  password: string;
  email?: string;
  phone?: string;
  captchaId?: string;
  captchaAnswer?: string;
}): Promise<AuthUser> {
  const resp = await request("/api/v1/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return normalizeAuthUser((await resp.json()) as AuthPayload);
}

/** 按场景申请验证码，场景参数用于区分登录与注册风控策略。 */
export async function issueCaptcha(scene: "login" | "register" = "login"): Promise<CaptchaPayload> {
  const resp = await request("/api/v1/auth/captcha", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ scene }),
  });
  return (await resp.json()) as CaptchaPayload;
}

/** 使用 refreshToken + sessionId 续期会话，避免前端仅凭 access token 失效后强制退出。 */
export async function refresh(refreshToken: string, sessionId: string): Promise<AuthUser> {
  const resp = await request("/api/v1/auth/refresh", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken, sessionId }),
  });
  return normalizeAuthUser((await resp.json()) as AuthPayload);
}

/** 查询当前会话状态，用于应用冷启动时判定是否仍可复用服务端会话。 */
export async function getSession(): Promise<{ user: { username: string; role: string }; status: string }> {
  const resp = await request("/api/v1/auth/session");
  return (await resp.json()) as { user: { username: string; role: string }; status: string };
}

/** 注销会话：有会话凭据时走受控注销接口；凭据缺失时降级为尽力登出，避免前端卡死在退出流程。 */
export async function logout(refreshToken?: string, sessionId?: string): Promise<void> {
  if (refreshToken && sessionId) {
    await request("/api/v1/auth/logout", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken, sessionId }),
    });
    return;
  }
  await fetch("/api/v1/auth/logout", { method: "POST" });
}
