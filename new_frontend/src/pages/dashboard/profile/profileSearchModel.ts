export const SEARCH_INPUT_DEBOUNCE_MS = 250;

/** 搜索节流判定：只有超过防抖窗口才允许触发请求，避免输入期高频调用后端搜索接口。 */
export function shouldTriggerSearch(lastChangeAt: number, now: number, debounceMs = SEARCH_INPUT_DEBOUNCE_MS): boolean {
  return now-lastChangeAt >= debounceMs;
}
