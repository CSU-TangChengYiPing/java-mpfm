import path from "path-browserify";

/**
 * 判断当前路径是否已经进入具体挂载目录。
 * 规则：仅 `/personal/{mount}`、`/shared/{mount}` 及其子路径可执行写操作。
 */
export function isConcreteNamespacePath(filePath: string): boolean {
  const cleaned = (filePath || ".")
    .replace(/\\/g, "/")
    .replace(/^\.\//, "")
    .replace(/^\/+/, "");
  if (!cleaned || cleaned === ".") return false;
  const segs = cleaned.split("/").filter(Boolean);
  if (segs.length < 2) return false;
  return segs[0] === "personal" || segs[0] === "shared";
}

/**
 * 目录点击后的目标路径构造：绝对虚拟路径保持原样，相对路径拼接到当前目录。
 */
export function buildNextPathFromDirectoryClick(currentPath: string, dirPath: string): string {
  const normalizeForCompare = (raw: string) =>
    (raw || "")
      .replace(/\\/g, "/")
      .replace(/^\.\//, "")
      .replace(/^\/+/, "")
      .replace(/\/+$/, "");
  const normalizedDirPath = dirPath.startsWith("./") ? dirPath.slice(1) : dirPath;
  const currentComparable = normalizeForCompare(currentPath);
  const targetComparable = normalizeForCompare(normalizedDirPath);
  const isAlreadyAbsoluteVirtual =
    normalizedDirPath.startsWith("/") ||
    (currentComparable !== "" && targetComparable.startsWith(`${currentComparable}/`));
  return isAlreadyAbsoluteVirtual
    ? (normalizedDirPath.startsWith("/") ? normalizedDirPath : `/${targetComparable}`)
    : path.join(currentPath, normalizedDirPath);
}

