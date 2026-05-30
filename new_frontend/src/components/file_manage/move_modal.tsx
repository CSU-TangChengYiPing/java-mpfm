import { Button } from "@heroui/button";
import { ModalBody, ModalContent, ModalFooter, ModalHeader } from "@heroui/modal";
import { Spinner } from "@heroui/spinner";
import clsx from "clsx";
import path from "path-browserify";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { IoAdd, IoRemove } from "react-icons/io5";
import FileManager from "../../controllers/file_manager";
import BlurModal from "../common/BlurModal";

interface MoveModalProps {
  isOpen: boolean;
  moveTargetPath: string;
  selectionInfo: string;
  onClose: () => void;
  onMove: () => void;
  onSelect: (dir: string) => void;
}

/** 目录树节点：按需懒加载子目录并同步当前移动目标选中态。 */
function DirectoryTree({ basePath, onSelect, selectedPath }: { basePath: string; onSelect: (dir: string) => void; selectedPath?: string }) {
  const [dirs, setDirs] = useState<string[]>([]);
  const [expanded, setExpanded] = useState(false);
  const [loading, setLoading] = useState(false);

  const fetchDirectories = async () => {
    try {
      const list = await FileManager.listDirectories(basePath);
      setDirs(list.map((item) => item.name));
    } catch {
      setDirs([]);
    }
  };

  const handleToggle = async () => {
    if (!expanded) {
      setExpanded(true);
      setLoading(true);
      await fetchDirectories();
      setLoading(false);
    } else {
      setExpanded(false);
    }
  };

  const isSelected = selectedPath === basePath;
  const variant = isSelected ? "solid" : selectedPath && path.dirname(selectedPath) === basePath ? "flat" : "light";

  return (
    <div className="ml-4">
      <Button radius="sm" onPress={() => { onSelect(basePath); void handleToggle(); }} className="py-1 px-2 text-left justify-start min-w-0 min-h-0 h-auto text-sm rounded-sm" size="sm" color="primary" variant={variant} startContent={<div className={clsx("rounded-sm", isSelected ? "bg-primary-600" : "bg-primary-50")}>{expanded ? <IoRemove /> : <IoAdd />}</div>}>
        {basePath === "." ? "." : path.basename(basePath)}
      </Button>
      {expanded && (
        <div>
          {loading ? <div className="flex py-1 px-8"><Spinner size="sm" color="primary" /></div> : dirs.map((dirName) => {
            const childPath = basePath === "." ? dirName : path.join(basePath, dirName);
            return <DirectoryTree key={childPath} basePath={childPath} onSelect={onSelect} selectedPath={selectedPath} />;
          })}
        </div>
      )}
    </div>
  );
}

export default function MoveModal({ isOpen, moveTargetPath, selectionInfo, onClose, onMove, onSelect }: MoveModalProps) {
  const { t } = useTranslation();

  return (
    <BlurModal
      radius="sm"
      isOpen={isOpen}
      onClose={onClose}
    >
      <ModalContent>
        <ModalHeader>{t("fileManager.moveTitle")}</ModalHeader>
        <ModalBody>
          <div className="rounded-sm p-2 border border-default-300 overflow-auto max-h-60">
            <DirectoryTree basePath="." onSelect={onSelect} selectedPath={moveTargetPath} />
          </div>
          <p className="text-sm text-default-500 mt-2">{t("fileManager.moveCurrent")}: {moveTargetPath || t("fileManager.moveEmpty")}</p>
          <p className="text-sm text-default-500">{t("fileManager.moveItems")}: {selectionInfo}</p>
        </ModalBody>
        <ModalFooter>
          <Button radius="sm" color="primary" variant="flat" onPress={onClose}>{t("fileManager.cancel")}</Button>
          <Button radius="sm" color="primary" onPress={onMove}>{t("fileManager.confirm")}</Button>
        </ModalFooter>
      </ModalContent>
    </BlurModal>
  );
}
