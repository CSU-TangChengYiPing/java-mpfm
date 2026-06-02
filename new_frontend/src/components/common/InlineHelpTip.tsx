import clsx from "clsx";
import { type ReactNode, useEffect, useMemo, useRef, useState } from "react";

type InlineHelpTipProps = {
  content: ReactNode;
  title?: ReactNode;
  ariaLabel?: string;
  className?: string;
  contentClassName?: string;
};

/** 说明气泡：统一承载配置类提示，桌面支持悬浮，手机支持点击展开。 */
export default function InlineHelpTip({ content, title, ariaLabel, className, contentClassName }: InlineHelpTipProps) {
  const [isOpen, setIsOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const closeOnOutsidePointerDown = (event: PointerEvent) => {
      const root = rootRef.current;
      if (!root || !isOpen) return;
      if (event.target instanceof Node && root.contains(event.target)) return;
      setIsOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setIsOpen(false);
    };
    document.addEventListener("pointerdown", closeOnOutsidePointerDown);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOnOutsidePointerDown);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [isOpen]);

  const buttonAriaLabel = useMemo(() => {
    if (typeof ariaLabel === "string" && ariaLabel.trim()) return ariaLabel;
    if (typeof title === "string" && title.trim()) return title;
    if (typeof content === "string" && content.trim()) return content;
    return "help";
  }, [ariaLabel, content, title]);

  return (
    <div
      ref={rootRef}
      className={clsx("relative inline-flex", className)}
      onMouseEnter={() => setIsOpen(true)}
      onMouseLeave={() => setIsOpen(false)}
      onFocusCapture={() => setIsOpen(true)}
      onBlurCapture={(event) => {
        const root = rootRef.current;
        if (!root || event.relatedTarget instanceof Node && root.contains(event.relatedTarget)) return;
        setIsOpen(false);
      }}
    >
      <button
        type="button"
        aria-label={buttonAriaLabel}
        aria-expanded={isOpen}
        className="inline-flex h-5 w-5 cursor-help items-center justify-center rounded-full border border-default-300 bg-default-100 text-xs font-semibold text-default-700 transition hover:border-primary/40 hover:text-primary focus:outline-none focus:ring-2 focus:ring-primary/40 touch-manipulation"
        onClick={() => setIsOpen((prev) => !prev)}
      >
        ?
      </button>
      {isOpen && (
        <div
          role="tooltip"
          className={clsx(
            "absolute left-1/2 top-full z-50 mt-2 w-max max-w-[320px] -translate-x-1/2 rounded-md border border-white/25 bg-white/95 px-3 py-2 text-left text-xs text-default-700 shadow-xl backdrop-blur-md dark:border-white/10 dark:bg-zinc-900/95 dark:text-default-100",
            contentClassName
          )}
        >
          {title ? <div className="mb-1 font-medium text-default-800 dark:text-default-50">{title}</div> : null}
          <div className="whitespace-pre-wrap leading-5">{content}</div>
        </div>
      )}
    </div>
  );
}
