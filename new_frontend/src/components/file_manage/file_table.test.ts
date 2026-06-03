import { describe, expect, it } from "vitest";
import { fileTableActionsColumnClassName, fileTableNameColumnClassName, resolveFilePermissionText, resolveFileTableColumnClassNames } from "./file_table";

describe("file_table column widths", () => {
  it("uses percentage widths on mobile and keeps desktop constraints", () => {
    expect(fileTableNameColumnClassName).toContain("w-[40%]");
    expect(fileTableNameColumnClassName).toContain("md:w-[34%]");
    expect(fileTableNameColumnClassName).toContain("md:min-w-[240px]");
    expect(fileTableActionsColumnClassName).toContain("w-[60%]");
    expect(fileTableActionsColumnClassName).toContain("md:w-[160px]");
    expect(fileTableActionsColumnClassName).toContain("md:min-w-[160px]");
  });

  it("switches to a visible permission column for share pages", () => {
    const shareColumnClassNames = resolveFileTableColumnClassNames(true);
    expect(shareColumnClassNames.permission).toContain("w-[28%]");
    expect(shareColumnClassNames.permission).toContain("md:w-[144px]");
    expect(shareColumnClassNames.actions).toContain("w-[32%]");
  });
});

describe("file table detail permission text", () => {
  it("returns visible/read/write labels for root", () => {
    expect(resolveFilePermissionText({ name: "a.txt", isDirectory: false, size: 1, mtime: new Date().toISOString() }, true)).toBe("visible, read, write");
  });

  it("returns N/A when normal user has no permissions", () => {
    expect(resolveFilePermissionText({ name: "a.txt", isDirectory: false, size: 1, mtime: new Date().toISOString(), visible: false, readable: false, writable: false }, false)).toBe("N/A");
  });
});
