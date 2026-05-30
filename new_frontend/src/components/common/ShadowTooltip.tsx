import { Tooltip, type TooltipProps } from "@heroui/tooltip";
import { type ReactNode, useCallback, useState } from "react";

type ShadowTooltipProps = {
  content: ReactNode;
  children: ReactNode;
  placement?: TooltipProps["placement"];
};

export default function ShadowTooltip({ content, children, placement = "top" }: ShadowTooltipProps) {
  const [autoPlacement, setAutoPlacement] = useState<TooltipProps["placement"]>(placement);

  const resolvePlacement = useCallback((el: HTMLElement | null) => {
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const viewportHeight = window.innerHeight || 0;
    const nearTop = rect.top < viewportHeight * 0.35;
    const nearBottom = rect.bottom > viewportHeight * 0.65;
    if (nearTop && !nearBottom) setAutoPlacement("bottom");
    else if (nearBottom) setAutoPlacement("top");
    else setAutoPlacement(placement);
  }, [placement]);

  return (
    <Tooltip
      content={content}
      placement={autoPlacement}
      showArrow
      delay={100}
      closeDelay={80}
      classNames={{
        content:
          "rounded-md border border-white/25 dark:border-white/10 bg-white/90 dark:bg-zinc-900/88 px-2 py-1 text-xs text-default-700 dark:text-default-100 shadow-xl backdrop-blur-md",
      }}
    >
      <span
        className="inline-flex"
        onMouseEnter={(e) => resolvePlacement(e.currentTarget)}
        onFocus={(e) => resolvePlacement(e.currentTarget)}
      >
        {children}
      </span>
    </Tooltip>
  );
}
