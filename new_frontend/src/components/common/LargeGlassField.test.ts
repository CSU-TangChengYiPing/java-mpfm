import { describe, expect, it } from "vitest";
import { resolveDraftValue } from "./LargeGlassField";

describe("LargeGlassField helpers", () => {
  it("normalizes draft values to strings", () => {
    expect(resolveDraftValue("abc")).toBe("abc");
    expect(resolveDraftValue(123)).toBe("123");
    expect(resolveDraftValue(null)).toBe("");
    expect(resolveDraftValue(undefined)).toBe("");
  });
});
