export const sharedGlassTableClassNames = {
  wrapper: "h-[calc(100vh-264px)] overflow-y-auto overflow-x-hidden bg-white/60 dark:bg-black/40 backdrop-blur-xl border border-white/40 dark:border-white/10 shadow-sm p-0 !rounded-sm",
  thead: "sticky top-0 z-30",
  th: "sticky top-0 z-[31] bg-white/40 dark:bg-black/40 text-default-700 dark:text-default-200",
} as const;

export const sharedTableBottomBarClassName =
  "grid w-full min-w-0 grid-cols-[1fr_auto_1fr] items-center border border-white/20 bg-white/75 p-2 backdrop-blur-xl dark:border-white/10 dark:bg-black/60";
