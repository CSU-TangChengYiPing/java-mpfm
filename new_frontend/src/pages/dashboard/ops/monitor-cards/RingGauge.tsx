export default function RingGauge({ label, usedText, totalText, percent, color }: { label: string; usedText: string; totalText: string; percent: number; color: string }) {
  const safe = Math.max(0, Math.min(100, percent));
  const style = {
    background: `conic-gradient(${color} ${safe}%, #e5e7eb ${safe}% 100%)`,
  };
  return (
    <div className="flex flex-col items-center gap-2">
      <div className="relative h-28 w-28 rounded-full" style={style}>
        <div className="absolute left-1/2 top-1/2 flex h-20 w-20 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-default-50 text-sm font-semibold text-default-700 dark:bg-black/50 dark:text-white">
          {usedText}
        </div>
      </div>
      <p className="text-sm font-medium text-default-700 dark:text-white">{label}</p>
      <p className="text-sm text-default-500 dark:text-white">{totalText}</p>
    </div>
  );
}
