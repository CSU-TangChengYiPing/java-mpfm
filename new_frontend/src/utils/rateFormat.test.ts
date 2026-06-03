import { describe, expect, it } from "vitest";
import { decomposeRateBps, formatRateBps, parseRateValueToBps } from "./rateFormat";

describe("rateFormat", () => {
  it("should format small values in B/s", () => {
    expect(formatRateBps(512)).toBe("512 B/s");
  });

  it("should auto convert large values to KB/s and MB/s", () => {
    expect(formatRateBps(123456789)).toBe("117.7 MB/s");
    expect(formatRateBps(1536)).toBe("1.5 KB/s");
  });

  it("should decompose bytes per second into editable value and unit", () => {
    expect(decomposeRateBps(123456789, ["KB", "MB"])).toEqual({ value: "117.74", unit: "MB" });
    expect(decomposeRateBps(512 * 1024, ["KB", "MB"])).toEqual({ value: "512.00", unit: "KB" });
  });

  it("should parse unit value back to bps", () => {
    expect(parseRateValueToBps("1.5", "KB")).toBe(1536);
    expect(parseRateValueToBps("0.5", "MB")).toBe(524288);
  });
});
