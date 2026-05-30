import { describe, expect, it } from "vitest";
import { createRollingEtaEstimator, estimateRemainingSeconds, formatHmsCountdown } from "./transfer_eta";

describe("transfer eta helpers", () => {
  it("should return null countdown for non-positive speed", () => {
    expect(estimateRemainingSeconds(1024, 0)).toBeNull();
  });

  it("should estimate remaining seconds with integer floor", () => {
    expect(estimateRemainingSeconds(5000, 1200)).toBe(4);
  });

  it("should format countdown to hh:mm:ss", () => {
    expect(formatHmsCountdown(3661)).toBe("01:01:01");
  });

  it("should return null when rolling window is shorter than 5 seconds", () => {
    const estimate = createRollingEtaEstimator(5, 30);
    expect(estimate(1000, 10000, 1000)).toBeNull();
    expect(estimate(3000, 10000, 5000)).toBeNull();
  });

  it("should estimate eta by recent 5-30s rolling samples", () => {
    const estimate = createRollingEtaEstimator(5, 30);
    estimate(1000, 10000, 0);
    estimate(3000, 10000, 6000);
    const remaining = estimate(6000, 10000, 12000);
    expect(remaining).toBe(8);
  });
});
