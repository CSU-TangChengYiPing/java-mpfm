import fs from "node:fs";
import path from "node:path";
import ts from "typescript";

const ROOT = process.cwd();
const SRC_DIR = path.join(ROOT, "src");
const DEFAULT_MIN_LINES = Number(process.env.COMMENT_LONG_FUNC_MIN_LINES ?? 40);
const STRICT_MIN_LINES = Number(process.env.COMMENT_LONG_FUNC_STRICT_MIN_LINES ?? 35);

/** 递归收集 ts/tsx 源文件，排除测试文件。 */
function collectSourceFiles(dir) {
  const out = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...collectSourceFiles(full));
      continue;
    }
    if (!/\.(ts|tsx)$/.test(entry.name)) continue;
    if (/\.test\.(ts|tsx)$/.test(entry.name)) continue;
    out.push(full);
  }
  return out;
}

function getFunctionName(node) {
  if (ts.isFunctionDeclaration(node) && node.name) return node.name.getText();
  if (ts.isMethodDeclaration(node) && node.name) return node.name.getText();
  if (ts.isFunctionExpression(node) || ts.isArrowFunction(node)) {
    if (node.parent && ts.isVariableDeclaration(node.parent) && ts.isIdentifier(node.parent.name)) {
      return node.parent.name.text;
    }
    if (node.parent && ts.isPropertyAssignment(node.parent)) return node.parent.name.getText();
  }
  return "<anonymous>";
}

function hasJsDoc(node) {
  if (Array.isArray(node.jsDoc) && node.jsDoc.length > 0) return true;
  if (node.parent && ts.isVariableDeclaration(node.parent)) {
    const declList = node.parent.parent;
    const stmt = declList && declList.parent;
    if (stmt && Array.isArray(stmt.jsDoc) && stmt.jsDoc.length > 0) return true;
  }
  return false;
}

function isTargetFunctionNode(node) {
  if (ts.isFunctionDeclaration(node)) return true;
  if (ts.isMethodDeclaration(node)) return true;
  if (ts.isArrowFunction(node) || ts.isFunctionExpression(node)) {
    if (node.parent && ts.isVariableDeclaration(node.parent) && ts.isIdentifier(node.parent.name)) return true;
  }
  return false;
}

function resolveMinLines(relativePath) {
  if (relativePath.startsWith("src/controllers/")) return STRICT_MIN_LINES;
  if (relativePath.startsWith("src/pages/dashboard/")) return STRICT_MIN_LINES;
  return DEFAULT_MIN_LINES;
}

const violations = [];
for (const filePath of collectSourceFiles(SRC_DIR)) {
  const relativePath = path.relative(ROOT, filePath).replaceAll("\\", "/");
  const minLines = resolveMinLines(relativePath);
  const sourceText = fs.readFileSync(filePath, "utf8");
  const sf = ts.createSourceFile(filePath, sourceText, ts.ScriptTarget.Latest, true, filePath.endsWith(".tsx") ? ts.ScriptKind.TSX : ts.ScriptKind.TS);

  const visit = (node) => {
    if (isTargetFunctionNode(node)) {
      const start = sf.getLineAndCharacterOfPosition(node.getStart(sf)).line + 1;
      const end = sf.getLineAndCharacterOfPosition(node.getEnd()).line + 1;
      const lineSpan = end - start + 1;
      if (lineSpan >= minLines && !hasJsDoc(node)) {
        violations.push({
          filePath: relativePath,
          start,
          name: getFunctionName(node),
          lineSpan,
          minLines,
        });
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
}

if (violations.length > 0) {
  console.error(`[comment-long-function-check] 发现 ${violations.length} 个长函数缺失 JSDoc（默认阈值: ${DEFAULT_MIN_LINES} 行；核心模块阈值: ${STRICT_MIN_LINES} 行）`);
  for (const item of violations) {
    console.error(`- ${item.filePath}:${item.start}  ${item.name} (${item.lineSpan} 行, 阈值 ${item.minLines})`);
  }
  process.exit(1);
}

console.log(`[comment-long-function-check] 通过：未发现达到阈值且缺失 JSDoc 的函数（默认 ${DEFAULT_MIN_LINES} 行；核心模块 ${STRICT_MIN_LINES} 行）。`);
