export type ErrorPresentationLevel = "field" | "form" | "toast";

export type ShareErrorResolved = {
  level: ErrorPresentationLevel;
  message: string;
};

/** 从统一错误文案中提取 `[CODE]` 片段，失败时回退 UNKNOWN，供错误呈现层分流。 */
export function parseErrorCode(error: unknown): string {
  if (!(error instanceof Error)) return "UNKNOWN";
  const match = error.message.match(/\[([A-Z_]+)\]/);
  return match?.[1] || "UNKNOWN";
}

/** 共享域错误分层映射：可定位输入问题走字段级，权限/状态问题走表单级，其余走全局 toast。 */
export function mapShareErrorPresentation(code: string): ErrorPresentationLevel {
  if (code === "VALIDATION_ERROR") return "field";
  if (code === "PERMISSION_DENIED") return "form";
  if (code === "LINK_EXPIRED" || code === "LINK_REVOKED" || code === "LINK_EXHAUSTED" || code === "ROLE_EXPIRED" || code === "ROLE_DISABLED") {
    return "form";
  }
  return "toast";
}

/** 统一解析共享域错误：从异常中提取错误码并给出呈现层级，缺省时回退调用方传入文案。 */
export function resolveShareError(error: unknown, fallbackMessage: string): ShareErrorResolved {
  const message = error instanceof Error ? error.message : fallbackMessage;
  const level = mapShareErrorPresentation(parseErrorCode(error));
  return { level, message };
}
