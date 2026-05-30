import { describe, expect, it } from "vitest";
import { resolveTotalLabel } from "./PaginatedTableShell";

describe("PaginatedTableShell resolveTotalLabel", () => {
  it("supports function label", () => {
    expect(resolveTotalLabel((total) => `共 ${total} 项`, 3)).toBe("共 3 项");
  });

  it("supports plain label node", () => {
    expect(resolveTotalLabel("TOTAL", 3)).toBe("TOTAL");
  });

  it("falls back to numeric text", () => {
    expect(resolveTotalLabel(undefined, 3)).toBe("3");
  });
});
