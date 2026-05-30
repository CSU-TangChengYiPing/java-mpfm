import { AUTH_KEY } from "../hooks/authStorage";
import { extractPath, withAuthHeader } from "./authFetch";

const HTTP_STATUS_UNAUTHORIZED = 401;
const REFRESH_PATH = "/api/v1/auth/refresh";

const UNAUTHORIZED_SKIP_PATHS = [
  "/api/v1/auth/login",
  "/api/v1/auth/logout",
  "/api/v1/auth/register",
  "/api/v1/auth/refresh",
  "/api/v1/auth/captcha",
  "/api/v1/users/me",
  "/api/health",
];

type StoredAuth = {
  tokenType?: string;
  accessToken?: string;
  refreshToken?: string;
  sessionId?: string;
};

function loadStoredAuth(): StoredAuth | null {
  const raw = localStorage.getItem(AUTH_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredAuth;
  } catch {
    return null;
  }
}

function saveStoredAuth(next: StoredAuth): void {
  localStorage.setItem(AUTH_KEY, JSON.stringify(next));
}

function shouldSkipUnauthorized(path: string): boolean {
  return UNAUTHORIZED_SKIP_PATHS.some((item) => path.includes(item));
}

/**
 * 创建带会话续期能力的 fetch：当业务请求返回 401 时，先尝试 refresh，再进行一次重试。
 * 前置条件：本地需存在 refreshToken 与 sessionId；刷新或重试仍失败时触发 onUnauthorized。
 */
export function createSessionAwareFetch(
  rawFetch: typeof fetch,
  onUnauthorized: () => void
): typeof fetch {
  let refreshPromise: Promise<boolean> | null = null;

  async function refreshSession(): Promise<boolean> {
    if (refreshPromise) return refreshPromise;
    refreshPromise = (async () => {
      const cached = loadStoredAuth();
      if (!cached?.refreshToken || !cached.sessionId) return false;
      const resp = await rawFetch(REFRESH_PATH, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: cached.refreshToken, sessionId: cached.sessionId }),
      });
      if (!resp.ok) return false;
      const payload = (await resp.json()) as { token?: StoredAuth };
      const token = payload.token;
      if (!token?.accessToken) return false;
      saveStoredAuth({ ...cached, ...token });
      return true;
    })().finally(() => {
      refreshPromise = null;
    });
    return refreshPromise;
  }

  return async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const firstInit = withAuthHeader(input, init);
    const firstResp = await rawFetch(input, firstInit);
    if (firstResp.status !== HTTP_STATUS_UNAUTHORIZED) return firstResp;

    const path = extractPath(input);
    if (shouldSkipUnauthorized(path)) return firstResp;

    const refreshed = await refreshSession();
    if (refreshed) {
      const retryInit = withAuthHeader(input, init);
      const retryResp = await rawFetch(input, retryInit);
      if (retryResp.status !== HTTP_STATUS_UNAUTHORIZED) return retryResp;
    }

    onUnauthorized();
    return firstResp;
  };
}
