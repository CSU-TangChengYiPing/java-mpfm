import FileIcon from "../common/file_icon";

export interface PreviewImage { key: string; src: string; alt: string }
export const imageExts = [".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".svg"];

export default function ImageNameButton({ name, onPreview }: { name: string; onPreview: () => void }) {
  return (
    <button type="button" className="flex w-full min-w-0 items-center gap-2 overflow-hidden text-left" onClick={onPreview}>
      <FileIcon name={name} isDirectory={false} />
      <span className="block min-w-0 max-w-full truncate" title={name}>
        {name}
      </span>
    </button>
  );
}
