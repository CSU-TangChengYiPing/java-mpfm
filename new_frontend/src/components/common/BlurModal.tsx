import { Modal as HeroModal, type ModalProps } from "@heroui/modal";

/**
 * 统一弹窗预设：与 temp/UI 对齐，默认使用 blur 背景并固定弹窗层级。
 */
export default function BlurModal(props: ModalProps) {
  const { classNames, backdrop, ...rest } = props;
  const mergedClassNames = {
    ...classNames,
    backdrop: `${classNames?.backdrop ?? ""} backdrop-blur-sm`.trim(),
    wrapper: `${classNames?.wrapper ?? ""} z-[99]`.trim(),
  };
  return <HeroModal {...rest} backdrop={backdrop ?? "blur"} classNames={mergedClassNames} />;
}

