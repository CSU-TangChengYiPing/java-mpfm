import { describe, expect, it } from "vitest";
import { resolveLocale } from "./i18n";

describe("resolveLocale", () => {
  it("keeps zh/en as-is", () => {
    expect(resolveLocale("zh")).toBe("zh");
    expect(resolveLocale("en")).toBe("en");
  });

  it("falls back to zh on invalid input", () => {
    expect(resolveLocale(null)).toBe("zh");
    expect(resolveLocale(undefined)).toBe("zh");
    expect(resolveLocale("jp")).toBe("zh");
  });
});
