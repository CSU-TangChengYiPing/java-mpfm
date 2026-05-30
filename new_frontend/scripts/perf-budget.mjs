import { readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const distAssetsDir = join(process.cwd(), "dist", "assets");
const maxLargestJsBytes = 380 * 1024;
const maxTotalJsBytes = 1600 * 1024;
const maxMainCssBytes = 320 * 1024;

function formatKB(bytes) {
  return `${(bytes / 1024).toFixed(1)}KB`;
}

const files = readdirSync(distAssetsDir);
const jsFiles = files.filter((name) => name.endsWith(".js"));
const cssFiles = files.filter((name) => name.endsWith(".css"));

const jsSizes = jsFiles.map((name) => ({ name, size: statSync(join(distAssetsDir, name)).size }));
const cssSizes = cssFiles.map((name) => ({ name, size: statSync(join(distAssetsDir, name)).size }));

const largestJs = jsSizes.sort((a, b) => b.size - a.size)[0] ?? { name: "(none)", size: 0 };
const totalJs = jsSizes.reduce((sum, item) => sum + item.size, 0);
const largestCss = cssSizes.sort((a, b) => b.size - a.size)[0] ?? { name: "(none)", size: 0 };

const violations = [];
if (largestJs.size > maxLargestJsBytes) {
  violations.push(`largest js too large: ${largestJs.name}=${formatKB(largestJs.size)} > ${formatKB(maxLargestJsBytes)}`);
}
if (totalJs > maxTotalJsBytes) {
  violations.push(`total js too large: ${formatKB(totalJs)} > ${formatKB(maxTotalJsBytes)}`);
}
if (largestCss.size > maxMainCssBytes) {
  violations.push(`largest css too large: ${largestCss.name}=${formatKB(largestCss.size)} > ${formatKB(maxMainCssBytes)}`);
}

console.log("[perf-budget] largest js:", largestJs.name, formatKB(largestJs.size));
console.log("[perf-budget] total js:", formatKB(totalJs));
console.log("[perf-budget] largest css:", largestCss.name, formatKB(largestCss.size));

if (violations.length > 0) {
  console.error("[perf-budget] failed:");
  for (const item of violations) {
    console.error(`- ${item}`);
  }
  process.exit(1);
}

console.log("[perf-budget] passed");
