import { describe, expect, it } from "vitest";
import { SEARCH_INPUT_DEBOUNCE_MS, shouldTriggerSearch } from "./profileSearchModel";

describe("profileSearchModel", () => {
  it("throttles within debounce window", () => {
    expect(shouldTriggerSearch(1000, 1000 + SEARCH_INPUT_DEBOUNCE_MS - 1)).toBe(false);
  });

  it("triggers after debounce window", () => {
    expect(shouldTriggerSearch(1000, 1000 + SEARCH_INPUT_DEBOUNCE_MS)).toBe(true);
  });
});

