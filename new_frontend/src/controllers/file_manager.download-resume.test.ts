import { describe, expect, it, vi } from "vitest";
import FileManager from "./file_manager";

describe("FileManager download resume", () => {
  it("恢复本地下载任务时应复用原始 key 对应的路径与挂载信息，避免生成新任务", async () => {
    const taskId = "download-local-4421412%3A%3Adocs%2Fa.txt";
    const localDownloadTasks = (FileManager as unknown as { localDownloadTasks: Map<string, unknown> }).localDownloadTasks;
    localDownloadTasks.set(taskId, {
      taskId,
      target: "/personal/4421412/docs/a.txt",
      status: "PAUSED",
    });
    const downloadSpy = vi
      .spyOn(FileManager, "downloadWithProgress")
      .mockResolvedValue({ taskId, action: "download", status: "RUNNING", progress: 42 } as never);

    await FileManager.resumeDownloadByTaskId(taskId);

    expect(downloadSpy).toHaveBeenCalledWith("docs/a.txt", "4421412");
  });

  it("暂停任务时在内存映射缺失场景应可由 taskId 反解并成功中断", () => {
    const taskId = "download-local-4421412%3A%3Adocs%2Fb.txt";
    const localDownloadTasks = (FileManager as unknown as { localDownloadTasks: Map<string, unknown> }).localDownloadTasks;
    const activeControllers = (FileManager as unknown as { activeDownloadControllers: Map<string, AbortController> }).activeDownloadControllers;
    const keyMap = (FileManager as unknown as { localDownloadTaskKeyById: Map<string, string> }).localDownloadTaskKeyById;
    localDownloadTasks.set(taskId, { taskId, status: "RUNNING", target: "/personal/4421412/docs/b.txt" });
    keyMap.delete(taskId);
    const controller = new AbortController();
    const abortSpy = vi.spyOn(controller, "abort");
    activeControllers.set("4421412::docs/b.txt", controller);

    const ok = FileManager.pauseDownloadByTaskId(taskId);

    expect(ok).toBe(true);
    expect(abortSpy).toHaveBeenCalledOnce();
  });
});
