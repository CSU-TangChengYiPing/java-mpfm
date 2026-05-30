import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import FileManager from "./file_manager";

type Listener = (event: MessageEvent<string>) => void;

class MockEventSource {
  static latest: MockEventSource | null = null;
  private listeners = new Map<string, Listener[]>();
  onerror: ((this: EventSource, ev: Event) => unknown) | null = null;

  constructor(url: string) {
    void url;
    MockEventSource.latest = this;
  }

  addEventListener(type: string, listener: EventListenerOrEventListenerObject): void {
    const fn = listener as Listener;
    const current = this.listeners.get(type) || [];
    current.push(fn);
    this.listeners.set(type, current);
  }

  close(): void {
    this.listeners.clear();
  }

  emit(type: string, payload: unknown): void {
    const current = this.listeners.get(type) || [];
    const event = { data: JSON.stringify(payload) } as MessageEvent<string>;
    current.forEach((fn) => fn(event));
  }
}

describe("FileManager task stream throttling", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    (globalThis as { window?: unknown }).window = globalThis;
    (globalThis as { EventSource: typeof EventSource }).EventSource = MockEventSource as unknown as typeof EventSource;
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("should coalesce high-frequency progress events into bounded UI updates", async () => {
    const calls: number[] = [];
    const unsubscribe = FileManager.subscribeTaskStream((tasks) => {
      calls.push(tasks.length);
    });
    const source = MockEventSource.latest;
    expect(source).not.toBeNull();
    for (let i = 1; i <= 100; i += 1) {
      source!.emit("task", {
        taskId: "t-1",
        state: "running",
        progress: i % 100,
        transferredBytes: i * 1024,
        totalBytes: 1024 * 1024,
        speedBytesPerSec: 2048,
        etaSeconds: 12,
        updatedAt: `2026-05-27T10:00:${String(i % 60).padStart(2, "0")}Z`,
      });
    }
    await vi.advanceTimersByTimeAsync(1000);
    expect(calls.length).toBeLessThanOrEqual(12);
    unsubscribe();
  });
});
