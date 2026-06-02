import { describe, expect, it } from "vitest";
import type { FileInfo } from "../../controllers/file_manager";
import { buildMoveArboristNodes, patchMoveArboristChildren } from "./move_arborist.model";

describe("buildMoveArboristNodes", () => {
  it("maps directory entries to tree nodes", () => {
    const dirs: FileInfo[] = [{ name: "docs", isDirectory: true, size: 0, mtime: "2026-06-02T00:00:00.000Z" }];
    expect(buildMoveArboristNodes("/personal/demo", dirs)[0]?.path).toBe("/personal/demo/docs");
  });
});

describe("patchMoveArboristChildren", () => {
  it("backfills children for a loaded branch", () => {
    const nodes = buildMoveArboristNodes("/personal/demo", [
      { name: "docs", isDirectory: true, size: 0, mtime: "2026-06-02T00:00:00.000Z" },
    ]);
    const patched = patchMoveArboristChildren(nodes, "/personal/demo/docs", [
      { id: "/personal/demo/docs/reports", name: "reports", path: "/personal/demo/docs/reports", hasChildren: false, loaded: false, children: [] },
    ]);
    expect(patched[0]?.children?.[0]?.name).toBe("reports");
  });
});
