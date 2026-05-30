import fs from "node:fs";
import path from "node:path";

const root = path.resolve(process.cwd(), "src");
const zhPath = path.resolve(root, "i18n/resources/zh.ts");
const enPath = path.resolve(root, "i18n/resources/en.ts");
const strictMode = process.argv.includes("--strict");

function loadResource(filePath) {
  const text = fs.readFileSync(filePath, "utf8");
  const normalized = text
    .replace(/^export const \w+\s*=\s*/m, "")
    .replace(/\s+as const;\s*$/m, "")
    .trim();
  return Function(`return (${normalized});`)();
}

function flatten(obj, prefix = "") {
  const out = new Set();
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === "object" && !Array.isArray(v)) {
      for (const child of flatten(v, key)) out.add(child);
    } else {
      out.add(key);
    }
  }
  return out;
}

function collectUsedKeys(dir) {
  const patterns = [
    /\bt\(\s*["'`]([^"'`]+)["'`]/g,
    /\bi18n\.t\(\s*["'`]([^"'`]+)["'`]/g,
  ];
  const used = new Set();
  const files = [];
  const walk = (d) => {
    for (const ent of fs.readdirSync(d, { withFileTypes: true })) {
      if (ent.name === "i18n" || ent.name === "node_modules" || ent.name === "dist") continue;
      const abs = path.join(d, ent.name);
      if (ent.isDirectory()) walk(abs);
      else if (/\.(ts|tsx)$/.test(ent.name)) files.push(abs);
    }
  };
  walk(dir);
  for (const file of files) {
    const txt = fs.readFileSync(file, "utf8");
    for (const pattern of patterns) {
      pattern.lastIndex = 0;
      let m;
      while ((m = pattern.exec(txt)) !== null) {
        if (m[1].includes("${")) continue;
        used.add(m[1]);
      }
    }
  }
  return used;
}

function collectHardcodedHanInJsx(dir) {
  const offenders = [];
  const walk = (d) => {
    for (const ent of fs.readdirSync(d, { withFileTypes: true })) {
      if (ent.name === "i18n" || ent.name === "node_modules" || ent.name === "dist") continue;
      const abs = path.join(d, ent.name);
      if (ent.isDirectory()) walk(abs);
      else if (ent.name.endsWith(".tsx")) {
        const txt = fs.readFileSync(abs, "utf8");
        const hasZhJsxText = />[^<>{}]*[\u4e00-\u9fff][^<>{}]*</.test(txt);
        const hasZhJsxAttr = /=\s*"[^"]*[\u4e00-\u9fff][^"]*"/.test(txt);
        if (hasZhJsxText || hasZhJsxAttr) offenders.push(path.relative(process.cwd(), abs));
      }
    }
  };
  walk(dir);
  return offenders;
}

function collectHardcodedTextLiterals(dir) {
  const offenders = [];
  const userFacingAttrs = new Set(["label", "placeholder", "title", "aria-label", "description", "helperText", "text"]);
  function isLikelyUserText(raw) {
    const text = raw.trim();
    if (!text) return false;
    if (text.length < 2) return false;
    if (/^[A-Z0-9_\-:.]+$/.test(text)) return false;
    if (/^[a-z0-9_\-:.\/]+$/.test(text)) return false;
    return /[\u4e00-\u9fff]/.test(text) || /[A-Za-z]/.test(text);
  }
  const walk = (d) => {
    for (const ent of fs.readdirSync(d, { withFileTypes: true })) {
      if (ent.name === "i18n" || ent.name === "node_modules" || ent.name === "dist") continue;
      const abs = path.join(d, ent.name);
      if (ent.isDirectory()) walk(abs);
      else if (ent.name.endsWith(".tsx")) {
        const txt = fs.readFileSync(abs, "utf8");
        const lines = txt.split(/\r?\n/);
        for (let i = 0; i < lines.length; i += 1) {
          const line = lines[i] ?? "";
          if (line.includes("t(") || line.includes("i18n.t(")) continue;
          const jsxText = line.match(/>([^<>{]+)</);
          const jsxValue = (jsxText?.[1] ?? "").trim();
          if (jsxText && !/[&|=()]/.test(jsxValue) && isLikelyUserText(jsxValue)) {
            offenders.push(`${path.relative(process.cwd(), abs)}:${i + 1}:jsx:${jsxValue}`);
            continue;
          }
          const attrMatches = Array.from(line.matchAll(/\b([a-zA-Z][\w-]*)\s*=\s*"([^"]+)"/g));
          for (const m of attrMatches) {
            const attrName = m[1] ?? "";
            const attrValue = m[2] ?? "";
            if (!userFacingAttrs.has(attrName)) continue;
            if (!/[\u4e00-\u9fff]/.test(attrValue) && !/\s/.test(attrValue)) continue;
            if (!isLikelyUserText(attrValue)) continue;
            offenders.push(`${path.relative(process.cwd(), abs)}:${i + 1}:attr:${attrValue.trim()}`);
          }
        }
      }
    }
  };
  walk(dir);
  return offenders;
}

const zh = loadResource(zhPath);
const en = loadResource(enPath);
const zhKeys = flatten(zh.translation || {});
const enKeys = flatten(en.translation || {});
const usedKeys = collectUsedKeys(root);

const missingInEn = [...zhKeys].filter((k) => !enKeys.has(k));
const missingInZh = [...enKeys].filter((k) => !zhKeys.has(k));
const missingInLocale = [...usedKeys].filter((k) => !zhKeys.has(k) || !enKeys.has(k));
const orphanKeys = [...zhKeys].filter((k) => !usedKeys.has(k));
const hardcodedHanInJsx = collectHardcodedHanInJsx(root);
const hardcodedTextLiterals = collectHardcodedTextLiterals(root);

let failed = false;
function printGroup(title, list) {
  if (!list.length) return;
  failed = true;
  console.error(`\n[${title}]`);
  for (const item of list.sort()) console.error(`- ${item}`);
}

printGroup("missing_in_en", missingInEn);
printGroup("missing_in_zh", missingInZh);
printGroup("used_key_missing_in_locale", missingInLocale);
printGroup("hardcoded_han_in_tsx", hardcodedHanInJsx);
if (strictMode) {
  printGroup("hardcoded_text_literals", hardcodedTextLiterals);
}

const orphanReport = path.resolve(process.cwd(), "i18n-orphan-report.txt");
fs.writeFileSync(orphanReport, orphanKeys.sort().join("\n"));
console.log(`i18n orphan report written: ${path.relative(process.cwd(), orphanReport)} (${orphanKeys.length} keys)`);
const hardcodeReport = path.resolve(process.cwd(), "i18n-hardcode-report.txt");
fs.writeFileSync(hardcodeReport, hardcodedTextLiterals.sort().join("\n"));
console.log(`i18n hardcode report written: ${path.relative(process.cwd(), hardcodeReport)} (${hardcodedTextLiterals.length} findings)`);

if (failed) {
  process.exit(1);
}

console.log("i18n check passed");
