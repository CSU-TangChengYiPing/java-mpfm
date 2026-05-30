import { describe, expect, it } from "vitest";
import {
  DEFAULT_FILE_EDIT_MODAL_GEOMETRY,
  fileEditCodeMirrorClassName,
  fileEditEditorShellClassName,
  fileEditLoadingShellClassName,
  fileEditModalClassNames,
  fileEditModalSize,
  fileEditTextareaClassNames,
  resolveEditorLanguageKey,
  resolveFileEditModalGeometry,
  resolveEditorThemeMode,
} from "./file_edit_modal";

describe("FileEditModal textarea style", () => {
  it("uses shared large glass field class", () => {
    expect(fileEditTextareaClassNames.className).toContain("mpfm-large-glass-field");
  });

  it("uses compact modal size and plain surface style", () => {
    expect(fileEditModalSize).toBe("5xl");
    expect(fileEditModalClassNames).toContain("resize");
    expect(fileEditModalClassNames).toContain("w-[min(94vw,1120px)]");
    expect(fileEditEditorShellClassName).toContain("flex");
    expect(fileEditEditorShellClassName).toContain("h-full");
    expect(fileEditEditorShellClassName).toContain("flex-1");
    expect(fileEditEditorShellClassName).toContain("min-w-0");
    expect(fileEditLoadingShellClassName).toContain("h-full");
  });

  it("keeps a stable persisted modal geometry and clamps it into viewport-safe bounds", () => {
    expect(DEFAULT_FILE_EDIT_MODAL_GEOMETRY).toEqual({ width: 1120, height: 920 });
    expect(resolveFileEditModalGeometry({ width: 1600, height: 1200 })).toEqual({ width: 1120, height: 920 });
    expect(resolveFileEditModalGeometry({ width: 200, height: 100 })).toEqual({ width: 760, height: 560 });
  });

  it("keeps CodeMirror on a constrained width box", () => {
    expect(fileEditCodeMirrorClassName).toContain("w-full");
    expect(fileEditCodeMirrorClassName).toContain("min-w-0");
    expect(fileEditCodeMirrorClassName).toContain("overflow-hidden");
  });

  it("resolves language key by file extension", () => {
    expect(resolveEditorLanguageKey("/a/readme.md")).toBe("markdown");
    expect(resolveEditorLanguageKey("/a/config.json")).toBe("json");
    expect(resolveEditorLanguageKey("/a/index.ts")).toBe("typescript");
    expect(resolveEditorLanguageKey("/a/Main.java")).toBe("java");
    expect(resolveEditorLanguageKey("/a/run.sh")).toBe("shell");
    expect(resolveEditorLanguageKey("/a/unknown.abcxyz")).toBe("plain");
  });

  it("resolves editor theme mode from darkness", () => {
    expect(resolveEditorThemeMode(true)).toBe("dark");
    expect(resolveEditorThemeMode(false)).toBe("light");
  });
});
