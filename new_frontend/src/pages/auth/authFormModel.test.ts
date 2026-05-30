import { describe, expect, it } from "vitest";
import { syncAutofillStateValue } from "./authFormModel";

describe("authFormModel", () => {
  it("syncs autofill value into controlled state", () => {
    expect(syncAutofillStateValue("", "alice")).toBe("alice");
    expect(syncAutofillStateValue("bob", "alice")).toBe("bob");
    expect(syncAutofillStateValue("", "")).toBe("");
  });
});
