import { Area, CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

type SeriesPoint = Record<string, number | string>;

export default function TrendChart({
  data,
  xKey,
  series,
  yUnit,
  isDark,
  formatValue,
}: {
  data: SeriesPoint[];
  xKey: string;
  series: Array<{ key: string; name: string; color: string; fill: string }>;
  yUnit?: string;
  isDark: boolean;
  formatValue?: (value: number) => string;
}) {
  return (
    <div className="mt-3 h-44 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={data}>
          <defs>
            {series.map((item) => (
              <linearGradient key={item.key} id={`${item.key}Gradient`} x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor={item.fill} stopOpacity={0.24} />
                <stop offset="95%" stopColor={item.fill} stopOpacity={0.04} />
              </linearGradient>
            ))}
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(120,120,120,0.2)" />
          <XAxis dataKey={xKey} tick={{ fontSize: 11 }} minTickGap={18} />
          <YAxis
            tick={{ fontSize: 11 }}
            unit={yUnit}
            tickFormatter={(value) => (formatValue ? formatValue(Number(value)) : `${value}${yUnit ?? ""}`)}
          />
          <Tooltip
            formatter={(value, name) => [formatValue ? formatValue(Number(value)) : `${value}${yUnit ?? ""}`, name]}
            contentStyle={{
              backgroundColor: isDark ? "rgba(20,20,22,0.92)" : "rgba(255,255,255,0.95)",
              border: isDark ? "1px solid rgba(255,255,255,0.16)" : "1px solid rgba(15,23,42,0.12)",
              borderRadius: 8,
              color: isDark ? "#e5e7eb" : "#0f172a",
            }}
            labelStyle={{ color: isDark ? "#f3f4f6" : "#111827" }}
            itemStyle={{ color: isDark ? "#d1d5db" : "#1f2937" }}
          />
          <Legend />
          {series.map((item) => (
            <Area key={`${item.key}-area`} type="monotone" dataKey={item.key} fill={`url(#${item.key}Gradient)`} stroke="none" isAnimationActive={false} name="" legendType="none" tooltipType="none" />
          ))}
          {series.map((item) => (
            <Line key={`${item.key}-line`} type="monotone" dataKey={item.key} stroke={item.color} dot={false} strokeWidth={2} isAnimationActive={false} name={item.name} />
          ))}
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}
