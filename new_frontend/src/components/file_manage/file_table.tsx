import { Button, ButtonGroup } from "@heroui/button";
import { Checkbox } from "@heroui/checkbox";
import { Spinner } from "@heroui/spinner";
import { type SortDescriptor, TableCell, TableColumn, TableRow } from "@heroui/table";
import clsx from "clsx";
import path from "path-browserify";
import { type ReactNode, useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { FiBookOpen, FiCopy, FiDownload, FiEdit2, FiEye, FiMove, FiTrash2 } from "react-icons/fi";
import { PhotoSlider } from "react-photo-view";
import i18n from "../../i18n";
import FileIcon from "../common/file_icon";
import FileManager, { type FileInfo } from "../../controllers/file_manager";
import { reportTransferSample } from "../../utils/transferRateMeter";
import { supportedPreviewExts } from "./preview_types";
import ImageNameButton, { type PreviewImage, imageExts } from "./image_name_button";
import PaginatedTableShell from "../common/PaginatedTableShell";
import ShadowTooltip from "../common/ShadowTooltip";

type Selection = Set<string | number> | "all";
type Priority = "high" | "low";
const emptyIllustration = "data:image/svg+xml;utf8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='220' height='140' viewBox='0 0 220 140'%3E%3Crect x='20' y='30' width='180' height='90' rx='12' fill='%23e6eefc'/%3E%3Cpath d='M35 50h58l10 12h82v48a10 10 0 0 1-10 10H35a10 10 0 0 1-10-10V60a10 10 0 0 1 10-10Z' fill='%23bfd2f8'/%3E%3Ctext x='110' y='92' text-anchor='middle' font-size='22' fill='%236480c8' font-family='Arial'%3Exxx%3C/text%3E%3C/svg%3E";

const typeByExt: Record<string, string> = {
  ".txt": "text",
  ".md": "Markdown",
  ".json": "JSON",
  ".yaml": "YAML",
  ".yml": "YAML",
  ".xml": "XML",
  ".csv": "CSV",
  ".pdf": "PDF",
  ".doc": "Word",
  ".docx": "Word",
  ".xls": "Excel",
  ".xlsx": "Excel",
  ".ppt": "PPT",
  ".pptx": "PPT",
  ".png": "image",
  ".jpg": "image",
  ".jpeg": "image",
  ".gif": "image",
  ".bmp": "image",
  ".webp": "image",
  ".mp3": "audio",
  ".wav": "audio",
  ".ogg": "audio",
  ".mp4": "video",
  ".webm": "video",
  ".mov": "video",
  ".zip": "archive",
  ".rar": "archive",
  ".7z": "archive",
  ".tar": "archive",
  ".gz": "archive",
  ".exe": "executable",
  ".msi": "installer",
  ".js": "script",
  ".ts": "script",
  ".tsx": "script",
  ".jsx": "script",
  ".go": "source",
  ".py": "source",
  ".java": "source",
  ".c": "source",
  ".cpp": "source",
  ".rs": "source",
  ".sh": "script",
  ".ps1": "script",
};

export function getFileTypeLabel(file: FileInfo): string {
  if (file.isDirectory) return i18n.t("types.directory");
  const ext = path.extname(file.name).toLowerCase();
  if (!ext) return i18n.t("types.file");
  const typeKey = typeByExt[ext];
  return typeKey ? i18n.t(`types.${typeKey}`) : ext.slice(1).toUpperCase();
}

function getVirtualTypeLabel(currentPath: string, file: FileInfo, resolveMountProtocol?: (mountName: string) => string | undefined): string {
  if (!file.isDirectory) return getFileTypeLabel(file);
  const cp = (currentPath || ".").replace(/\\/g, "/").replace(/^\/+/, "");
  const parts = !cp || cp === "." ? [] : cp.split("/").filter(Boolean);
  const name = file.name;
  const formatProtocolLabel = (protocolRaw: string | undefined): string => {
    const protocol = (protocolRaw ?? "").trim().toLowerCase();
    if (protocol === "webdav") return "WebDAV";
    if (protocol === "sftp") return "SFTP";
    if (protocol === "local") return "local";
    return protocolRaw ?? "";
  };
  if (parts.length === 0 && (name === "personal" || name === "shared")) {
    if (name === "personal") return i18n.t("types.spaceEntry");
    return i18n.t("types.spaceShared");
  }
  if (parts.length === 1 && (parts[0] === "personal" || parts[0] === "shared")) {
    const protocol = formatProtocolLabel(resolveMountProtocol?.(name));
    if (protocol) return i18n.t("types.mountEntryWithProtocol", { protocol });
    return i18n.t("types.mountEntry");
  }
  return i18n.t("types.directory");
}

function selectedModeRowClass(selectionMode: boolean, isSelected: boolean): string {
  return clsx(
    "cursor-pointer transition-all duration-200 will-change-transform active:scale-[0.995]",
    selectionMode
      ? isSelected
        ? "bg-primary/10 text-primary dark:bg-primary/20 dark:text-primary-400"
        : "hover:translate-x-[2px] hover:bg-default-100/70 active:bg-primary/10 active:text-primary dark:hover:bg-white/10 dark:active:bg-primary/20 dark:active:text-primary-400"
      : "hover:translate-x-[2px] hover:bg-default-100/70 active:bg-primary/10 active:text-primary dark:hover:bg-white/10 dark:active:bg-primary/20 dark:active:text-primary-400"
  );
}

/** 文件列表主表格：统一处理预览、选择、权限可操作性与行内动作按钮。 */
export default function FileTable({ files, currentPath, loading, sortDescriptor, onSortChange, selectedFiles, onSelectionChange, selectionMode, onDirectoryClick, onEdit, onPreview, onRenameRequest, onMoveRequest, onCopyPath, onDelete, onDownload, canOperatePath, currentIsRoot, resolveDownloadUrl, resolveMountProtocol, extraAction, showDefaultActions = true, wrapperClassName }: { files: FileInfo[]; currentPath: string; loading: boolean; sortDescriptor: SortDescriptor; onSortChange: (descriptor: SortDescriptor) => void; selectedFiles: Selection; onSelectionChange: (selected: Selection) => void; selectionMode: boolean; onDirectoryClick: (dirPath: string) => void; onEdit: (filePath: string) => void; onPreview: (filePath: string) => void; onRenameRequest: (filePath: string) => void; onMoveRequest: (filePath: string) => void; onCopyPath: (filePath: string) => void; onDelete: (filePath: string) => void; onDownload: (filePath: string) => void; canOperatePath: (filePath: string) => boolean; currentIsRoot?: boolean; resolveDownloadUrl: (filePath: string) => string | null; resolveMountProtocol?: (mountName: string) => string | undefined; extraAction?: { icon: ReactNode; title: string; onPress: (filePath: string, file: FileInfo) => void; disabled?: (filePath: string, file: FileInfo, canOperate: boolean, canManage: boolean) => boolean }; showDefaultActions?: boolean; wrapperClassName?: string }) {
  const { t } = useTranslation();
  const [showImage, setShowImage] = useState(false);
  const [previewIndex, setPreviewIndex] = useState(0);
  const [previewImages, setPreviewImages] = useState<PreviewImage[]>([]);
  const [previewSizeMap, setPreviewSizeMap] = useState<Record<string, number>>({});
  const [resolvedImageSrcMap, setResolvedImageSrcMap] = useState<Record<string, string>>({});
  const [imageLoadedBytesMap, setImageLoadedBytesMap] = useState<Record<string, number>>({});
  const inflightRef = useRef<Map<string, Promise<void>>>(new Map());
  const lowPriorityQueueRef = useRef<string[]>([]);
  const lowPriorityWorkerRunningRef = useRef(false);
  const objectUrlMapRef = useRef<Record<string, string>>({});
  const previewTrafficBytesRef = useRef(0);

  const selectedSet: Set<string | number> = selectedFiles === "all" ? new Set(files.map((file) => file.name)) : selectedFiles;
  const allSelected = files.length > 0 && (selectedFiles === "all" || (selectedFiles instanceof Set && selectedFiles.size === files.length));
  const partiallySelected = selectedFiles instanceof Set && selectedFiles.size > 0 && selectedFiles.size < files.length;

  const toggleSelected = (name: string) => {
    if (selectedFiles === "all") {
      onSelectionChange(new Set([name]));
      return;
    }
    const next = new Set<string | number>(selectedSet);
    if (next.has(name)) next.delete(name);
    else next.add(name);
    onSelectionChange(next);
  };

  const toggleSelectAll = () => {
    if (allSelected) {
      onSelectionChange(new Set());
      return;
    }
    onSelectionChange("all");
  };

  const sleep = useCallback((ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms)), []);
  const formatBytes = useCallback((bytes: number): string => {
    if (!Number.isFinite(bytes) || bytes <= 0) return "0 KB";
    if (bytes < 1024 * 1024) {
      const kb = bytes / 1024;
      return `${kb.toFixed(kb >= 100 ? 0 : kb >= 10 ? 1 : 2)} KB`;
    }
    if (bytes < 1024 * 1024 * 1024) {
      const mb = bytes / (1024 * 1024);
      return `${mb.toFixed(mb >= 100 ? 0 : mb >= 10 ? 1 : 2)} MB`;
    }
    const gb = bytes / (1024 * 1024 * 1024);
    return `${gb.toFixed(gb >= 100 ? 0 : gb >= 10 ? 1 : 2)} GB`;
  }, []);

  const ensureLoaded = useCallback(async (src: string, priority: Priority) => {
    if (objectUrlMapRef.current[src]) return;
    const inflight = inflightRef.current.get(src);
    if (inflight) return inflight;

    const task = (async () => {
      setImageLoadedBytesMap((prev) => ({ ...prev, [src]: prev[src] ?? 0 }));
      const resp = await FileManager.fetchBinary(src);
      if (!resp.ok) throw new Error(`image fetch failed: ${resp.status}`);

      if (!resp.body) {
        const blob = await resp.blob();
        previewTrafficBytesRef.current += Math.max(0, blob.size);
        reportTransferSample("download", previewTrafficBytesRef.current);
        const objectURL = window.URL.createObjectURL(blob);
        objectUrlMapRef.current[src] = objectURL;
        setResolvedImageSrcMap((prev) => ({ ...prev, [src]: objectURL }));
        setImageLoadedBytesMap((prev) => ({ ...prev, [src]: blob.size }));
        return;
      }

      const reader = resp.body.getReader();
      const chunks: Uint8Array[] = [];
      let loaded = 0;
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        if (value) {
          chunks.push(value);
          loaded += value.length;
          previewTrafficBytesRef.current += Math.max(0, value.length);
          reportTransferSample("download", previewTrafficBytesRef.current);
          setImageLoadedBytesMap((prev) => ({ ...prev, [src]: loaded }));
          if (priority === "low") await sleep(25);
        }
      }
      const blob = new Blob(chunks as unknown as BlobPart[], { type: resp.headers.get("content-type") ?? "application/octet-stream" });
      const objectURL = window.URL.createObjectURL(blob);
      objectUrlMapRef.current[src] = objectURL;
      setResolvedImageSrcMap((prev) => ({ ...prev, [src]: objectURL }));
      setImageLoadedBytesMap((prev) => ({ ...prev, [src]: blob.size }));
    })()
      .catch(() => {
        setImageLoadedBytesMap((prev) => ({ ...prev, [src]: -1 }));
      })
      .finally(() => {
        inflightRef.current.delete(src);
      });

    inflightRef.current.set(src, task);
    return task;
  }, [sleep]);

  const runLowPriorityQueue = useCallback(() => {
    if (lowPriorityWorkerRunningRef.current) return;
    lowPriorityWorkerRunningRef.current = true;
    void (async () => {
      while (lowPriorityQueueRef.current.length > 0) {
        const src = lowPriorityQueueRef.current.shift();
        if (!src || objectUrlMapRef.current[src] || inflightRef.current.has(src)) continue;
        await sleep(180);
        await ensureLoaded(src, "low");
      }
      lowPriorityWorkerRunningRef.current = false;
    })();
  }, [ensureLoaded, sleep]);

  const handleImagePreview = (name: string) => {
    const imageFiles = files.filter((f) => !f.isDirectory && imageExts.includes(path.extname(f.name).toLowerCase()));
    const images: PreviewImage[] = imageFiles.flatMap((f) => {
      const filePath = f.path || path.join(currentPath, f.name);
      const src = resolveDownloadUrl(filePath);
      if (!src) return [];
      return [{ key: f.name, src, alt: f.name }];
    });
    const nextSizeMap: Record<string, number> = {};
    for (const f of imageFiles) {
      const filePath = f.path || path.join(currentPath, f.name);
      const src = resolveDownloadUrl(filePath);
      if (!src) continue;
      nextSizeMap[src] = Math.max(0, Number(f.size) || 0);
    }
    setPreviewSizeMap(nextSizeMap);
    setPreviewImages(images);
    const index = images.findIndex((image) => image.key === name);
    if (index !== -1) {
      setPreviewIndex(index);
      setShowImage(true);
    }
  };

  const openRow = (file: FileInfo, filePath: string, ext: string) => {
    if (selectionMode) {
      toggleSelected(file.name);
      return;
    }
    if (file.isDirectory) {
      onDirectoryClick(file.path || file.name);
      return;
    }
    if (imageExts.includes(ext)) {
      handleImagePreview(file.name);
      return;
    }
    if (supportedPreviewExts.includes(ext)) {
      onPreview(filePath);
      return;
    }
    onEdit(filePath);
  };

  useEffect(() => {
    if (!showImage || previewImages.length === 0) return;
    const current = previewImages[previewIndex];
    if (!current) return;
    void ensureLoaded(current.src, "high");
    lowPriorityQueueRef.current = previewImages
      .map((img) => img.src)
      .filter((src) => src !== current.src && !objectUrlMapRef.current[src] && !inflightRef.current.has(src));
    runLowPriorityQueue();
  }, [ensureLoaded, previewImages, previewIndex, runLowPriorityQueue, showImage]);

  useEffect(() => {
    return () => {
      for (const src of Object.values(objectUrlMapRef.current)) window.URL.revokeObjectURL(src);
      objectUrlMapRef.current = {};
    };
  }, []);

  return (
    <>
      <PhotoSlider
        images={previewImages.map((img) => ({
          ...img,
          src: resolvedImageSrcMap[img.src],
          render: ({ attrs }) => {
            const resolved = resolvedImageSrcMap[img.src];
            if (resolved) return <img {...attrs} src={resolved} alt={img.alt} />;
            return <div {...attrs} />;
          },
        }))}
        overlayRender={({ index }) => {
          const current = previewImages[index];
          if (!current) return null;
          const resolved = resolvedImageSrcMap[current.src];
          if (resolved) return null;
          const loaded = Math.max(0, imageLoadedBytesMap[current.src] ?? 0);
          const total = Math.max(0, previewSizeMap[current.src] ?? 0);
          const ratio = total > 0 ? Math.min(100, Math.round((loaded / total) * 100)) : 0;
          return (
            <div className="pointer-events-none fixed inset-0 z-[30]">
              <div className="absolute inset-0 flex items-center justify-center">
                <div className="w-[320px] max-w-[80vw]">
                  <div className="mb-3 flex justify-center scale-200"><Spinner size="sm" color="default" /></div>
                  <div className="mt-5 flex items-center justify-center text-xs text-white/85">
                    <span>{formatBytes(loaded)} / {formatBytes(total)}</span>
                  </div>
                </div>
              </div>
              <div className="absolute bottom-0 left-0 right-0 h-1.5 overflow-hidden bg-white/25">
                <div className="h-full bg-primary transition-all duration-150" style={{ width: `${ratio}%` }} />
              </div>
            </div>
          );
        }}
        visible={showImage}
        onClose={() => setShowImage(false)}
        index={previewIndex}
        onIndexChange={setPreviewIndex}
      />
      <PaginatedTableShell
        key={currentPath}
        ariaLabel={t("fileManager.tableLabel")}
        wrapperClassName={wrapperClassName}
        rows={files.map((file) => ({ ...file, key: file.name }))}
        loading={loading}
        loadingContent={<div className="flex h-full items-center justify-center"><Spinner /></div>}
        sortDescriptor={sortDescriptor}
        onSortChange={onSortChange}
        totalLabel={(total) => t("fileManager.totalLabel", { count: total })}
        enablePageSizeInput
        defaultPageSize={20}
        pageSizeLabel={t("fileManager.pageSizeLabel")}
        emptyContent={
          <div className="flex h-52 w-full flex-col items-center justify-center gap-2 text-default-500">
            <img src={emptyIllustration} alt="xxx" className="h-28 w-auto opacity-90" />
            <div className="text-sm font-medium">{t("fileManager.emptyTitle")}</div>
            <div className="text-xs opacity-85">{t("fileManager.emptyDesc")}</div>
          </div>
        }
        header={
          <>
          <TableColumn key="select" width={56} className={selectionMode ? "" : "hidden"}>
            <div className="flex items-center justify-center">
              <Checkbox isSelected={allSelected} isIndeterminate={partiallySelected} onValueChange={toggleSelectAll} />
            </div>
          </TableColumn>
          <TableColumn key="name" allowsSorting>{t("fileManager.name")}</TableColumn>
          <TableColumn key="type" allowsSorting className="hidden md:table-cell">{t("fileManager.type")}</TableColumn>
          <TableColumn key="size" allowsSorting className="hidden md:table-cell">{t("fileManager.size")}</TableColumn>
          <TableColumn key="mtime" allowsSorting className="hidden md:table-cell">{t("fileManager.mtime")}</TableColumn>
          <TableColumn key="perm" className="hidden md:table-cell">{t("fileManager.permission")}</TableColumn>
          <TableColumn key="actions">{t("fileManager.actions")}</TableColumn>
          </>
        }
        renderRow={(file) => {
            const filePath = file.path || path.join(currentPath, file.name);
            const ext = path.extname(file.name).toLowerCase();
            const canOperate = canOperatePath(filePath);
            const isVirtualEntry = !canOperate;
            const visible = currentIsRoot ? true : file.visible !== false;
            const readable = currentIsRoot ? true : file.readable !== false;
            const writable = currentIsRoot ? true : file.writable === true;
            const canRead = readable;
            const canManage = writable;
            const effectivePermLabels = isVirtualEntry
              ? [file.visibility ? file.visibility : "N/A"]
              : [];
            const isSelected = selectedFiles === "all" || (selectedFiles instanceof Set && selectedFiles.has(file.name));
            return (
              <TableRow key={file.name} className={selectedModeRowClass(selectionMode, isSelected)}>
                <TableCell className={selectionMode ? "" : "hidden"}>
                  <div className="flex items-center justify-center" onClick={(e) => e.stopPropagation()}>
                    <Checkbox isSelected={selectedSet.has(file.name)} onValueChange={() => toggleSelected(file.name)} />
                  </div>
                </TableCell>
                <TableCell onClick={() => openRow(file, filePath, ext)} className="cursor-pointer">
                  {imageExts.includes(ext) ? <ImageNameButton name={file.name} onPreview={() => openRow(file, filePath, ext)} /> : <div className="flex items-center gap-2"><FileIcon name={file.name} isDirectory={file.isDirectory} /><span>{file.name}</span></div>}
                </TableCell>
                <TableCell onClick={() => openRow(file, filePath, ext)} className="hidden cursor-pointer md:table-cell">{getVirtualTypeLabel(currentPath, file, resolveMountProtocol)}</TableCell>
                <TableCell onClick={() => openRow(file, filePath, ext)} className="hidden cursor-pointer md:table-cell">{Number.isNaN(file.size) || file.isDirectory ? "-" : formatBytes(file.size)}</TableCell>
                <TableCell onClick={() => openRow(file, filePath, ext)} className="hidden cursor-pointer md:table-cell">{new Date(file.mtime).toLocaleString()}</TableCell>
                <TableCell className="hidden md:table-cell">
                  <div className="flex items-center gap-2">
                    {!isVirtualEntry && visible && (
                      <ShadowTooltip content="visible">
                        <span className="inline-flex h-6 w-6 items-center justify-center rounded-full border border-default-300 text-default-500"><FiEye /></span>
                      </ShadowTooltip>
                    )}
                    {!isVirtualEntry && readable && (
                      <ShadowTooltip content="read">
                        <span className="inline-flex h-6 w-6 items-center justify-center rounded-full border border-default-300 text-default-500"><FiBookOpen /></span>
                      </ShadowTooltip>
                    )}
                    {!isVirtualEntry && writable && (
                      <ShadowTooltip content="write">
                        <span className="inline-flex h-6 w-6 items-center justify-center rounded-full border border-default-300 text-default-500"><FiEdit2 /></span>
                      </ShadowTooltip>
                    )}
                    {isVirtualEntry && effectivePermLabels.length > 0 && (
                      <span className="rounded border border-default-300 px-2 py-0.5 text-xs text-default-600">{effectivePermLabels.join(",")}</span>
                    )}
                  </div>
                </TableCell>
                <TableCell onClick={(e) => e.stopPropagation()}>
                  <ButtonGroup radius="sm" size="sm" variant="light">
                    {showDefaultActions && <ShadowTooltip content={t("fileManager.renameTitle")}><Button aria-label={t("fileManager.renameTitle")} isIconOnly className="text-default-500 hover:text-primary" isDisabled={!canOperate || !canManage} onPress={() => onRenameRequest(filePath)}><FiEdit2 /></Button></ShadowTooltip>}
                    {showDefaultActions && <ShadowTooltip content={t("fileManager.moveTitle")}><Button aria-label={t("fileManager.moveTitle")} isIconOnly className="text-default-500 hover:text-primary" isDisabled={!canOperate || !canManage} onPress={() => onMoveRequest(filePath)}><FiMove /></Button></ShadowTooltip>}
                    {extraAction && (
                      <ShadowTooltip content={extraAction.title}><Button
                          aria-label={extraAction.title}
                          isIconOnly
                          className={file.share_override ? "text-warning-600 hover:text-warning-500 dark:text-warning-300" : "text-default-500 hover:text-primary"}
                          isDisabled={extraAction.disabled ? extraAction.disabled(filePath, file, canOperate, canManage) : (!canOperate || !canManage)}
                          onPress={() => extraAction.onPress(filePath, file)}
                        >
                          {extraAction.icon}
                        </Button></ShadowTooltip>
                    )}
                    {showDefaultActions && <ShadowTooltip content={t("fileManager.pathCopied")}><Button aria-label={t("fileManager.pathCopied")} isIconOnly className="text-default-500 hover:text-primary" onPress={() => onCopyPath(filePath)}><FiCopy /></Button></ShadowTooltip>}
                    {showDefaultActions && <ShadowTooltip content={t("common.download")}><Button aria-label={t("common.download")} isIconOnly className="text-default-500 hover:text-primary" isDisabled={!canOperate || file.isDirectory || !canRead} onPress={() => onDownload(filePath)}><FiDownload /></Button></ShadowTooltip>}
                    {showDefaultActions && <ShadowTooltip content={t("common.delete")}><Button aria-label={t("common.delete")} isIconOnly color="danger" className="text-danger hover:bg-danger/10" isDisabled={!canOperate || !canManage} onPress={() => onDelete(filePath)}><FiTrash2 /></Button></ShadowTooltip>}
                  </ButtonGroup>
                </TableCell>
              </TableRow>
            );
          }}
      />
    </>
  );
}
