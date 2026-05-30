import { describe, expect, it } from "vitest";
import { createSingleFlightGate } from "./save_guard";

describe("createSingleFlightGate", () => {
  it("同一时刻只允许一次保存进入", () => {
    const gate = createSingleFlightGate();

    expect(gate.enter()).toBe(true);
    expect(gate.enter()).toBe(false);

    gate.leave();
    expect(gate.enter()).toBe(true);
  });
});
