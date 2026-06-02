import path from "path-browserify";

export function normalizeMoveBrowsePath(rawPath: string): string {
  const cleaned = (rawPath || ".").trim().replace(/\\/g, "/").replace(/\/{2,}/g, "/");
  if (!cleaned || cleaned === ".") return ".";
  if (cleaned === "/") return "/";
  if (/^[A-Za-z]:$/.test(cleaned)) return cleaned;
  if (/^[A-Za-z]:[\\/]/.test(cleaned)) return cleaned.replace(/\\/g, "/").replace(/\/{2,}/g, "/");
  return cleaned.startsWith("/") ? cleaned : `/${cleaned}`.replace(/\/{2,}/g, "/");
}

export function isMoveBrowseVirtualPrefixPath(rawPath: string): boolean {
  const normalized = normalizeMoveBrowsePath(rawPath);
  return /^\/\.(?:\/)?(personal|shared)\//.test(normalized);
}

export function getMoveBrowseVirtualMountRootPath(rawPath: string): string | null {
  const normalized = normalizeMoveBrowsePath(rawPath);
  const segments = normalized.split("/").filter(Boolean);
  if (segments.length < 2) return null;
  if (segments[0] === "." && (segments[1] !== "personal" && segments[1] !== "shared")) return null;
  if (segments[0] === ".") {
    if (segments.length < 3) return null;
    return `/${segments.slice(0, 3).join("/")}`;
  }
  if (segments[0] === "personal" || segments[0] === "shared") {
    return `/${segments.slice(0, 2).join("/")}`;
  }
  return null;
}

export function isMoveBrowseWithinVirtualMount(candidatePath: string, currentPath: string): boolean {
  const currentRoot = getMoveBrowseVirtualMountRootPath(currentPath);
  if (!currentRoot) return true;
  const normalizedCandidate = normalizeMoveBrowsePath(candidatePath);
  return normalizedCandidate === currentRoot || normalizedCandidate.startsWith(`${currentRoot}/`);
}

export function resolveMoveBrowseParentPath(currentPath: string): string {
  const normalized = normalizeMoveBrowsePath(currentPath);
  if (normalized === "." || normalized === "/") return normalized;
  if (/^[A-Za-z]:$/.test(normalized)) return normalized;
  const virtualRoot = getMoveBrowseVirtualMountRootPath(normalized);
  if (virtualRoot) {
    const segments = normalized.split("/").filter(Boolean);
    if (segments.length <= virtualRoot.split("/").filter(Boolean).length) return normalized;
    return `/${segments.slice(0, -1).join("/")}`;
  }
  const parent = path.dirname(normalized);
  if (!parent || parent === "." || parent === normalized) return normalized;
  return normalizeMoveBrowsePath(parent);
}

export function resolveMoveBrowseChildPath(currentPath: string, childName: string): string {
  const normalizedCurrent = normalizeMoveBrowsePath(currentPath);
  const safeChild = (childName || "").trim().replace(/\\/g, "/").replace(/^\/+/, "");
  if (!safeChild) return normalizedCurrent;
  if (normalizedCurrent === ".") return normalizeMoveBrowsePath(`/${safeChild}`);
  if (normalizedCurrent === "/") return normalizeMoveBrowsePath(`/${safeChild}`);
  if (/^[A-Za-z]:$/.test(normalizedCurrent)) return normalizeMoveBrowsePath(`${normalizedCurrent}\\${safeChild}`);
  return normalizeMoveBrowsePath(path.join(normalizedCurrent, safeChild));
}

export function splitMoveBrowseTrail(currentPath: string): string[] {
  const normalized = normalizeMoveBrowsePath(currentPath);
  if (normalized === "." || normalized === "/") return normalized === "." ? [] : ["/"];
  return normalized.replace(/\\/g, "/").split("/").filter(Boolean);
}
