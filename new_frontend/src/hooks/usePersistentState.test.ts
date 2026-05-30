import { describe, expect, it } from "vitest";
import { readStoredJson, writeStoredJson } from "./usePersistentState";

function createStorageMock() {
  const store = new Map<string, string>();
  return {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
  };
}

describe("persistent state helpers", () => {
  it("reads stored json and falls back on invalid payload", () => {
    const storage = createStorageMock();
    storage.setItem("debug-tab", "\"logs\"");
    expect(readStoredJson(storage, "debug-tab", "toast")).toBe("logs");

    storage.setItem("debug-tab", "not-json");
    expect(readStoredJson(storage, "debug-tab", "toast")).toBe("toast");
  });

  it("writes json payloads back to storage", () => {
    const storage = createStorageMock();
    writeStoredJson(storage, "debug-tab", "logs");
    expect(storage.getItem("debug-tab")).toBe("\"logs\"");
  });
});
