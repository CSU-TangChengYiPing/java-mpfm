import { Button } from "@heroui/button";
import { LargeGlassInput } from "../common/LargeGlassField";
import { ModalBody, ModalContent, ModalFooter, ModalHeader } from "@heroui/modal";
import { Tab, Tabs } from "@heroui/tabs";
import { useTranslation } from "react-i18next";
import BlurModal from "../common/BlurModal";

interface CreateFileModalProps {
  isOpen: boolean;
  fileType: "file" | "directory";
  newFileName: string;
  onTypeChange: (type: "file" | "directory") => void;
  onNameChange: (value: string) => void;
  onClose: () => void;
  onCreate: () => void;
}

export default function CreateFileModal({ isOpen, fileType, newFileName, onTypeChange, onNameChange, onClose, onCreate }: CreateFileModalProps) {
  const { t } = useTranslation();

  return (
    <BlurModal
      radius="sm"
      isOpen={isOpen}
      onClose={onClose}
    >
      <ModalContent>
        <ModalHeader>{t("fileManager.createTitle")}</ModalHeader>
        <ModalBody>
          <div className="flex flex-col gap-4">
            <Tabs
              selectedKey={fileType}
              onSelectionChange={(k) => onTypeChange(String(k) as "file" | "directory")}
              size="sm"
              color="primary"
              variant="solid"
            >
              <Tab key="file" title={t("fileManager.createTypeFile")} />
              <Tab key="directory" title={t("fileManager.createTypeDirectory")} />
            </Tabs>
            <LargeGlassInput radius="sm" label={t("fileManager.createNameLabel")} value={newFileName} onValueChange={onNameChange} commitMode="blur" />
          </div>
        </ModalBody>
        <ModalFooter>
          <Button radius="sm" color="primary" variant="flat" onPress={onClose}>{t("fileManager.cancel")}</Button>
          <Button radius="sm" color="primary" onPress={onCreate}>{t("fileManager.create")}</Button>
        </ModalFooter>
      </ModalContent>
    </BlurModal>
  );
}

