import { Button } from "@heroui/button";
import { Input } from "@heroui/input";
import { ModalBody, ModalContent, ModalFooter, ModalHeader } from "@heroui/modal";
import { Spinner } from "@heroui/spinner";
import clsx from "clsx";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FiCheck, FiChevronRight, FiFolder } from "react-icons/fi";
import { TiArrowBack } from "react-icons/ti";
import { Tree, type NodeRendererProps } from "react-arborist";
import FileManager from "../../controllers/file_manager";
import BlurModal from "../common/BlurModal";
import { buildMoveArboristNodes, patchMoveArboristChildren, type MoveArboristNode } from "./move_arborist.model";
import {
  getMoveBrowseVirtualMountRootPath,
  isMoveBrowseWithinVirtualMount,
  normalizeMoveBrowsePath,
  resolveMoveBrowseParentPath,
  splitMoveBrowseTrail,
} from "./move_modal.model";

interface MoveModalProps {
  isOpen: boolean;
  moveTargetPath: string;
  selectionInfo: string;
  onClose: () => void;
  onMove: () => void;
  onSelect: (dir: string) => void;
}

function MoveNode({ node, style }: NodeRendererProps<MoveArboristNode>) {
  return (
    <div
      style={style}
      className={clsx(
        "flex items-center gap-1 border-l border-transparent px-1.5 py-0.5 text-[10px] leading-none transition-colors",
        node.isSelected
          ? "border-primary bg-primary-50 text-primary-700 dark:border-primary-400 dark:bg-primary-900/25 dark:text-primary-200"
          : "border-transparent text-default-700 hover:bg-default-100 dark:text-default-200 dark:hover:bg-default-800/60",
      )}
      onClick={() => node.select()}
      onDoubleClick={() => node.activate()}
    >
      <button
        type="button"
        className="flex h-4 w-4 shrink-0 items-center justify-center p-0 text-primary"
        onClick={(event) => {
          event.stopPropagation();
          if (node.isInternal) node.toggle();
        }}
        aria-label={node.isOpen ? "collapse" : "expand"}
      >
        {node.isInternal ? <FiChevronRight className={clsx("text-[9px] transition-transform", node.isOpen && "rotate-90")} /> : <FiFolder className="text-[9px]" />}
      </button>
      <span className="min-w-0 flex-1 truncate">{node.data.name}</span>
    </div>
  );
}

export default function MoveModal({ isOpen, moveTargetPath, selectionInfo, onClose, onMove, onSelect }: MoveModalProps) {
  const { t } = useTranslation();
  const treePanelHeight = 260;
  const [browsePath, setBrowsePath] = useState(normalizeMoveBrowsePath(moveTargetPath || "."));
  const [selectedPath, setSelectedPath] = useState(normalizeMoveBrowsePath(moveTargetPath || "."));
  const [isPathEditing, setIsPathEditing] = useState(false);
  const [jumpPath, setJumpPath] = useState(normalizeMoveBrowsePath(moveTargetPath || "."));
  const [treeData, setTreeData] = useState<MoveArboristNode[]>([]);
  const [loading, setLoading] = useState(false);

  const currentTrail = useMemo(() => splitMoveBrowseTrail(browsePath), [browsePath]);
  const virtualMountRoot = useMemo(() => getMoveBrowseVirtualMountRootPath(browsePath), [browsePath]);
  const parentPath = useMemo(() => resolveMoveBrowseParentPath(browsePath), [browsePath]);
  const canGoParent = parentPath !== browsePath;

  const loadChildren = async (basePath: string) => {
    setLoading(true);
    try {
      const list = await FileManager.listDirectories(basePath);
      const nodes = buildMoveArboristNodes(basePath, list);
      if (basePath === browsePath) {
        setTreeData(nodes);
      } else {
        setTreeData((prev) => patchMoveArboristChildren(prev, basePath, nodes));
      }
    } catch {
      if (basePath === browsePath) setTreeData([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!isOpen) return;
    const initialPath = normalizeMoveBrowsePath(moveTargetPath || ".");
    setBrowsePath(initialPath);
    setSelectedPath(initialPath);
    setIsPathEditing(false);
    setJumpPath(initialPath);
    setTreeData([]);
    onSelect(initialPath);
    const seq = window.setTimeout(() => {
      void loadChildren(initialPath);
    }, 0);
    return () => window.clearTimeout(seq);
  }, [isOpen, moveTargetPath, onSelect]);

  useEffect(() => {
    if (!isOpen) return;
    const seq = window.setTimeout(() => {
      void loadChildren(browsePath);
    }, 0);
    return () => window.clearTimeout(seq);
  }, [browsePath, isOpen]);

  const findNodeById = (nodes: MoveArboristNode[], id: string): MoveArboristNode | null => {
    for (const node of nodes) {
      if (node.id === id) return node;
      const child = findNodeById(node.children, id);
      if (child) return child;
    }
    return null;
  };

  const openChildPath = (childPath: string) => {
    const normalized = normalizeMoveBrowsePath(childPath);
    if (!isMoveBrowseWithinVirtualMount(normalized, browsePath)) return;
    setBrowsePath(normalized);
    setSelectedPath(normalized);
    setJumpPath(normalized);
    onSelect(normalized);
  };

  const confirmJumpPath = () => {
    const nextPath = normalizeMoveBrowsePath(jumpPath || ".");
    if (!isMoveBrowseWithinVirtualMount(nextPath, browsePath)) {
      setJumpPath(browsePath);
      return;
    }
    setIsPathEditing(false);
    openChildPath(nextPath);
  };

  return (
    <BlurModal radius="sm" isOpen={isOpen} onClose={onClose}>
      <ModalContent className="w-[640px] max-w-[94vw]">
        <ModalHeader>{t("fileManager.moveTitle")}</ModalHeader>
        <ModalBody>
          <div className="flex flex-col gap-2">
            <div className="rounded-sm border border-default-300 bg-default-50/60 px-2 py-2 dark:bg-default-900/30">
              <div className="flex items-center gap-2">
                <Button
                  isIconOnly
                  radius="sm"
                  size="sm"
                  color="primary"
                  variant="flat"
                  isDisabled={!canGoParent}
                  onPress={() => {
                    const nextPath = resolveMoveBrowseParentPath(browsePath);
                    openChildPath(nextPath);
                  }}
                  aria-label={t("shares.goParent")}
                  className="shrink-0 min-w-8"
                >
                  <TiArrowBack className="text-[14px]" />
                </Button>
                <div className="flex h-8 min-w-0 flex-1 items-center overflow-hidden rounded-sm border border-white/20 bg-white/40 shadow-sm backdrop-blur-md dark:bg-black/20">
                  {isPathEditing ? (
                    <Input
                      autoFocus
                      radius="sm"
                      size="sm"
                      type="text"
                      value={jumpPath}
                      onChange={(event) => setJumpPath(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") confirmJumpPath();
                        if (event.key === "Escape") {
                          setIsPathEditing(false);
                          setJumpPath(browsePath);
                        }
                      }}
                    onBlur={() => {
                      setIsPathEditing(false);
                      setJumpPath(browsePath);
                    }}
                      className="w-full mpfm-path-jump-input"
                      classNames={{
                        inputWrapper: "h-8 min-h-8 rounded-sm border-0 bg-transparent px-2 shadow-none",
                        input: "text-sm text-default-700 dark:text-default-200",
                      }}
                    />
                  ) : (
                    <div
                      className="flex h-full min-w-0 flex-1 items-center overflow-x-auto whitespace-nowrap px-2 text-xs text-default-600 hide-scrollbar"
                      onClick={() => {
                        setIsPathEditing(true);
                        setJumpPath(browsePath);
                      }}
                    >
                      <span className="shrink-0 text-default-400">{virtualMountRoot ? t("fileManager.moveCurrent") : t("fileManager.moveCurrent")}：</span>
                      <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-1">
                          <button
                            type="button"
                            className="shrink-0 hover:text-primary"
                            onClick={(event) => {
                              event.stopPropagation();
                              openChildPath(".");
                            }}
                          >
                            .
                          </button>
                          {currentTrail.map((part, index) => {
                            const nextPath = currentTrail.slice(0, index + 1).join("/") || ".";
                            return (
                              <span key={`${part}-${index}`} className="flex min-w-0 items-center gap-1">
                                <span className="shrink-0 text-default-300">/</span>
                                <button
                                  type="button"
                                  className="min-w-0 truncate hover:text-primary"
                                  onClick={(event) => {
                                    event.stopPropagation();
                                    openChildPath(nextPath);
                                  }}
                                >
                                  {part}
                                </button>
                              </span>
                            );
                          })}
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>

            <div className="rounded-sm border border-default-300 bg-default-50/40 dark:bg-default-900/30">
              <div className="h-[260px] overflow-hidden">
                {loading && treeData.length === 0 ? (
                  <div className="flex h-full items-center justify-center text-xs text-default-500">
                    <Spinner size="sm" color="primary" />
                    <span className="ml-2">{t("common.loading")}</span>
                  </div>
                ) : (
                  <Tree
                    data={treeData}
                    width="100%"
                    height={treePanelHeight}
                    indent={10}
                    rowHeight={24}
                    openByDefault={false}
                    disableMultiSelection
                    selectionFollowsFocus
                    selection={selectedPath}
                    onSelect={(nodes) => {
                      const first = nodes[0];
                      if (!first) return;
                      setSelectedPath(first.data.path);
                      onSelect(first.data.path);
                    }}
                    onActivate={(node) => {
                      setSelectedPath(node.data.path);
                      onSelect(node.data.path);
                      if (node.isInternal) node.toggle();
                    }}
                    onToggle={(id) => {
                      const target = findNodeById(treeData, id);
                      if (target && !target.loaded) {
                        void loadChildren(target.path);
                      }
                    }}
                    >
                      {MoveNode}
                    </Tree>
                )}
              </div>
            </div>
          </div>
          <div className="mt-2 text-xs text-default-500">
            {t("fileManager.moveItems")}: {selectionInfo}
          </div>
        </ModalBody>
        <ModalFooter>
          <Button radius="sm" color="primary" variant="flat" onPress={onClose}>{t("fileManager.cancel")}</Button>
          <Button radius="sm" color="primary" onPress={onMove} startContent={<FiCheck />}>{t("fileManager.confirm")}</Button>
        </ModalFooter>
      </ModalContent>
    </BlurModal>
  );
}
