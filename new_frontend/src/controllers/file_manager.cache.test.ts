import { describe, expect, it, vi } from "vitest";
import FileManager from "./file_manager";

function jsonResponse(body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

describe("FileManager read requests", () => {
  it("读取类接口应显式禁用浏览器缓存，避免旧版本回写导致版本冲突", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ items: [] }))
      .mockResolvedValueOnce(jsonResponse({ entry: { path: "/personal/demo/a.txt", name: "a.txt", type: "file", sizeBytes: 3, mtime: "2026-05-30T00:00:00Z", etag: "\"etag-1\"", version: "1:3" }, content: "abc" }, { ETag: "\"etag-1\"" }))
      .mockResolvedValueOnce(jsonResponse({ entry: { path: "/personal/demo/a.txt", name: "a.txt", type: "file", sizeBytes: 3, mtime: "2026-05-30T00:00:00Z", etag: "\"etag-1\"", version: "1:3" } }, { ETag: "\"etag-1\"" }));

    await FileManager.listFiles("/personal/demo");
    await FileManager.readFile("/personal/demo/a.txt");
    await FileManager.statFile("/personal/demo/a.txt");

    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ cache: "no-store" });
    expect(fetchMock.mock.calls[1]?.[1]).toMatchObject({ cache: "no-store" });
    expect(fetchMock.mock.calls[2]?.[1]).toMatchObject({ cache: "no-store" });
  });

  it("写入时应直接使用调用方传入的版本值", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");
    fetchMock
      .mockResolvedValueOnce(jsonResponse({
        entry: { path: "/personal/demo/a.txt", name: "a.txt", type: "file", sizeBytes: 3, mtime: "2026-05-30T00:00:00Z", etag: "\"etag-read\"", version: "1:3" },
        content: "abc",
      }, { ETag: "\"etag-read\"" }))
      .mockResolvedValueOnce(jsonResponse({
        path: "/personal/demo/a.txt",
        name: "a.txt",
        type: "file",
        sizeBytes: 4,
        mtime: "2026-05-30T00:01:00Z",
        etag: "\"etag-write\"",
        version: "2:4",
      }));

    await FileManager.readFileWithMeta("/personal/demo/a.txt");
    await FileManager.writeFile("/personal/demo/a.txt", "abcd", "\"etag-edit\"");

    const writeInit = fetchMock.mock.calls[1]?.[1];
    expect(new Headers(writeInit?.headers).get("If-Match")).toBe("\"etag-edit\"");
    expect(writeInit?.body).toBe(JSON.stringify({ virtualPath: "/personal/demo/a.txt", content: "abcd" }));
  });
});
