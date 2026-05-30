import { useContext } from "react";
import { AuthContext } from "./authContextValue";

/** 读取鉴权上下文；若组件脱离 AuthProvider，立即抛错阻断运行时“未鉴权但继续渲染”的隐性风险。 */
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
