import { useEffect, useRef, useState } from "react";
import { FiChevronLeft, FiChevronRight, FiX } from "react-icons/fi";
import { Spinner } from "@heroui/spinner";
import { useTranslation } from "react-i18next";
import type { MediaPreviewItem } from "./preview_types";

interface Props {
  isOpen: boolean;
  items: MediaPreviewItem[];
  index: number;
  onIndexChange: (next: number) => void;
  onClose: () => void;
}

/** 多媒体预览弹层：统一图片/音视频翻页预览并在关闭时释放视频网络占用。 */
export default function MediaPreviewModal({ isOpen, items, index, onIndexChange, onClose }: Props) {
  const { t } = useTranslation();
  const hasItems = items.length > 0;
  const current = hasItems ? items[Math.max(0, Math.min(index, items.length - 1))] : undefined;
  const canPrev = hasItems && index > 0;
  const canNext = hasItems && index < items.length - 1;
  const [isBuffering, setIsBuffering] = useState(false);
  const videoRef = useRef<HTMLVideoElement | null>(null);

  const stopCurrentVideoTransfer = () => {
    const video = videoRef.current;
    if (!video) return;
    video.pause();
    video.removeAttribute("src");
    video.load();
  };

  const closeAndStop = () => {
    stopCurrentVideoTransfer();
    onClose();
  };

  const goPrev = () => {
    if (!canPrev) return;
    stopCurrentVideoTransfer();
    onIndexChange(index - 1);
  };

  const goNext = () => {
    if (!canNext) return;
    stopCurrentVideoTransfer();
    onIndexChange(index + 1);
  };

  useEffect(() => {
    const mountedVideo = videoRef.current;
    return () => {
      if (!mountedVideo) return;
      mountedVideo.pause();
      mountedVideo.removeAttribute("src");
      mountedVideo.load();
    };
  }, [current?.key, isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    const onKeyDown = (evt: KeyboardEvent) => {
      if (evt.key === "Escape") {
        stopCurrentVideoTransfer();
        onClose();
      }
      if (evt.key === "ArrowLeft" && canPrev) {
        stopCurrentVideoTransfer();
        onIndexChange(index - 1);
      }
      if (evt.key === "ArrowRight" && canNext) {
        stopCurrentVideoTransfer();
        onIndexChange(index + 1);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [isOpen, canPrev, canNext, index, onClose, onIndexChange]);

  if (!isOpen || !current) return null;

  return (
    <div className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/88 p-4 backdrop-blur-sm" onClick={closeAndStop}>
      <div className="relative w-full max-w-[1200px]" onClick={(e) => e.stopPropagation()}>
        <button onClick={closeAndStop} aria-label={t("common.close")} className="absolute right-2 top-2 z-20 rounded-full bg-black/55 p-2 text-white/90 hover:bg-black/70">
          <FiX size={20} />
        </button>

        {items.length > 1 && (
          <>
            <button onClick={goPrev} disabled={!canPrev} aria-label={t("common.previous")} className="absolute left-2 top-1/2 z-20 -translate-y-1/2 rounded-full bg-black/55 p-2 text-white/90 disabled:opacity-35 hover:bg-black/70">
              <FiChevronLeft size={20} />
            </button>
            <button onClick={goNext} disabled={!canNext} aria-label={t("common.next")} className="absolute right-2 top-1/2 z-20 -translate-y-1/2 rounded-full bg-black/55 p-2 text-white/90 disabled:opacity-35 hover:bg-black/70">
              <FiChevronRight size={20} />
            </button>
          </>
        )}

        <div className="overflow-hidden rounded-md border border-white/20 bg-[#0b0b0b] shadow-2xl">
          {current.kind === "video" ? (
            <div className="aspect-[16/9] w-full bg-black">
              <div className="relative h-full w-full">
                <video
                  ref={videoRef}
                  key={current.key}
                  src={current.src}
                  controls
                  autoPlay
                  preload="metadata"
                  className="h-full w-full object-contain"
                  onLoadStart={() => setIsBuffering(true)}
                  onWaiting={() => setIsBuffering(true)}
                  onCanPlay={() => setIsBuffering(false)}
                  onPlaying={() => setIsBuffering(false)}
                  onEmptied={() => setIsBuffering(false)}
                />
                {isBuffering && (
                  <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/35">
                    <div className="rounded-sm bg-black/70 px-4 py-3">
                      <Spinner size="sm" color="default" />
                    </div>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="flex min-h-[220px] items-center justify-center p-8">
              <audio key={current.key} src={current.src} controls autoPlay preload="metadata" className="w-full max-w-[840px]" />
            </div>
          )}
          <div className="flex items-center justify-between border-t border-white/10 px-4 py-2 text-xs text-white/75">
            <span className="truncate">{current.name}</span>
            <span>{index + 1} / {items.length}</span>
          </div>
        </div>
      </div>
    </div>
  );
}
