import { Button } from "@heroui/button";
import { ModalBody, ModalContent, ModalFooter, ModalHeader } from "@heroui/modal";
import type { ReactNode } from "react";
import BlurModal from "./BlurModal";

type FormModalProps = {
  isOpen: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  onSubmit: () => void;
  submitText?: ReactNode;
  cancelText?: ReactNode;
  radius?: "none" | "sm" | "md" | "lg";
  isDismissable?: boolean;
  secondaryActionText?: ReactNode;
  onSecondaryAction?: () => void;
  hideCancelButton?: boolean;
  hideSubmitButton?: boolean;
};

export default function FormModal({
  isOpen,
  onClose,
  title,
  children,
  onSubmit,
  submitText = "Save",
  cancelText = "Cancel",
  radius = "sm",
  isDismissable = true,
  secondaryActionText,
  onSecondaryAction,
  hideCancelButton = false,
  hideSubmitButton = false,
}: FormModalProps) {
  return (
    <BlurModal
      isOpen={isOpen}
      onClose={onClose}
      radius={radius}
      isDismissable={isDismissable}
      isKeyboardDismissDisabled={false}
    >
      <ModalContent>
        <ModalHeader>{title}</ModalHeader>
        <ModalBody className="gap-3">{children}</ModalBody>
        <ModalFooter>
          {!hideCancelButton ? (
            <Button variant="flat" onPress={onClose}>
              {cancelText}
            </Button>
          ) : null}
          {secondaryActionText && onSecondaryAction ? (
            <Button variant="flat" onPress={onSecondaryAction}>
              {secondaryActionText}
            </Button>
          ) : null}
          {!hideSubmitButton ? (
            <Button color="primary" onPress={onSubmit}>
              {submitText}
            </Button>
          ) : null}
        </ModalFooter>
      </ModalContent>
    </BlurModal>
  );
}
