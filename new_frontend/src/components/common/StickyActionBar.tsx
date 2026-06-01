import clsx from "clsx";
import type { PropsWithChildren } from "react";

type StickyActionBarProps = PropsWithChildren<{
  className?: string;
}>;

export default function StickyActionBar({ className, children }: StickyActionBarProps) {
  return (
    <div
      className={clsx(
        "mb-4 sticky top-2 z-10 backdrop-blur-sm shadow-sm py-2 px-4 rounded-sm border border-white/40 bg-white/60 dark:bg-black/40 dark:border-white/10",
        className
      )}
    >
      {children}
    </div>
  );
}

