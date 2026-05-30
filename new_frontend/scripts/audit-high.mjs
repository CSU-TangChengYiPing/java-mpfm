import { spawnSync } from "node:child_process";

const result = spawnSync("pnpm audit --json", { encoding: "utf-8", shell: true });
const raw = `${result.stdout ?? ""}\n${result.stderr ?? ""}`.trim();
if (!raw) {
  console.error("[audit-high] 无法获取审计输出");
  process.exit(1);
}

let payload;
try {
  const firstBrace = raw.indexOf("{");
  const lastBrace = raw.lastIndexOf("}");
  const jsonText = firstBrace >= 0 && lastBrace > firstBrace ? raw.slice(firstBrace, lastBrace + 1) : raw;
  payload = JSON.parse(jsonText);
} catch (error) {
  console.error("[audit-high] JSON 解析失败");
  console.error(raw);
  process.exit(1);
}

const vulnerabilities = payload?.metadata?.vulnerabilities ?? {};
const high = Number(vulnerabilities.high ?? 0);
const critical = Number(vulnerabilities.critical ?? 0);
const moderate = Number(vulnerabilities.moderate ?? 0);
const low = Number(vulnerabilities.low ?? 0);

console.log(`[audit-high] critical=${critical}, high=${high}, moderate=${moderate}, low=${low}`);

if (critical > 0 || high > 0) {
  console.error("[audit-high] 检测到 high/critical 漏洞，门禁失败");
  process.exit(1);
}

console.log("[audit-high] 未检测到 high/critical 漏洞，门禁通过");
