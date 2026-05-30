import { beforeEach, describe, expect, it, vi } from "vitest";
import { AUTH_KEY } from "../hooks/authStorage";
import { createSessionAwareFetch } from "./authSessionFetch";

function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function createMemoryStorage(): Storage {
  const map = new Map<string, string>();
  return {
    get length() {
      return map.size;
    },
    clear() {
      map.clear();
    },
    getItem(key: string) {
      return map.has(key) ? map.get(key)! : null;
    },
    key(index: number) {
      return Array.from(map.keys())[index] ?? null;
    },
    removeItem(key: string) {
      map.delete(key);
    },
    setItem(key: string, value: string) {
      map.set(key, value);
    },
  };
}

if (!("localStorage" in globalThis)) {
  Object.defineProperty(globalThis, "localStorage", {
    value: createMemoryStorage(),
    writable: false,
    configurable: true,
  });
}

describe("createSessionAwareFetch", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("业务接口 401 时应先 refresh 再重试原请求", async () => {
    localStorage.setItem(AUTH_KEY, JSON.stringify({
      tokenType: "Bearer",
      accessToken: "old-access",
      refreshToken: "refresh-1",
      sessionId: "session-1",
    }));
    const onUnauthorized = vi.fn();
    const rawFetch = vi
      .fn<(...args: Parameters<typeof fetch>) => ReturnType<typeof fetch>>()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse({ token: { tokenType: "Bearer", accessToken: "new-access", refreshToken: "refresh-2", sessionId: "session-2" } }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }));

    const wrapped = createSessionAwareFetch(rawFetch, onUnauthorized);
    const resp = await wrapped("/api/v1/files", { method: "GET" });

    expect(resp.status).toBe(200);
    expect(await resp.json()).toEqual({ ok: true });
    expect(rawFetch).toHaveBeenCalledTimes(3);
    const retriedInit = (rawFetch as unknown as { mock: { calls: Array<[RequestInfo | URL, RequestInit?]> } }).mock.calls[2]?.[1];
    expect(new Headers(retriedInit?.headers).get("Authorization")).toBe("Bearer new-access");
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it("refresh 失败时应触发未授权回调", async () => {
    localStorage.setItem(AUTH_KEY, JSON.stringify({
      tokenType: "Bearer",
      accessToken: "old-access",
      refreshToken: "refresh-1",
      sessionId: "session-1",
    }));
    const onUnauthorized = vi.fn();
    const rawFetch = vi
      .fn<(...args: Parameters<typeof fetch>) => ReturnType<typeof fetch>>()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(null, { status: 401 }));

    const wrapped = createSessionAwareFetch(rawFetch, onUnauthorized);
    const resp = await wrapped("/api/v1/files", { method: "GET" });

    expect(resp.status).toBe(401);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });
});
