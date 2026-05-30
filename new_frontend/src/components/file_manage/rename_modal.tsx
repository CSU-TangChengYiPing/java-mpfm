import { Button } from "@heroui/button";
import { LargeGlassInput } from "../common/LargeGlassField";
import { ModalBody, ModalContent, ModalFooter, ModalHeader } from "@heroui/modal";
import { useTranslation } from "react-i18next";
import BlurModal from "../common/BlurModal";

interface RenameModalProps {
  isOpen: boolean;
  newFileName: string;
  onNameChange: (value: string) => void;
  onClose: () => void;
  onRename: () => void;
}

export default function RenameModal({ isOpen, newFileName, onNameChange, onClose, onRename }: RenameModalProps) {
  const { t } = useTranslation();

  return (
    <BlurModal
      radius="sm"
      isOpen={isOpen}
      onClose={onClose}
    >
      <ModalContent>
        <ModalHeader>{t("fileManager.renameTitle")}</ModalHeader>
        <ModalBody>
          <LargeGlassInput radius="sm" label={t("fileManager.renameLabel")} value={newFileName} onValueChange={onNameChange} commitMode="blur" />
        </ModalBody>
        <ModalFooter>
          <Button radius="sm" color="primary" variant="flat" onPress={onClose}>{t("fileManager.cancel")}</Button>
          <Button radius="sm" color="primary" onPress={onRename}>{t("fileManager.confirm")}</Button>
        </ModalFooter>
      </ModalContent>
    </BlurModal>
  );
}

