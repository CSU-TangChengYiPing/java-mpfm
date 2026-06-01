type PermissionLevel = 0 | 1 | 2 | 3;

export default function PermissionLevelSelector({
  level,
  onChange,
  label = "权限等级",
}: {
  level: PermissionLevel;
  onChange: (level: PermissionLevel) => void;
  label?: string;
}) {
  return (
    <div className="space-y-2">
      <div className="text-xs text-default-500">{label}</div>
      <div className="relative px-2 pt-3 pb-8">
        <div className="absolute left-2 right-2 top-5 h-1 overflow-hidden rounded-full bg-default-200">
          <div
            className="h-full rounded-full bg-primary transition-all duration-200"
            style={{ width: `${Math.max(0, Math.min(100, (level / 3) * 100))}%` }}
          />
        </div>
        <div className="absolute left-2 right-2 top-5 z-10 flex -translate-y-1/2 items-center justify-between">
          {[0, 1, 2, 3].map((lv) => (
            <button
              key={lv}
              type="button"
              onClick={() => onChange(lv as PermissionLevel)}
              className={`inline-flex h-5 w-5 items-center justify-center rounded-full border-2 transition-colors ${
                level >= lv ? "border-blue-800 bg-primary" : "border-default-400 bg-default-200"
              }`}
            />
          ))}
        </div>
        <div className="absolute left-2 right-2 top-8 flex items-center justify-between text-[11px] text-default-500">
          {(["none", "visible", "read", "write"] as const).map((text, idx) => (
            <button
              key={text}
              type="button"
              onClick={() => onChange(idx as PermissionLevel)}
              className={level >= idx ? "text-primary font-semibold" : ""}
            >
              {text}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
