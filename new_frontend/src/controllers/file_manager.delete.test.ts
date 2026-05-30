import { describe, expect, it, vi } from "vitest";
import FileManager from "./file_manager";

function mockJsonResponse(body: unknown, ok = true, status = 200): Response {
  return {
    ok,
    status,
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as Response;
}

describe("FileManager delete task cache sync", () => {
  it("should remove deleted task from subsequent task list", async () => {
    (globalThis as { window?: unknown }).window = globalThis;
    const fetchMock = vi.spyOn(globalThis, "fetch");
    fetchMock
      .mockResolvedValueOnce(mockJsonResponse([{ id: "t1", name: "batch_upload", creator: "a123", taskGroup: "upload", state: "succeeded", status: "success", progress: 100, startTime: "2026-01-01T00:00:00Z", endTime: "2026-01-01T00:00:01Z", totalBytes: 1 }]))
      .mockResolvedValueOnce(mockJsonResponse([]))
      .mockResolvedValueOnce(mockJsonResponse({ taskId: "t1", status: "success" }))
      .mockResolvedValueOnce(mockJsonResponse([]))
      .mockResolvedValueOnce(mockJsonResponse([]));

    const before = await FileManager.listTasks();
    expect(before.map((item) => item.taskId)).toContain("t1");

    await FileManager.deleteTask("t1");
    const after = await FileManager.listTasks();
    expect(after.map((item) => item.taskId)).not.toContain("t1");
  });
});
