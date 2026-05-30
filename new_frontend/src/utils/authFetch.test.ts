import { describe, expect, it } from "vitest";
import { buildAuthInit } from "./authFetch";

describe("buildAuthInit", () => {
  it("给 /api/v4 请求注入 Authorization", () => {
    const init = buildAuthInit(
      "/api/v4/transfers/me/rates",
      { "X-Test": "1" },
      "Bearer token-v4"
    );
    expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer token-v4");
  });

  it("不覆盖调用方已显式设置的 Authorization", () => {
    const init = buildAuthInit(
      "/api/v4/transfers/me/rates",
      { Authorization: "Bearer caller-token" },
      "Bearer storage-token"
    );
    expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer caller-token");
  });

  it("登录接口不注入 Authorization", () => {
    const init = buildAuthInit(
      "/api/v1/auth/login",
      { "X-Test": "1" },
      "Bearer token"
    );
    expect(new Headers(init?.headers).get("Authorization")).toBeNull();
  });
});
