import { describe, expect, it } from "vitest";
import { hasMoreFromCursor, mergeUniqueUsers } from "./useInfiniteUsers";

describe("useInfiniteUsers helpers", () => {
  it("deduplicates by user_id when merging", () => {
    const existing = [
      { user_id: "a", nickname: "A", is_root: false, avatar_url: "" },
      { user_id: "b", nickname: "B", is_root: false, avatar_url: "" },
    ];
    const incoming = [
      { user_id: "b", nickname: "B2", is_root: false, avatar_url: "" },
      { user_id: "c", nickname: "C", is_root: false, avatar_url: "" },
    ];
    const out = mergeUniqueUsers(existing, incoming);
    expect(out.map((it) => it.user_id)).toEqual(["a", "b", "c"]);
  });

  it("detects hasMore from next_cursor", () => {
    expect(hasMoreFromCursor("20")).toBe(true);
    expect(hasMoreFromCursor("")).toBe(false);
    expect(hasMoreFromCursor("   ")).toBe(false);
  });
});

