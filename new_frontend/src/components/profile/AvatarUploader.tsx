import { Button } from "@heroui/button";
import { ModalBody, ModalContent, ModalFooter, ModalHeader } from "@heroui/modal";
import { useLocalStorage } from "@uidotdev/usehooks";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import Cropper, { type Area } from "react-easy-crop";
import i18n from "../../i18n";
import key from "../../const/key";
import ProfileController from "../../controllers/profile";
import UserAvatar from "../common/UserAvatar";
import { validateAvatarFile } from "./avatarValidation";
import BlurModal from "../common/BlurModal";

type Props = {
  userID: string;
  avatarURL?: string;
  onUploaded?: (avatarURL: string) => void;
};

/** 头像上传器：负责裁剪、校验、上传与头像版本信号刷新。 */
export default function AvatarUploader({ userID, avatarURL, onUploaded }: Props) {
  const { t } = useTranslation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [isOpen, setIsOpen] = useState(false);
  const [sourceFile, setSourceFile] = useState<File | null>(null);
  const [sourceURL, setSourceURL] = useState("");
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [cropArea, setCropArea] = useState<Area | null>(null);
  const [status, setStatus] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [avatarVersion, setAvatarVersion] = useLocalStorage<string>(key.profileAvatarVersion, "");
  const menuRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const previewURL = useMemo(() => {
    const base = avatarURL || "";
    if (!base) return "";
    if (!avatarVersion) return base;
    const sep = base.includes("?") ? "&" : "?";
    return `${base}${sep}v=${encodeURIComponent(avatarVersion)}`;
  }, [avatarURL, avatarVersion]);

  useEffect(() => {
    if (!menuOpen) return;
    const onPointerDown = (event: MouseEvent) => {
      if (!menuRef.current) return;
      if (!menuRef.current.contains(event.target as Node)) setMenuOpen(false);
    };
    window.addEventListener("mousedown", onPointerDown);
    return () => window.removeEventListener("mousedown", onPointerDown);
  }, [menuOpen]);

  function onPickFile(file: File | undefined) {
    const error = validateAvatarFile(file);
    if (error) {
      setStatus(error);
      return;
    }
    if (!file) return;
    setStatus("");
    setSourceFile(file);
    setSourceURL(URL.createObjectURL(file));
    setCrop({ x: 0, y: 0 });
    setZoom(1);
    setCropArea(null);
    setMenuOpen(false);
    setIsOpen(true);
  }

  async function submitCropped() {
    if (!sourceFile || !sourceURL || !cropArea) return;
    setSubmitting(true);
    setStatus("");
    try {
      const blob = await getCroppedBlob(sourceURL, cropArea);
      const ext = sourceFile.type.includes("png") ? "png" : sourceFile.type.includes("webp") ? "webp" : "jpg";
      const croppedFile = new File([blob], `avatar.${ext}`, { type: blob.type || sourceFile.type || "image/jpeg" });
      const resp = await ProfileController.uploadAvatar(croppedFile);
      const version = String(Date.now());
      setAvatarVersion(version);
      window.dispatchEvent(new CustomEvent("mpfm:avatar-updated", { detail: { version, userID } }));
      setStatus(t("avatar.uploadSuccess"));
      onUploaded?.(resp.avatar_url);
      setIsOpen(false);
    } catch (err) {
      setStatus(err instanceof Error ? err.message : t("avatar.uploadFailed"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex items-start gap-3">
        <div ref={menuRef} className="relative">
          <button
            type="button"
            className="rounded-full transition-transform hover:scale-[1.02] focus:outline-none focus:ring-2 focus:ring-primary/40"
            onClick={() => setMenuOpen((prev) => !prev)}
            aria-label={t("avatar.menuAriaLabel")}
          >
            <UserAvatar userId={userID} avatarUrl={previewURL} size={64} />
          </button>
          {menuOpen && (
            <div className="absolute left-0 top-[78px] z-40 w-44 rounded-xl border border-white/35 bg-white/92 p-2 shadow-[0_18px_42px_rgba(15,23,42,0.22),0_2px_10px_rgba(15,23,42,0.12)] backdrop-blur-md dark:border-white/15 dark:bg-black/78 dark:shadow-[0_20px_48px_rgba(0,0,0,0.5),0_2px_12px_rgba(0,0,0,0.35)]">
              <div className="absolute -top-[7px] left-6 h-[14px] w-[14px] rotate-45 border-l border-t border-white/35 bg-white/92 shadow-[-2px_-2px_6px_rgba(15,23,42,0.08)] dark:border-white/15 dark:bg-black/78 dark:shadow-[-2px_-2px_6px_rgba(0,0,0,0.25)]" />
              <a
                href={previewURL}
                target="_blank"
                rel="noreferrer"
                className="block rounded-lg px-3 py-2 text-sm text-default-700 transition-colors hover:bg-primary/10 dark:text-default-200 dark:hover:bg-white/10"
                onClick={() => setMenuOpen(false)}
              >
                {t("avatar.downloadOrigin")}
              </a>
              <button
                type="button"
                className="block w-full rounded-lg px-3 py-2 text-left text-sm text-default-700 transition-colors hover:bg-primary/10 dark:text-default-200 dark:hover:bg-white/10"
                onClick={() => fileInputRef.current?.click()}
              >
                {t("avatar.uploadAction")}
              </button>
            </div>
          )}
        </div>
        <div className="pt-1 text-xs text-default-500">{t("avatar.helperText")}</div>
        <input
          ref={fileInputRef}
          type="file"
          className="hidden"
          accept="image/png,image/jpeg,image/webp"
          onChange={(e) => void onPickFile(e.target.files?.[0])}
        />
      </div>
      {status && <p className="text-xs text-default-500">{status}</p>}

      <BlurModal
        isOpen={isOpen}
        onClose={() => setIsOpen(false)}
        size="2xl"
        radius="sm"
      >
        <ModalContent>
          <ModalHeader>{t("avatar.cropTitle")}</ModalHeader>
          <ModalBody>
            <div className="relative h-[360px] overflow-hidden rounded-xl border border-white/20 bg-black/40">
              {sourceURL && (
                <Cropper
                  image={sourceURL}
                  crop={crop}
                  zoom={zoom}
                  aspect={1}
                  cropShape="round"
                  showGrid={false}
                  onCropChange={setCrop}
                  onCropComplete={(_, pixels) => setCropArea(pixels)}
                  onZoomChange={setZoom}
                />
              )}
            </div>
            <label className="space-y-2 text-sm text-default-600 dark:text-default-300">
              <span>{t("avatar.zoomLabel", { value: zoom.toFixed(1) })}</span>
              <input
                type="range"
                min={1}
                max={3}
                step={0.1}
                value={zoom}
                onChange={(e) => setZoom(Number(e.target.value))}
                className="w-full"
              />
            </label>
          </ModalBody>
          <ModalFooter>
            <Button variant="flat" onPress={() => setIsOpen(false)}>
              {t("common.cancel")}
            </Button>
            <Button color="primary" onPress={() => void submitCropped()} isLoading={submitting}>
              {t("avatar.uploadAction")}
            </Button>
          </ModalFooter>
        </ModalContent>
      </BlurModal>
    </div>
  );
}

async function getCroppedBlob(imageSrc: string, area: Area): Promise<Blob> {
  const image = await loadImage(imageSrc);
  const canvas = document.createElement("canvas");
  const size = Math.min(area.width, area.height);
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("crop context unavailable");
  ctx.drawImage(image, area.x, area.y, size, size, 0, 0, size, size);
  return await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        reject(new Error(i18n.t("avatar.cropFailed")));
        return;
      }
      resolve(blob);
    }, "image/jpeg", 0.92);
  });
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error(i18n.t("avatar.imageLoadFailed")));
    image.src = src;
  });
}
