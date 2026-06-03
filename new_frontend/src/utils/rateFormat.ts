export type RateUnit = "B" | "KB" | "MB" | "GB";

type RateUnitMeta = {
  unit: RateUnit;
  bytes: number;
};

export type RateUnitLabels = Record<RateUnit, string>;

export type RateValueDraft = {
  value: string;
  unit: RateUnit;
};

const RATE_UNIT_META: RateUnitMeta[] = [
  { unit: "GB", bytes: 1024 ** 3 },
  { unit: "MB", bytes: 1024 ** 2 },
  { unit: "KB", bytes: 1024 },
  { unit: "B", bytes: 1 },
];

const DEFAULT_RATE_UNIT_LABELS: RateUnitLabels = {
  B: "B/s",
  KB: "KB/s",
  MB: "MB/s",
  GB: "GB/s",
};

function resolveUnitMeta(unit: RateUnit): RateUnitMeta {
  return RATE_UNIT_META.find((item) => item.unit === unit) ?? RATE_UNIT_META[RATE_UNIT_META.length - 1];
}

function resolveUnitLabel(labels: Partial<RateUnitLabels> | undefined, unit: RateUnit): string {
  return labels?.[unit] ?? DEFAULT_RATE_UNIT_LABELS[unit];
}

function resolveDisplayUnit(value: number, allowedUnits: RateUnit[]): RateUnit {
  const safe = Math.max(0, value);
  const available = new Set(allowedUnits);
  for (const meta of RATE_UNIT_META) {
    if (!available.has(meta.unit)) {
      continue;
    }
    if (safe >= meta.bytes || meta.unit === "B") {
      return meta.unit;
    }
  }
  return allowedUnits[0] ?? "B";
}

/**
 * 速率自动格式化：按数据量级选择最合适的单位，保留读数稳定性，避免界面出现大量 1B/s 级噪声。
 */
export function formatRateBps(value: number, options?: { labels?: Partial<RateUnitLabels>; precision?: number }): string {
  const safe = Math.max(0, Number(value ?? 0));
  const unit = resolveDisplayUnit(safe, ["B", "KB", "MB", "GB"]);
  const meta = resolveUnitMeta(unit);
  const precision = options?.precision ?? (unit === "B" ? 0 : 1);
  const suffix = resolveUnitLabel(options?.labels, unit);
  return `${(safe / meta.bytes).toFixed(precision)} ${suffix}`;
}

/**
 * 将 B/s 拆成输入框可编辑的“数值 + 单位”组合，优先使用允许范围内更大的单位，减少小数输入的负担。
 */
export function decomposeRateBps(value: number, allowedUnits: RateUnit[]): RateValueDraft {
  return decomposeRateBpsWithPrecision(value, allowedUnits);
}

/**
 * 按指定允许单位拆分速率输入值；用于编辑场景保留更高精度，避免切换单位时丢失原始 B/s 数值。
 */
export function decomposeRateBpsWithPrecision(value: number, allowedUnits: RateUnit[], precision?: number): RateValueDraft {
  const safe = Math.max(0, Number(value ?? 0));
  const unit = resolveDisplayUnit(safe, allowedUnits);
  const meta = resolveUnitMeta(unit);
  const digits = precision ?? (unit === "B" ? 0 : 2);
  return {
    value: (safe / meta.bytes).toFixed(digits),
    unit,
  };
}

/**
 * 将编辑框中的速率值转换回 B/s；输入为空或非法时返回 null，由调用方维持当前保存值。
 */
export function parseRateValueToBps(value: string, unit: RateUnit): number | null {
  const numeric = Number(String(value ?? "").trim());
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return null;
  }
  return Math.round(numeric * resolveUnitMeta(unit).bytes);
}

/**
 * 生成速率单位选项；由调用方决定可用单位集合，组件层负责把选项文案接到 i18n。
 */
export function getRateUnitOptions(allowedUnits: RateUnit[], labels?: Partial<RateUnitLabels>): Array<{ unit: RateUnit; label: string }> {
  return allowedUnits.map((unit) => ({ unit, label: resolveUnitLabel(labels, unit) }));
}
