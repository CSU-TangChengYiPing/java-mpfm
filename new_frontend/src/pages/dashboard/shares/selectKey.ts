type SelectKeys = "all" | Set<string | number>;

/**
 * 解析单选 Select 的 key：兼容 HeroUI 返回 "all" 的场景，避免把字符串按字符拆分成非法 key。
 */
export function pickSingleSelectKey(keys: SelectKeys): string {
  if (keys === "all") return "";
  const first = keys.values().next().value;
  return first === undefined || first === null ? "" : String(first);
}

