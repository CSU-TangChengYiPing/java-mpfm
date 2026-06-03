import { Select, SelectItem } from "@heroui/select";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { LargeGlassInput } from "./LargeGlassField";
import { decomposeRateBps, decomposeRateBpsWithPrecision, getRateUnitOptions, parseRateValueToBps, type RateUnit } from "../../utils/rateFormat";

type RateInputFieldProps = {
  label: string;
  valueBps: number;
  onValueChangeBps: (value: number) => void;
  allowedUnits?: RateUnit[];
};

/**
 * 速率输入组件：把“数值 + 单位”的交互收敛到一个控件里，避免各页面重复实现换算和回填逻辑。
 */
export default function RateInputField({
  label,
  valueBps,
  onValueChangeBps,
  allowedUnits = ["B", "KB", "MB", "GB"],
}: RateInputFieldProps) {
  const { t } = useTranslation();
  const allowedUnitsKey = allowedUnits.join("|");
  const unitLabels = useMemo(() => ({
    B: t("common.rateUnits.b"),
    KB: t("common.rateUnits.kb"),
    MB: t("common.rateUnits.mb"),
    GB: t("common.rateUnits.gb"),
  }), [t]);
  const unitOptions = useMemo(() => getRateUnitOptions(allowedUnits, unitLabels), [allowedUnitsKey, unitLabels]);
  const [draft, setDraft] = useState(() => decomposeRateBps(valueBps, allowedUnits));

  useEffect(() => {
    setDraft(decomposeRateBps(valueBps, allowedUnits));
  }, [allowedUnitsKey, valueBps]);

  return (
    <label className="flex flex-col gap-1">
      <span className="text-sm text-default-700 dark:text-default-300">{label}</span>
      <div className="grid grid-cols-[minmax(0,1fr)_110px] gap-2">
        <LargeGlassInput
          type="number"
          inputMode="decimal"
          step="any"
          min="0"
          size="sm"
          value={draft.value}
          onValueChange={(next) => {
            setDraft((prev) => {
              const updated = { ...prev, value: next };
              const parsed = parseRateValueToBps(updated.value, updated.unit);
              if (parsed !== null) {
                onValueChangeBps(parsed);
              }
              return updated;
            });
          }}
          onBlur={() => {
            const parsed = parseRateValueToBps(draft.value, draft.unit);
            if (parsed === null) {
              setDraft(decomposeRateBps(valueBps, allowedUnits));
            }
          }}
        />
        <Select
          aria-label={label}
          disableAnimation
          disallowEmptySelection
          radius="sm"
          size="sm"
          selectedKeys={[draft.unit]}
          classNames={{ trigger: "h-10 min-h-10", value: "text-xs" }}
          onSelectionChange={(keys) => {
            const nextUnit = String(Array.from(keys)[0] || draft.unit) as RateUnit;
            setDraft((prev) => {
              const parsed = parseRateValueToBps(prev.value, prev.unit);
              if (parsed === null) {
                return { ...prev, unit: nextUnit };
              }
              const nextDraft = decomposeRateBpsWithPrecision(parsed, [nextUnit], nextUnit === "B" ? 0 : 3);
              onValueChangeBps(parsed);
              return nextDraft;
            });
          }}
        >
          {unitOptions.map((item) => (
            <SelectItem key={item.unit}>{item.label}</SelectItem>
          ))}
        </Select>
      </div>
    </label>
  );
}
