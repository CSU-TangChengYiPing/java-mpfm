import { describe, expect, it } from "vitest";
import { canDoShareAction, resolvePlatformRole } from "./guardMatrix";

describe("shares guard matrix", () => {
  it("should resolve platform role without using share-role", () => {
    expect(resolvePlatformRole("root", false)).toBe("ROOT");
    expect(resolvePlatformRole("admin", false)).toBe("ADMIN");
    expect(resolvePlatformRole("user", false)).toBe("USER");
    expect(resolvePlatformRole(undefined, true)).toBe("ROOT");
  });

  it("should enforce page action guard matrix consistently", () => {
    expect(canDoShareAction("manage_role_template", "ROOT", false, false)).toBe(true);
    expect(canDoShareAction("manage_role_template", "ADMIN", false, false)).toBe(false);
    expect(canDoShareAction("manage_role_template", "ADMIN", false, true)).toBe(true);
    expect(canDoShareAction("manage_path_policy", "USER", true, false)).toBe(true);
    expect(canDoShareAction("revoke_link", "USER", false, false)).toBe(false);
    expect(canDoShareAction("create_link", "USER", false, true)).toBe(true);
    expect(canDoShareAction("create_link", "USER", false, false)).toBe(false);
  });
});
