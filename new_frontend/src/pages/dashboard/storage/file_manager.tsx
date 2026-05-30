import { BreadcrumbItem, Breadcrumbs } from "@heroui/breadcrumbs";
import { Button } from "@heroui/button";
import { Input } from "@heroui/input";
import type { SortDescriptor } from "@heroui/table";
import clsx from "clsx";
import path from "path-browserify";
import { useCallback, useEffect, useRef, useState } from "react";
import { useDropzone } from "react-dropzone";
import toast from "react-hot-toast";
import { FiCheck, FiCheckSquare, FiDownload, FiMove, FiPlus, FiUpload, FiX } from "react-icons/fi";
import { MdRefresh } from "react-icons/md";
import { TbTrash } from "react-icons/tb";
import { TiArrowBack } from "react-icons/ti";
import { useLocation, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import CreateFileModal from "../../../components/file_manage/create_file_modal";
import FileEditModal from "../../../components/file_manage/file_edit_modal";
import FilePreviewModal from "../../../components/file_manage/file_preview_modal";
import FileTable, { getFileTypeLabel } from "../../../components/file_manage/file_table";
import MoveModal from "../../../components/file_manage/move_modal";
import ShadowTooltip from "../../../components/common/ShadowTooltip";
import type { MediaPreviewItem } from "../../../components/file_manage/preview_types";
import RenameModal from "../../../components/file_manage/rename_modal";
import { getMediaKind } from "../../../components/file_manage/preview_types";
import { EDIT_MAX_BYTES, NON_EDITABLE_EXTS } from "../../../const/file_edit";
import key from "../../../const/key";
import FileManager, { APIError, type FileInfo, type UploadCapability } from "../../../controllers/file_manager";
import MountsController, { type MountInfo } from "../../../controllers/mounts";
import { useAuth } from "../../../hooks/useAuth";
import { AUTH_KEY } from "../../../hooks/authStorage";
import { createRollingEtaEstimator, formatHmsCountdown } from "./transfer_eta";
import { createSingleFlightGate } from "./save_guard";
import { buildNextPathFromDirectoryClick, isConcreteNamespacePath } from "./path_navigation";

type Selection = Set<string | number> | "all";
type EditingFileState = {
  path: string;
  content: string;
  etag?: string;
  version?: string;
};

/** 文件管理主页面：编排挂载解析、路径导航、文件操作与批量任务入口。 */
export default function FileManagerPage() {
  const { user } = useAuth();
  const { t } = useTranslation();
  const [files, setFiles] = useState<FileInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [sortDescriptor, setSortDescriptor] = useState<SortDescriptor>({ column: "name", direction: "ascending" });
  const location = useLocation();
  const navigate = useNavigate();
  let currentPath = decodeURIComponent(location.hash.slice(1) || ".");
  currentPath = currentPath.replace(/\\/g, "/").replace(/^\/\.\//, "/").replace(/^\.\/+/, "");
  if (currentPath && currentPath !== "." && !currentPath.startsWith("/")) {
    currentPath = `/${currentPath}`;
  }
  if (/^\/[A-Z]:$/i.test(currentPath)) currentPath = currentPath.slice(1);

  const [editingFile, setEditingFile] = useState<EditingFileState | null>(null);
  const [isEditLoading, setIsEditLoading] = useState(false);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [newFileName, setNewFileName] = useState("");
  const [fileType, setFileType] = useState<"file" | "directory">("file");
  const [selectedFiles, setSelectedFiles] = useState<Selection>(new Set());
  const [selectionMode, setSelectionMode] = useState(false);
  const [isRenameModalOpen, setIsRenameModalOpen] = useState(false);
  const [isMoveModalOpen, setIsMoveModalOpen] = useState(false);
  const [renamingFile, setRenamingFile] = useState("");
  const [moveTargetPath, setMoveTargetPath] = useState("");
  const [jumpPath, setJumpPath] = useState("");
  const [isPathEditing, setIsPathEditing] = useState(false);
  const [isSavingFile, setIsSavingFile] = useState(false);
  const [previewImageFile, setPreviewImageFile] = useState("");
  const [previewImageSrc, setPreviewImageSrc] = useState("");
  const [previewMediaItems, setPreviewMediaItems] = useState<MediaPreviewItem[]>([]);
  const [previewMediaIndex, setPreviewMediaIndex] = useState(0);
  const [isDraggingFile, setIsDraggingFile] = useState(false);
  const [mounts, setMounts] = useState<MountInfo[]>([]);
  const [uploadCapability, setUploadCapability] = useState<UploadCapability | null>(null);
  const [currentDirWritable, setCurrentDirWritable] = useState(false);
  const lastValidPathRef = useRef<string>(currentPath);
  const saveGateRef = useRef(createSingleFlightGate());
  const dragDepthRef = useRef(0);
  const editRequestSeqRef = useRef(0);

  const toSharedAlias = useCallback((mountName: string, ownerUser?: string): string => {
    const safeMountName = (mountName || "-").trim() || "-";
    const safeOwnerUser = (ownerUser || "-").trim() || "-";
    return `${safeMountName}---${safeOwnerUser}`;
  }, []);

  const normalizeApiVirtualPath = useCallback((rawPath: string): string => {
    const safeDecodeOnce = (value: string): string => {
      try {
        return decodeURIComponent(value);
      } catch {
        return value;
      }
    };
    let normalized = safeDecodeOnce((rawPath || ".").trim()).replace(/\\/g, "/");
    normalized = normalized.replace(/^\/?\.\//, "/");
    normalized = normalized.replace(/\/{2,}/g, "/");
    if (!normalized || normalized === ".") return ".";
    if (normalized.startsWith("/./")) normalized = normalized.slice(2);
    return normalized.startsWith("/") ? normalized : `/${normalized}`;
  }, []);

  const normalizePathWithMountName = useCallback((rawPath: string): string => {
    const normalized = normalizeApiVirtualPath(rawPath);
    if (normalized === ".") return normalized;
    const segs = normalized.replace(/^\/+/, "").split("/").filter(Boolean);
    if (segs.length < 2) return normalized;
    const scope = segs[0];
    if (scope !== "personal" && scope !== "shared") return normalized;
    if (scope === "shared") return normalized;
    const mountKey = segs[1];
    const hit = mounts.find((m) => m.id === mountKey || m.name === mountKey);
    if (!hit) return normalized;
    segs[1] = hit.name;
    return `/${segs.join("/")}`;
  }, [mounts, normalizeApiVirtualPath]);

  const resolveMountProtocol = useCallback((mountName: string): string | undefined => {
    const normalizedName = mountName.replace(/\s*\([^)]*\)\s*$/, "");
    const hit = mounts.find((m) => m.name === normalizedName);
    return hit?.protocol;
  }, [mounts]);

  // 规范化虚拟路径，将路径转换为数组，每个元素为路径段
  const normalizeVirtual = (p: string): string[] => {
    const cleaned = (p || ".")
      .replace(/\\/g, "/")
      .replace(/^\.\//, "")
      .replace(/^\/+/, "");
    if (!cleaned || cleaned === ".") return [];
    return cleaned.split("/").filter(Boolean);
  };

  // 仅解析虚拟目录节点；业务请求统一直传 virtualPath。
  const resolveVirtualEntries = useCallback((targetPath: string): { virtualEntries?: FileInfo[] } => {
    const ts = new Date().toISOString();
    const virtualDirEntry = (name: string, disabled?: boolean, virtualPath?: string): FileInfo => ({
      name,
      path: virtualPath,
      isDirectory: true,
      size: 0,
      mtime: ts,
      disabled,
    });
    const segs = normalizeVirtual(targetPath);
    if (segs.length === 0) {
      return { virtualEntries: [virtualDirEntry("personal"), virtualDirEntry("shared")] };
    }
    const root = segs[0];
    const isRoot = !!user?.is_root;
    const currentUsername = (user?.username ?? "").trim();
    const canSeeDisabledMount = (m: MountInfo) => isRoot || !!m.can_manage;
    const isEnabledForView = (m: MountInfo) => m.enabled || canSeeDisabledMount(m);
    const isOwnedPersonalMount = (m: MountInfo) => {
      const owner = (m.owner_user ?? "").trim();
      if (!owner || !currentUsername) return !isRoot;
      return owner === currentUsername;
    };

    if (root === "personal") {
      if (segs.length === 1) {
        return {
          virtualEntries: mounts
            .filter((m) => isEnabledForView(m) && isOwnedPersonalMount(m))
            .map((m) => virtualDirEntry(m.name, !m.enabled, `/personal/${m.name}`)),
        };
      }
      return {};
    }

    if (root === "shared") {
      const sharedVisible = (m: MountInfo) =>
        isEnabledForView(m) &&
        !m.can_manage &&
        !!m.shared_enabled;
      if (segs.length === 1) {
        return {
          virtualEntries: mounts
            .filter((m) => sharedVisible(m))
            .map((m) => virtualDirEntry(`${m.name}(${m.owner_user || "-"})`, !m.enabled, `/shared/${toSharedAlias(m.name, m.owner_user)}`)),
        };
      }
      return {};
    }
    return {};
  }, [mounts, toSharedAlias, user?.is_root]);

  // 对文件列表进行排序
  const sortFiles = (input: FileInfo[], descriptor: SortDescriptor) => {
    return [...input].sort((a, b) => {
      if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1;
      const direction = descriptor.direction === "ascending" ? 1 : -1;
      switch (descriptor.column) {
        case "name":
          return direction * a.name.localeCompare(b.name);
        case "type": {
          const aType = getFileTypeLabel(a);
          const bType = getFileTypeLabel(b);
          return direction * aType.localeCompare(bType);
        }
        case "size":
          return direction * ((a.size || 0) - (b.size || 0));
        case "mtime":
          return direction * (new Date(a.mtime).getTime() - new Date(b.mtime).getTime());
        default:
          return 0;
      }
    });
  };

  // 加载文件列表
  const loadFiles = useCallback(async () => {
    setLoading(true);
    try {
      const resolved = resolveVirtualEntries(currentPath);
      if (resolved.virtualEntries) {
        setFiles(sortFiles(resolved.virtualEntries, sortDescriptor));
        lastValidPathRef.current = currentPath;
        setLoading(false);
        return;
      }
      const fileList = await FileManager.listFiles(currentPath);
      setFiles(sortFiles(fileList, sortDescriptor));
      lastValidPathRef.current = currentPath;
    } catch {
      const rollbackPath = lastValidPathRef.current;
      if (rollbackPath && rollbackPath !== currentPath) {
        toast.error(t("fileManager.pathRollback"));
        navigate(`/app/files#${encodeURIComponent(rollbackPath)}`, { replace: true });
      } else {
        toast.error(t("fileManager.loadFailed"));
        setFiles([]);
      }
    }
    setLoading(false);
  }, [currentPath, navigate, resolveVirtualEntries, sortDescriptor, t]);

  useEffect(() => {
    const id = window.setTimeout(async () => {
      try {
        const list = await MountsController.list();
        setMounts(list);
      } catch {
        setMounts([]);
      }
    }, 0);
    return () => window.clearTimeout(id);
  }, []);

  useEffect(() => { const id = window.setTimeout(() => { void loadFiles(); }, 0); return () => window.clearTimeout(id); }, [loadFiles]);

  useEffect(() => {
    let cancelled = false;
    if (!isConcreteNamespacePath(currentPath)) {
      setCurrentDirWritable(false);
      return () => { cancelled = true; };
    }
    void (async () => {
      try {
        const stat = await FileManager.statFile(currentPath);
        if (!cancelled) setCurrentDirWritable(!!stat?.writable);
      } catch {
        if (!cancelled) setCurrentDirWritable(false);
      }
    })();
    return () => { cancelled = true; };
  }, [currentPath]);

  useEffect(() => {
    if (mounts.length === 0) return;
    const normalized = normalizePathWithMountName(currentPath);
    if (normalized !== currentPath) {
      navigate(`/app/files#${encodeURIComponent(normalized)}`, { replace: true });
    }
  }, [currentPath, mounts, navigate, normalizePathWithMountName]);

  // 处理排序变化
  const handleSortChange = (descriptor: SortDescriptor) => {
    setSortDescriptor(descriptor);
    setFiles((prev) => sortFiles(prev, descriptor));
  };

  // 处理目录点击事件
  const handleDirectoryClick = (dirPath: string) => {
    if (dirPath === "..") {
      const parentPath = /^[A-Z]:$/i.test(currentPath) ? "." : path.dirname(currentPath);
      const nextParent = parentPath === currentPath ? "." : parentPath;
      if (nextParent === currentPath) {
        setLoading(false);
        return;
      }
      setLoading(true);
      setFiles([]);
      setSelectedFiles(new Set());
      navigate(`/app/files#${encodeURIComponent(nextParent)}`);
      return;
    }
    const nextPath = buildNextPathFromDirectoryClick(currentPath, dirPath);
    setLoading(true);
    setFiles([]);
    setSelectedFiles(new Set());
    navigate(`/app/files#${encodeURIComponent(nextPath)}`);
  };

  // 解析跳转路径，返回规范化的路径
  const resolveJumpPath = (input: string): string => {
    const raw = input.trim();
    if (!raw) return currentPath;
    if (raw === ".") return currentPath;
    if (/^[A-Za-z]:[\\/]/.test(raw) || /^[A-Za-z]:$/.test(raw) || raw.startsWith("/")) {
      return raw.replace(/\\/g, "/");
    }
    return path.normalize(path.join(currentPath, raw)).replace(/\\/g, "/");
  };

  // 确认跳转路径，导航到目标路径
  const confirmJumpPath = () => {
    const target = resolveJumpPath(jumpPath);
    setIsPathEditing(false);
    navigate(`/app/files#${encodeURIComponent(target)}`);
  };

  // 处理编辑文件点击事件
  const handleEdit = async (filePath: string) => {
    const fileName = path.basename(filePath);
    const ext = path.extname(fileName).toLowerCase();
    const file = files.find((f) => f.name === fileName);
    if (NON_EDITABLE_EXTS.has(ext)) {
      toast.error(t("fileManager.unsupportedEditType", { ext: ext || "unknown" }));
      return;
    }
    if (file && !file.isDirectory && file.size > EDIT_MAX_BYTES) {
      toast.error(t("fileManager.fileTooLarge", { max: EDIT_MAX_BYTES }));
      return;
    }
    const requestSeq = editRequestSeqRef.current + 1;
    editRequestSeqRef.current = requestSeq;
    setIsEditLoading(true);
    setEditingFile({
      path: filePath,
      content: "",
      etag: file?.etag,
      version: file?.version,
    });
    try {
      const result = await FileManager.readFileWithMeta(filePath);
      if (editRequestSeqRef.current !== requestSeq) return;
      const selectedEtag = result.entry?.etag ?? file?.etag;
      const selectedVersion = result.entry?.version ?? file?.version;
      setEditingFile({
        path: filePath,
        content: result.content,
        etag: selectedEtag,
        version: selectedVersion,
      });
    } catch {
      if (editRequestSeqRef.current !== requestSeq) return;
      setEditingFile(null);
      toast.error(t("fileManager.openFailed"));
    } finally {
      if (editRequestSeqRef.current === requestSeq) setIsEditLoading(false);
    }
  };

  // 处理保存文件点击事件
  const handleSave = async (content: string) => {
    if (!editingFile) return;
    if (!saveGateRef.current.enter()) return;
    setIsSavingFile(true);
    try {
      const ifMatch = editingFile.etag || editingFile.version;
      if (!ifMatch) {
        const stat = await FileManager.statFile(editingFile.path);
        if (!stat?.etag && !stat?.version) {
          toast.error(t("authErrors.codes.VERSION_CONFLICT"));
          return;
        }
        await FileManager.writeFile(editingFile.path, content, stat?.etag || stat?.version);
      } else {
        await FileManager.writeFile(editingFile.path, content, ifMatch);
      }
      toast.success(t("common.saveSuccess"));
      setEditingFile(null);
      await loadFiles();
    } catch (error) {
      if (error instanceof APIError && error.code === "VERSION_CONFLICT") {
        await loadFiles();
        toast.error(t("authErrors.codes.VERSION_CONFLICT"));
        return;
      }
      toast.error(t("common.saveFailed"));
    } finally {
      setIsSavingFile(false);
      saveGateRef.current.leave();
    }
  };

  // 处理删除文件点击事件
  const handleDelete = async (filePath: string) => {
    if (!window.confirm(t("fileManager.confirmDelete", { path: filePath }))) return;
    try {
      await FileManager.delete(filePath);
      toast.success(t("common.deleteSuccess"));
      await loadFiles();
    } catch {
      toast.error(t("common.deleteFailed"));
    }
  };

  // 处理创建文件点击事件
  const handleCreate = async () => {
    if (!currentDirWritable) {
      toast.error(t("fileManager.virtualDenied"));
      return;
    }
    const trimmedName = newFileName.trim();
    if (!trimmedName) {
      toast.error(t("fileManager.nameRequired"));
      return;
    }
    if (/[\\/]/.test(trimmedName)) {
      toast.error(t("fileManager.invalidName"));
      return;
    }
    const exists = files.find((f) => f.name === trimmedName);
    if (fileType === "file" && exists?.isDirectory) {
      toast.error(t("fileManager.dirExists"));
      return;
    }
    const newPath = path.join(currentPath, trimmedName);
    try {
      if (fileType === "directory") await FileManager.createDirectory(newPath);
      else await FileManager.createFile(newPath);
      toast.success(t("common.createSuccess"));
      setIsCreateModalOpen(false);
      setNewFileName("");
      await loadFiles();
    } catch (error) {
      const msg = (error as Error)?.message || t("common.createFailed");
      if (msg.toLowerCase().includes("is a directory")) {
        toast.error(t("fileManager.createTargetDir"));
        return;
      }
      toast.error(msg);
    }
  };

  // 处理批量删除文件点击事件
  const handleBatchDelete = async () => {
    const selectedArray = selectedFiles instanceof Set ? Array.from(selectedFiles) : files.map((f) => f.name);
    if (selectedArray.length === 0) return;
    const valid = selectedArray.filter((k) => canOperatePath(path.join(currentPath, k.toString())));
    if (valid.length === 0) {
      denyVirtualOp();
      return;
    }
    if (!window.confirm(t("fileManager.confirmBatchDelete", { count: selectedArray.length }))) return;
    try {
      for (const k of valid) {
        const full = path.join(currentPath, k.toString());
        await FileManager.delete(full);
      }
      toast.success(t("fileManager.batchDeleteSuccess"));
      setSelectedFiles(new Set());
      await loadFiles();
    } catch {
      toast.error(t("fileManager.batchDeleteFailed"));
    }
  };

  // 处理重命名文件点击事件
  const handleRename = async () => {
    if (!renamingFile || !newFileName) return;
    try {
      await FileManager.rename(renamingFile, path.join(path.dirname(renamingFile), newFileName));
      toast.success(t("fileManager.renameSuccess"));
      setIsRenameModalOpen(false);
      setRenamingFile("");
      setNewFileName("");
      await loadFiles();
    } catch {
      toast.error(t("fileManager.renameFailed"));
    }
  };

  // 处理移动文件点击事件
  const handleMove = async (sourceName: string) => {
    if (!moveTargetPath) return;
    try {
      const sourcePath = sourceName;
      await FileManager.move(sourcePath, path.join(moveTargetPath, path.basename(sourcePath)));
      toast.success(t("fileManager.moveSuccess"));
      setIsMoveModalOpen(false);
      setMoveTargetPath("");
      await loadFiles();
    } catch {
      toast.error(t("fileManager.moveFailed"));
    }
  };

  // 处理批量移动文件点击事件
  const handleBatchMove = async () => {
    if (!moveTargetPath) return;
    const selectedArray = selectedFiles instanceof Set ? Array.from(selectedFiles) : files.map((f) => f.name);
    if (selectedArray.length === 0) return;
    const valid = selectedArray.filter((name) => canOperatePath(path.join(currentPath, name.toString())));
    if (valid.length === 0) {
      denyVirtualOp();
      return;
    }
    try {
      for (const name of valid) {
        await FileManager.move(path.join(currentPath, name.toString()), path.join(moveTargetPath, name.toString()));
      }
      toast.success(t("fileManager.batchMoveSuccess"));
      setIsMoveModalOpen(false);
      setMoveTargetPath("");
      setSelectedFiles(new Set());
      await loadFiles();
    } catch {
      toast.error(t("fileManager.batchMoveFailed"));
    }
  };

  // 处理复制路径点击事件
  const handleCopyPath = (filePath: string) => {
    void navigator.clipboard.writeText(filePath);
    toast.success(t("fileManager.pathCopied"));
  };

  // 处理移动文件点击事件
  const handleMoveClick = (filePath: string) => {
    setRenamingFile(filePath);
    setMoveTargetPath("");
    setIsMoveModalOpen(true);
  };

  // 处理下载文件点击事件
  const handleDownload = (filePath: string) => {
    if (!canOperatePath(filePath)) return;
    const toastId = `download-${encodeURIComponent(filePath)}`;
    const name = path.basename(filePath);
    const estimateEta = createRollingEtaEstimator(1, 30);
    toast.custom(renderDownloadToast(name, 0, 1, "0 B", "--:--:--"), { id: toastId, duration: Number.POSITIVE_INFINITY });
    void FileManager.downloadWithProgress(filePath, undefined, (loaded, total, speedBps) => {
      const eta = formatHmsCountdown(estimateEta(loaded, total));
      toast.custom(renderDownloadToast(name, loaded, total, formatBytes(speedBps), eta), { id: toastId, duration: Number.POSITIVE_INFINITY });
    })
      .then(() => {
        toast.success(t("fileManager.downloadFinished", { name }), { id: toastId, duration: 5000 });
      })
      .catch((error: unknown) => {
        if (error instanceof APIError && error.code === "DOWNLOAD_PAUSED") {
          toast.success(t("tasks.pauseDone"), { id: toastId, duration: 5000 });
          return;
        }
        if (error instanceof APIError && error.code === "VERSION_CONFLICT") {
          toast.error(t("authErrors.codes.VERSION_CONFLICT"), { id: toastId, duration: 5000 });
          return;
        }
        const message = error instanceof Error ? error.message : t("common.serverError");
        toast.error(t("fileManager.downloadFailedWithMsg", { message }), { id: toastId, duration: 5000 });
      });
  };

  // 检查路径是否可操作
  const canOperatePath = useCallback((filePath: string) => {
    return isConcreteNamespacePath(filePath);
  }, []);

  const canOperateCurrentPath = canOperatePath(currentPath);
  const canUploadCurrentPath = canOperateCurrentPath && uploadCapability?.supportsDirectUpload !== false;

  const getUploadDeniedMessage = useCallback((): string => {
    if (!canOperateCurrentPath) return t("fileManager.virtualDenied");
    if (uploadCapability?.supportsDirectUpload === false) {
      return t("fileManager.uploadCapabilityDenied", { provider: uploadCapability.provider || "unknown" });
    }
    return t("fileManager.uploadFailed");
  }, [canOperateCurrentPath, t, uploadCapability]);

  useEffect(() => {
    let cancelled = false;
    if (!canOperateCurrentPath) {
      setUploadCapability(null);
      return () => { cancelled = true; };
    }
    void (async () => {
      try {
        const capability = await FileManager.getUploadCapability(currentPath);
        if (!cancelled) setUploadCapability(capability);
      } catch (error) {
        if (error instanceof APIError && error.code === "CAPABILITY_NOT_SUPPORTED") {
          if (!cancelled) {
            setUploadCapability({
              supportsDirectUpload: false,
              provider: "unknown",
              maxPartSizeBytes: 0,
              suggestedChunkSizeBytes: 0,
            });
          }
          return;
        }
        if (!cancelled) setUploadCapability(null);
      }
    })();
    return () => { cancelled = true; };
  }, [canOperateCurrentPath, currentPath]);

  // 处理批量下载文件点击事件
  const resolveDownloadUrl = useCallback((filePath: string): string | null => {
    const finalPath = normalizePathWithMountName(filePath);
    const segs = normalizeVirtual(finalPath);
    if (segs.length < 3) return null;
    if (segs[0] !== "personal" && segs[0] !== "shared") return null;
    const query = new URLSearchParams({ virtualPath: finalPath });
    try {
      const raw = localStorage.getItem(AUTH_KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as { accessToken?: string };
        if (parsed.accessToken) query.set("access_token", parsed.accessToken);
      }
    } catch {
      // ignore
    }
    return `/api/v4/transfers/downloads/proxy?${query.toString()}`;
  }, [normalizePathWithMountName]);

  // 处理虚拟操作拒绝事件
  const denyVirtualOp = () => {
    toast.error(t("fileManager.virtualDenied"));
  };

  // 处理批量下载文件点击事件
  const handleBatchDownload = async () => {
    const selectedArray = selectedFiles instanceof Set ? Array.from(selectedFiles) : files.map((f) => f.name);
    if (selectedArray.length === 0) return;
    const validPaths = selectedArray
      .map((name) => path.join(currentPath, name.toString()))
      .filter((filePath) => canOperatePath(filePath));
    if (validPaths.length === 0) {
      denyVirtualOp();
      return;
    }
    toast.success(t("fileManager.batchDownloadSubmitted", { count: validPaths.length }));
    for (const filePath of validPaths) {
      try {
        await FileManager.downloadWithProgress(filePath);
      } catch (error) {
        const message = error instanceof Error ? error.message : t("common.serverError");
        toast.error(t("fileManager.downloadFailedWithMsg", { message }));
        break;
      }
    }
  };


  // 处理预览文件点击事件
  const handlePreview = (filePath: string) => {
    const mediaItems: MediaPreviewItem[] = [];
    for (const file of files) {
      if (file.isDirectory) continue;
      const fullPath = file.path || path.join(currentPath, file.name);
      const kind = getMediaKind(fullPath);
      if (!kind) continue;
      const src = resolveDownloadUrl(fullPath);
      if (!src) continue;
      mediaItems.push({ key: file.name, name: file.name, src, kind });
    }
    const currentName = path.basename(filePath);
    const idx = mediaItems.findIndex((item) => item.name === currentName);
    if (idx >= 0) {
      setPreviewMediaItems(mediaItems);
      setPreviewMediaIndex(idx);
      setPreviewImageFile("");
      setPreviewImageSrc("");
      return;
    }
    const src = resolveDownloadUrl(filePath);
    setPreviewImageFile(filePath);
    setPreviewImageSrc(src ?? "");
    setPreviewMediaItems([]);
    setPreviewMediaIndex(0);
  };

  // 处理选择文件点击事件
  const handleSelectionChange = (selected: Selection) => setSelectedFiles(selected);
  
  // 处理切换选择模式点击事件
  const toggleSelectionMode = () => {
    setSelectionMode((prev) => {
      if (prev) setSelectedFiles(new Set());
      return !prev;
    });
  };

  // 格式化字节数
  const formatBytes = (bytes: number): string => {
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
  };

  // 渲染上传进度条
  const renderUploadToast = (name: string, current: number, totalCount: number, loaded: number, totalBytes: number, etaText = "--:--:--") => {
    const percent = totalBytes > 0 ? Math.max(0, Math.min(100, Math.round((loaded / totalBytes) * 100))) : 0;
    return (
      <div className="relative overflow-hidden rounded-md bg-background px-3 py-2 text-sm text-foreground shadow-md">
        <div className="flex items-start gap-2">
          <FiUpload className="mt-0.5 shrink-0 text-primary" />
          <div className="min-w-0 flex-1">
            <div className="truncate text-default-700 dark:text-default-200">{t("debug.uploading", { name, current, total: totalCount })}</div>
            <div className="mt-1 flex items-center justify-between gap-3 text-xs text-default-500">
              <span>{formatBytes(loaded)} / {formatBytes(totalBytes)}</span>
              <span>{t("tasks.metricEta")}: {etaText}</span>
            </div>
          </div>
        </div>
        <div className="absolute bottom-0 left-0 h-0.5 bg-primary transition-all duration-150" style={{ width: `${percent}%` }} />
      </div>
    );
  };

  const renderDownloadToast = (name: string, loaded: number, totalBytes: number, speedText: string, etaText = "--:--:--") => {
    const percent = totalBytes > 0 ? Math.max(0, Math.min(100, Math.round((loaded / totalBytes) * 100))) : 0;
    return (
      <div className="relative overflow-hidden rounded-md bg-background px-3 py-2 text-sm text-foreground shadow-md">
        <div className="flex items-start gap-2">
          <FiDownload className="mt-0.5 shrink-0 text-primary" />
          <div className="min-w-0 flex-1">
            <div className="truncate text-default-700 dark:text-default-200">{t("fileManager.downloadingProgress", { name, percent })}</div>
            <div className="mt-1 flex items-center justify-between gap-3 text-xs text-default-500">
              <span>{formatBytes(loaded)} / {formatBytes(totalBytes)}</span>
              <span>{speedText}/s · {etaText}</span>
            </div>
          </div>
        </div>
        <div className="absolute bottom-0 left-0 h-0.5 bg-primary transition-all duration-150" style={{ width: `${percent}%` }} />
      </div>
    );
  };

  /** 拖拽上传入口：按当前目录与权限条件触发批量上传并刷新列表。 */
  const onDrop = async (acceptedFiles: File[]) => {
    if (acceptedFiles.length === 0) return;
    if (!canUploadCurrentPath) {
      toast.error(getUploadDeniedMessage());
      return;
    }
    let toastId: string | undefined;
    let successCount = 0;
    let failedCount = 0;
    const firstErrorMessages: string[] = [];
    try {
      const processedFiles = acceptedFiles.map((file) => {
        const relativePath = file.webkitRelativePath || file.name;
        return new File([file], relativePath, { type: file.type, lastModified: file.lastModified });
      });
      const total = processedFiles.length;
      toastId = toast.custom(renderUploadToast(processedFiles[0].name, 1, total, 0, processedFiles[0].size), { duration: Number.POSITIVE_INFINITY });
      if (!canOperateCurrentPath) throw new Error("current path is virtual namespace");
      if (processedFiles.length > 1) {
        const totalBytes = processedFiles.reduce((sum, file) => sum + file.size, 0);
        toast.custom(renderUploadToast(t("fileManager.batchTarget", { count: processedFiles.length }), 1, 1, 0, totalBytes), { id: toastId, duration: Number.POSITIVE_INFINITY });
        const estimateEta = createRollingEtaEstimator(1, 30);
        try {
          const task = await FileManager.uploadBatchWithProgress(currentPath, processedFiles, (loaded, allBytes) => {
            const remainingSeconds = estimateEta(loaded, allBytes);
            const etaText = formatHmsCountdown(remainingSeconds);
            toast.custom(renderUploadToast(t("fileManager.batchTarget", { count: processedFiles.length }), 1, 1, loaded, allBytes, etaText), {
              id: toastId,
              duration: Number.POSITIVE_INFINITY,
            });
          });
          successCount = task.successCount ?? 0;
          failedCount = task.failedCount ?? 0;
          if (task.errorCode && task.errorCode !== "") {
            firstErrorMessages.push(task.errorCode);
          }
        } catch (error) {
          failedCount = processedFiles.length;
          if (error instanceof APIError) {
            firstErrorMessages.push(error.message);
          } else if (error instanceof Error && error.message) {
            firstErrorMessages.push(error.message);
          }
        }
      } else {
        for (let i = 0; i < processedFiles.length; i += 1) {
        const file = processedFiles[i];
        const currentIndex = i + 1;
        toast.custom(renderUploadToast(file.name, currentIndex, total, 0, file.size), { id: toastId, duration: Number.POSITIVE_INFINITY });
        const estimateEta = createRollingEtaEstimator(1, 30);
        try {
          await FileManager.uploadWithProgress(currentPath, file, (loaded, totalBytes) => {
            const remainingSeconds = estimateEta(loaded, totalBytes);
            const etaText = formatHmsCountdown(remainingSeconds);
            toast.custom(renderUploadToast(file.name, currentIndex, total, loaded, totalBytes, etaText), { id: toastId, duration: Number.POSITIVE_INFINITY });
          });
          successCount += 1;
        } catch (error) {
          failedCount += 1;
          if (error instanceof APIError) {
            firstErrorMessages.push(error.message);
          } else if (error instanceof Error && error.message) {
            firstErrorMessages.push(error.message);
          }
        }
      }
      }
      await loadFiles();
      if (failedCount === 0) {
        toast.success(t("fileManager.uploadDone", { total: successCount }), { id: toastId, duration: 1800 });
      } else if (successCount > 0) {
        const msg = firstErrorMessages[0] || t("fileManager.uploadFailed");
        toast.error(`${t("fileManager.uploadDone", { total: successCount })} | ${t("mounts.batchFailed", { count: failedCount })}: ${msg}`, { id: toastId, duration: 3200 });
      } else {
        const msg = firstErrorMessages[0] || t("fileManager.uploadFailed");
        toast.error(t("fileManager.uploadFailedWithMsg", { message: msg }), { id: toastId, duration: 2600 });
      }
      if (toastId) window.setTimeout(() => toast.dismiss(toastId), 2200);
    } catch (error) {
      let msg = t("fileManager.uploadFailed");
      if (error instanceof APIError) {
        if (error.code === "PERMISSION_DENIED") msg = t("fileManager.uploadNoPerm");
        else if (error.code === "UNAUTHORIZED") msg = t("fileManager.uploadUnauthorized");
        else if (error.code === "MOUNT_DISABLED") msg = t("fileManager.uploadMountDisabled");
        else msg = t("fileManager.uploadFailedWithMsg", { message: error.message });
      } else if (error instanceof Error && error.message) {
        msg = t("fileManager.uploadFailedWithMsg", { message: error.message });
      }
      if (toastId) {
        toast.error(msg, { id: toastId, duration: 2600 });
        window.setTimeout(() => toast.dismiss(toastId), 3000);
      } else {
        toast.error(msg, { duration: 2600 });
      }
    }
  };

  const { getRootProps, getInputProps, isDragActive, open } = useDropzone({
    onDrop: (acceptedFiles) => { void onDrop(acceptedFiles); },
    noClick: true,
    onDragOver: (e) => { e.preventDefault(); e.stopPropagation(); },
    useFsAccessApi: false,
  });

  useEffect(() => {
    const hasFiles = (evt: DragEvent) => {
      const types = evt.dataTransfer?.types;
      return !!types && Array.from(types).includes("Files");
    };
    const onDragEnter = (evt: DragEvent) => {
      if (!hasFiles(evt)) return;
      dragDepthRef.current += 1;
      setIsDraggingFile(true);
    };
    const onDragLeave = (evt: DragEvent) => {
      if (!hasFiles(evt)) return;
      dragDepthRef.current = Math.max(0, dragDepthRef.current - 1);
      if (dragDepthRef.current === 0) setIsDraggingFile(false);
    };
    const onDropGlobal = () => {
      dragDepthRef.current = 0;
      setIsDraggingFile(false);
    };
    const onDragEnd = () => {
      dragDepthRef.current = 0;
      setIsDraggingFile(false);
    };
    window.addEventListener("dragenter", onDragEnter);
    window.addEventListener("dragleave", onDragLeave);
    window.addEventListener("drop", onDropGlobal);
    window.addEventListener("dragend", onDragEnd);
    return () => {
      window.removeEventListener("dragenter", onDragEnter);
      window.removeEventListener("dragleave", onDragLeave);
      window.removeEventListener("drop", onDropGlobal);
      window.removeEventListener("dragend", onDragEnd);
    };
  }, []);

  const backgroundImage = localStorage.getItem(key.backgroundImage) ?? "";
  const hasBackground = !!backgroundImage;

  return (
    <div className="h-full flex flex-col relative gap-4 w-full p-2 md:p-4">
      <div className={clsx("mb-4 flex flex-col md:flex-row items-stretch md:items-center gap-4 sticky top-14 z-10 backdrop-blur-sm shadow-sm py-2 px-4 rounded-sm transition-colors", hasBackground ? "bg-white/20 dark:bg-black/10 border border-white/40 dark:border-white/10" : "bg-white/60 dark:bg-black/40 border border-white/40 dark:border-white/10")}>
        <div className="flex items-center gap-2 overflow-x-auto hide-scrollbar pb-1 md:pb-0">
          <ShadowTooltip content={t("shares.goParent")}><Button aria-label={t("shares.goParent")} radius="sm" color="primary" size="sm" isIconOnly variant="flat" onPress={() => handleDirectoryClick("..") } className="text-lg min-w-8"><TiArrowBack /></Button></ShadowTooltip>
          <ShadowTooltip content={t("fileManager.createTitle")}><Button aria-label={t("fileManager.createTitle")} radius="sm" color="primary" size="sm" isIconOnly variant="flat" onPress={() => setIsCreateModalOpen(true)} className="text-lg min-w-8"><FiPlus /></Button></ShadowTooltip>
          <ShadowTooltip content={t("common.refresh")}><Button aria-label={t("common.refresh")} radius="sm" color="primary" isLoading={loading} size="sm" isIconOnly variant="flat" onPress={() => void loadFiles()} className="text-lg min-w-8"><MdRefresh /></Button></ShadowTooltip>
          <ShadowTooltip content={canUploadCurrentPath ? t("fileManager.dropUpload") : getUploadDeniedMessage()}><Button aria-label={t("fileManager.dropUpload")} radius="sm" color="primary" size="sm" isIconOnly variant="flat" onPress={() => { if (!canUploadCurrentPath) { toast.error(getUploadDeniedMessage()); return; } open(); }} className="text-lg min-w-8"><FiUpload /></Button></ShadowTooltip>
          <ShadowTooltip content={selectionMode ? t("shares.exitMultiSelect") : t("shares.enterMultiSelect")}><Button
            aria-label={selectionMode ? t("shares.exitMultiSelect") : t("shares.enterMultiSelect")}
            radius="sm"
            color="primary"
            size="sm"
            isIconOnly
            variant={selectionMode ? "solid" : "flat"}
            onPress={toggleSelectionMode}
            className="text-lg min-w-8"
          >
            {selectionMode ? <FiX /> : <FiCheckSquare />}
          </Button></ShadowTooltip>
          {((selectedFiles instanceof Set && selectedFiles.size > 0) || selectedFiles === "all") && (
            <>
              <Button radius="sm" color="danger" size="sm" variant="flat" onPress={() => void handleBatchDelete()} className="text-sm px-2 min-w-fit" startContent={<TbTrash className="text-lg" />}>({selectedFiles instanceof Set ? selectedFiles.size : files.length})</Button>
              <Button radius="sm" color="warning" size="sm" variant="flat" onPress={() => { setMoveTargetPath(""); setIsMoveModalOpen(true); }} className="text-sm px-2 min-w-fit" startContent={<FiMove className="text-lg" />}>({selectedFiles instanceof Set ? selectedFiles.size : files.length})</Button>
              <Button radius="sm" color="secondary" size="sm" variant="flat" onPress={() => void handleBatchDownload()} className="text-sm px-2 min-w-fit" startContent={<FiDownload className="text-lg" />}>({selectedFiles instanceof Set ? selectedFiles.size : files.length})</Button>
            </>
          )}
        </div>

        <div className="flex flex-1 gap-2 overflow-hidden items-stretch md:items-center">
          <div className="flex-1 rounded-sm border border-white/20 bg-white/40 px-2 py-1 shadow-sm backdrop-blur-md dark:bg-black/20">
            {isPathEditing ? (
              <Input
                autoFocus
                radius="sm"
                type="text"
                placeholder={t("fileManager.pathPlaceholder")}
                value={jumpPath}
                onChange={(e) => setJumpPath(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") confirmJumpPath();
                  if (e.key === "Escape") {
                    setIsPathEditing(false);
                    setJumpPath(currentPath);
                  }
                }}
                onBlur={() => {
                  setIsPathEditing(false);
                  setJumpPath(currentPath);
                }}
                className="w-full mpfm-path-jump-input"
                classNames={{
                  inputWrapper:
                    "bg-white/40 dark:bg-black/20 backdrop-blur-md px-2",
                  input: "text-sm text-default-700 dark:text-default-200",
                }}
              />
            ) : (
              <div onClick={() => { setIsPathEditing(true); setJumpPath(currentPath); }}>
                <Breadcrumbs radius="sm" className="overflow-x-auto hide-scrollbar whitespace-nowrap">
                  {currentPath.split("/").map((part, index, parts) => (
                    <BreadcrumbItem
                      key={`${part}-${index}`}
                      isCurrent={false}
                      onPress={() => {
                        const newPath = parts.slice(0, index + 1).join("/");
                        navigate(`/app/files#${encodeURIComponent(newPath || ".")}`);
                      }}
                    >
                      {part || "."}
                    </BreadcrumbItem>
                  ))}
                </Breadcrumbs>
              </div>
            )}
          </div>
          <Button
            radius="sm"
            color="primary"
            size="sm"
            variant="flat"
            onPress={() => {
              if (isPathEditing) confirmJumpPath();
              else {
                setIsPathEditing(true);
                setJumpPath(currentPath);
              }
            }}
            startContent={<FiCheck />}
            className="shrink-0"
          >
            {t("fileManager.pathConfirm")}
          </Button>
        </div>
      </div>

      <div {...getRootProps()} className="relative min-h-0 flex-1 pb-1">
        <input {...getInputProps()} />
        <div className="h-full min-h-0">
          <FileTable files={files} currentPath={currentPath} loading={loading} sortDescriptor={sortDescriptor} onSortChange={handleSortChange} selectedFiles={selectedFiles} onSelectionChange={handleSelectionChange} selectionMode={selectionMode} onDirectoryClick={handleDirectoryClick} onEdit={(p) => { void handleEdit(p); }} onPreview={handlePreview} onRenameRequest={(filePath) => { if (!canOperatePath(filePath)) return denyVirtualOp(); setRenamingFile(filePath); setNewFileName(path.basename(filePath)); setIsRenameModalOpen(true); }} onMoveRequest={(filePath) => { if (!canOperatePath(filePath)) return denyVirtualOp(); handleMoveClick(filePath); }} onCopyPath={handleCopyPath} onDelete={(p) => { if (!canOperatePath(p)) return denyVirtualOp(); void handleDelete(p); }} onDownload={(p) => { if (!canOperatePath(p)) return denyVirtualOp(); handleDownload(p); }} canOperatePath={canOperatePath} currentIsRoot={user?.is_root} resolveDownloadUrl={resolveDownloadUrl} resolveMountProtocol={resolveMountProtocol} />
        </div>
        {isDraggingFile && (
          <div
            className={clsx(
              "pointer-events-none absolute inset-0 z-20 flex items-center justify-center rounded-sm border-2 border-dashed transition-all duration-200",
              isDragActive
                ? "border-primary bg-white/16 dark:bg-black/22 backdrop-blur-[4px] shadow-[inset_0_0_0_1px_rgba(59,130,246,0.25)]"
                : "border-default-300/80 bg-white/10 dark:bg-black/16 backdrop-blur-[2px]"
            )}
          >
            <div
              className={clsx(
                "rounded-sm px-4 py-2 text-sm font-medium shadow-sm transition-all duration-200",
                isDragActive ? "bg-white/90 text-primary dark:bg-black/65" : "bg-white/75 text-default-600 dark:bg-black/45 dark:text-default-300"
              )}
            >
              {t("fileManager.dropUpload")}
            </div>
          </div>
        )}
      </div>

      <FileEditModal isOpen={!!editingFile} file={editingFile} isSaving={isSavingFile} isLoading={isEditLoading} onClose={() => { editRequestSeqRef.current += 1; setIsEditLoading(false); setEditingFile(null); }} onSave={(content) => void handleSave(content)} />
      <FilePreviewModal
        isOpen={!!previewImageFile || previewMediaItems.length > 0}
        imageFilePath={previewImageFile}
        imageSrc={previewImageSrc}
        mediaItems={previewMediaItems}
        mediaIndex={previewMediaIndex}
        onMediaIndexChange={setPreviewMediaIndex}
        onClose={() => {
          setPreviewImageFile("");
          setPreviewImageSrc("");
          setPreviewMediaItems([]);
          setPreviewMediaIndex(0);
        }}
      />
      <CreateFileModal isOpen={isCreateModalOpen} fileType={fileType} newFileName={newFileName} onTypeChange={setFileType} onNameChange={setNewFileName} onClose={() => setIsCreateModalOpen(false)} onCreate={() => void handleCreate()} />
      <RenameModal isOpen={isRenameModalOpen} newFileName={newFileName} onNameChange={setNewFileName} onClose={() => setIsRenameModalOpen(false)} onRename={() => void handleRename()} />
      <MoveModal isOpen={isMoveModalOpen} moveTargetPath={moveTargetPath} selectionInfo={selectedFiles instanceof Set && selectedFiles.size > 0 ? t("fileManager.itemsCount", { count: selectedFiles.size }) : path.basename(renamingFile)} onClose={() => setIsMoveModalOpen(false)} onMove={() => (selectedFiles instanceof Set && selectedFiles.size > 0 ? void handleBatchMove() : void handleMove(renamingFile))} onSelect={(dir) => setMoveTargetPath(dir)} />
    </div>
  );
}


