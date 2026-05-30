import type { ReactNode } from "react";

export default function MonitorMetricCard({ title, icon, children }: { title: string; icon: ReactNode; children: ReactNode }) {
  return (
    <section className="overflow-hidden rounded-sm border border-white/40 bg-white/60 shadow-sm backdrop-blur-xl dark:border-white/10 dark:bg-black/40">
      <header className="flex items-center gap-2 border-b border-white/30 bg-white/40 px-4 py-3 dark:border-white/10 dark:bg-black/25">
        <span className="text-default-500 dark:text-white">{icon}</span>
        <h3 className="text-base font-semibold tracking-tight text-default-800 dark:text-white">{title}</h3>
      </header>
      <div className="px-4 py-4">{children}</div>
    </section>
  );
}
