import FileIcon from "../common/file_icon";

export interface PreviewImage { key: string; src: string; alt: string }
export const imageExts = [".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".svg"];

export default function ImageNameButton({ name, onPreview }: { name: string; onPreview: () => void }) {
  return (
    <button type="button" className="flex w-full items-center gap-2 text-left" onClick={onPreview}>
      <FileIcon name={name} isDirectory={false} />
      <span>{name}</span>
    </button>
  );
}
