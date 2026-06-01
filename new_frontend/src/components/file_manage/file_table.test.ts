import { describe, expect, it } from "vitest";
import { resolveFilePermissionText } from "./file_table";

describe("file table detail permission text", () => {
  it("returns visible/read/write labels for root", () => {
    expect(resolveFilePermissionText({ name: "a.txt", isDirectory: false, size: 1, mtime: new Date().toISOString() }, true)).toBe("visible, read, write");
  });

  it("returns N/A when normal user has no permissions", () => {
    expect(resolveFilePermissionText({ name: "a.txt", isDirectory: false, size: 1, mtime: new Date().toISOString(), visible: false, readable: false, writable: false }, false)).toBe("N/A");
  });
});
