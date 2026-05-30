import key from "../const/key";

/** 从本地配置恢复主题并同步到根节点 class，保证首屏样式与用户偏好一致。 */
export function loadTheme() {
  const stored = localStorage.getItem(key.theme);
  const raw = stored ? JSON.parse(stored) as string : "light";
  const theme = raw === "dark" ? "dark" : "light";
  if (theme === "dark") {
    document.documentElement.classList.add("dark");
  } else {
    document.documentElement.classList.remove("dark");
  }
}

/** 切换主题后立即持久化并重放加载流程，确保 DOM 与存储状态始终一致。 */
export function toggleTheme() {
  const isDark = document.documentElement.classList.contains("dark");
  const next = isDark ? "light" : "dark";
  localStorage.setItem(key.theme, JSON.stringify(next));
  loadTheme();
}
