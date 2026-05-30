import { useEffect, useState } from "react";

export type StorageLike = Pick<Storage, "getItem" | "setItem">;

function resolveInitialState<T>(fallback: T | (() => T)): T {
  return typeof fallback === "function" ? (fallback as () => T)() : fallback;
}

/** 从本地存储恢复状态；解析失败时回退到默认值，避免脏数据把页面卡死在异常分支。 */
export function readStoredJson<T>(storage: StorageLike | undefined, key: string, fallback: T | (() => T)): T {
  const defaultValue = resolveInitialState(fallback);
  if (!storage) return defaultValue;
  const raw = storage.getItem(key);
  if (!raw) return defaultValue;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return defaultValue;
  }
}

/** 写回本地存储；序列化失败时静默降级，避免非关键 UI 状态影响主流程。 */
export function writeStoredJson<T>(storage: StorageLike | undefined, key: string, value: T): void {
  if (!storage) return;
  try {
    storage.setItem(key, JSON.stringify(value));
  } catch {
    // localStorage 满额或被禁用时，不阻断页面运行
  }
}

/**
 * 将非关键 UI 状态落到本地存储，适合 tab、筛选器、面板展开状态等“下次还想接着看”的场景。
 * 传入的 key 应保持稳定，避免不同页面/模块互相污染。
 */
export function usePersistentState<T>(key: string, fallback: T | (() => T), storage: StorageLike | undefined = typeof window !== "undefined" ? window.localStorage : undefined) {
  const [state, setState] = useState<T>(() => readStoredJson(storage, key, fallback));

  useEffect(() => {
    writeStoredJson(storage, key, state);
  }, [key, state, storage]);

  return [state, setState] as const;
}
