/** 浏览器自动填充不会总是触发受控输入更新；当状态仍为空时以 DOM 实值兜底同步，避免误判“未输入”。 */
export function syncAutofillStateValue(current: string, domValue: string | null | undefined): string {
  if (current.trim().length > 0) return current;
  const next = domValue?.trim() ?? "";
  return next.length > 0 ? next : current;
}
