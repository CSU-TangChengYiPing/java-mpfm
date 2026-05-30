import { AUTH_KEY } from "../hooks/authStorage";

const AUTH_SKIP_PATHS = [
  "/api/v1/auth/login",
  "/api/v1/auth/register",
  "/api/v1/auth/captcha",
  "/api/v1/auth/refresh",
];

/** 从 fetch 入参提取请求路径，兼容 string/URL/Request 三种形态。 */
export function extractPath(input: RequestInfo | URL): string {
  if (typeof input === "string") {
    try {
      return new URL(input, window.location.origin).pathname;
    } catch {
      return input;
    }
  }
  if (input instanceof URL) return input.pathname;
  return input.url;
}

/** 从本地会话中读取 Authorization 头值；缺失或解析失败时返回 null。 */
export function readAuthHeaderFromStorage(): string | null {
  const raw = localStorage.getItem(AUTH_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as { tokenType?: string; accessToken?: string };
    if (!parsed.accessToken) return null;
    const tokenType = (parsed.tokenType || "Bearer").trim();
    return `${tokenType} ${parsed.accessToken}`;
  } catch {
    return null;
  }
}

/** 构造带鉴权头的 RequestInit：仅对 API 路径生效，并跳过登录/注册/刷新等白名单。 */
export function buildAuthInit(input: RequestInfo | URL, headers?: HeadersInit, authHeader?: string): RequestInit | undefined {
  const path = extractPath(input);
  if (!path.includes("/api/")) return headers ? { headers } : undefined;
  if (AUTH_SKIP_PATHS.some((item) => path.includes(item))) return headers ? { headers } : undefined;
  const token = authHeader || readAuthHeaderFromStorage();
  if (!token) return headers ? { headers } : undefined;
  const nextHeaders = new Headers(headers);
  if (nextHeaders.has("Authorization")) return headers ? { headers } : undefined;
  nextHeaders.set("Authorization", token);
  return { headers: nextHeaders };
}

/** 在保留调用方原始配置的前提下，按规则注入 Authorization 请求头。 */
export function withAuthHeader(input: RequestInfo | URL, init?: RequestInit): RequestInit | undefined {
  const merged = buildAuthInit(input, init?.headers);
  if (!merged) return init;
  return { ...init, ...merged };
}
