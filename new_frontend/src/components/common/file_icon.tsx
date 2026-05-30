import { FiFile, FiFolder, FiImage } from "react-icons/fi";

/** 文件图标组件：根据文件名和是否为目录显示图标。 */
export default function FileIcon({ name, isDirectory }: { name: string; isDirectory: boolean }) {
  if (isDirectory) return <FiFolder className="text-warning" />;
  const lower = name.toLowerCase();
  if (/\.(png|jpg|jpeg|gif|bmp|webp|svg)$/.test(lower)) return <FiImage className="text-primary" />;
  if (lower.endsWith(".md") || lower.endsWith(".txt")) return <FiFile className="text-success" />;
  return <FiFile className="text-default-500" />;
}
