import { describe, expect, it } from "vitest";
import { buildNextPathFromDirectoryClick, isConcreteNamespacePath } from "./path_navigation";

describe("buildNextPathFromDirectoryClick", () => {
  it("should keep relative child path inside mount even if child starts with personal", () => {
    const next = buildNextPathFromDirectoryClick("/personal/mount-a", "personal/docs");
    expect(next).toBe("/personal/mount-a/personal/docs");
  });

  it("should keep relative child path inside mount even if child starts with shared", () => {
    const next = buildNextPathFromDirectoryClick("/personal/mount-a", "shared/assets");
    expect(next).toBe("/personal/mount-a/shared/assets");
  });

  it("should honor absolute virtual path", () => {
    const next = buildNextPathFromDirectoryClick(".", "/personal/mount-a");
    expect(next).toBe("/personal/mount-a");
  });
});

describe("isConcreteNamespacePath", () => {
  it("should allow mount root path under personal namespace", () => {
    expect(isConcreteNamespacePath("/personal/4421412")).toBe(true);
  });

  it("should reject namespace root path", () => {
    expect(isConcreteNamespacePath("/personal")).toBe(false);
    expect(isConcreteNamespacePath("/shared")).toBe(false);
  });
});
