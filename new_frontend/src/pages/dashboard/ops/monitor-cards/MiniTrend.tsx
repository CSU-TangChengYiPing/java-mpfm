import { Line, LineChart, ResponsiveContainer } from "recharts";

export default function MiniTrend({ values, color = "#60a5fa" }: { values: number[]; color?: string }) {
  const data = values.slice(-40).map((value, idx) => ({ idx, value }));
  return (
    <div className="mt-3 h-14 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data}>
          <Line type="monotone" dataKey="value" stroke={color} strokeWidth={2} dot={false} isAnimationActive={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
