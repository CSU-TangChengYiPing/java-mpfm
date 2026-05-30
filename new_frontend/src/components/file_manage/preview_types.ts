import path from "path-browserify";

export const videoExts = [".mp4", ".webm", ".mov"];
export const audioExts = [".mp3", ".wav", ".ogg"];
export const imageExts = [".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"];
export const mediaExts = [...videoExts, ...audioExts];
export const supportedPreviewExts = [...mediaExts, ...imageExts];

export type MediaKind = "video" | "audio";

export interface MediaPreviewItem {
  key: string;
  name: string;
  src: string;
  kind: MediaKind;
}

export function getExt(filePath: string): string {
  return path.extname(filePath).toLowerCase();
}

export function getMediaKind(filePath: string): MediaKind | null {
  const ext = getExt(filePath);
  if (videoExts.includes(ext)) return "video";
  if (audioExts.includes(ext)) return "audio";
  return null;
}

