# PR 标题
请使用：`动作（范围）：中文说明`

## 1. 变更说明
- 本次解决的问题：
- 主要改动点：
- 影响范围：

## 2. 质量门禁
- [ ] `pnpm --dir new_frontend gate:fast` 已通过（i18n/lint/typecheck/test）
- [ ] `pnpm --dir new_frontend gate:full` 已通过（含 build）
- [ ] `pnpm --dir new_frontend a11y` 已通过
- [ ] `pnpm --dir new_frontend perf:bundle` 已通过
- [ ] `pnpm --dir new_frontend audit:deps` 已通过（high/critical=0）
- [ ] 后端改动已执行 `scripts/ps1/test-backend.ps1`（如适用）

## 3. 前端注释门禁（强制）
- [ ] 已按 `new_frontend/docs/comment-quality-standard.md` 自检
- [ ] 强制注释场景已覆盖（公共契约/风险逻辑/异常映射/非直观实现）
- [ ] 未出现模板化空话、机械复述、过期注释
- [ ] `@ts-ignore/@ts-expect-error/eslint-disable` 均包含中文原因
- [ ] 注释与实现一致，且可在实现/调用链中找到依据

## 4. 文档一致性
- [ ] 涉及前端规则变更，已同步 `new_frontend/docs/quality-gate.md`
- [ ] 涉及全局约束变更，已同步 `AGENTS.md`
- [ ] 涉及需求/API/Schema 变更，已同步 `docs/计划` 对应文档（如适用）

## 5. 验证证据
- 命令与结果：
- 截图/日志/报告路径：

## 6. 风险与回滚
- 主要风险：
- 回滚方案：
