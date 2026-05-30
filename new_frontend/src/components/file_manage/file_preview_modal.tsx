import { PhotoSlider } from "react-photo-view";
import { useTranslation } from "react-i18next";
import type { MediaPreviewItem } from "./preview_types";
import MediaPreviewModal from "./media_preview_modal";

interface Props {
  isOpen: boolean;
  imageFilePath: string;
  imageSrc: string;
  mediaItems: MediaPreviewItem[];
  mediaIndex: number;
  onMediaIndexChange: (next: number) => void;
  onClose: () => void;
}

export default function FilePreviewModal({ isOpen, imageFilePath, imageSrc, mediaItems, mediaIndex, onMediaIndexChange, onClose }: Props) {
  const { t } = useTranslation();
  if (mediaItems.length > 0) {
    return <MediaPreviewModal isOpen={isOpen} items={mediaItems} index={mediaIndex} onIndexChange={onMediaIndexChange} onClose={onClose} />;
  }

  if (imageFilePath && imageSrc) {
    return <PhotoSlider visible={isOpen} onClose={onClose} index={0} images={[{ key: imageFilePath, src: imageSrc }]} bannerVisible />;
  }

  return (
    <dialog open={isOpen} onClose={onClose} className="bg-black/85 p-4">
      <div className="flex flex-col items-center justify-center h-full">
        <div className="p-6 text-center text-white/85">{t("fileManager.unsupportedPreview")}</div>
        <button onClick={onClose} className="rounded bg-gray-700 px-4 py-2 text-white hover:bg-gray-600">
          {t("common.close")}
        </button>
      </div>
    </dialog>
  );
}
