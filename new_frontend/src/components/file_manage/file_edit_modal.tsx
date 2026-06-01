import { Button } from "@heroui/button";
import { ModalBody, ModalContent, ModalFooter, ModalHeader } from "@heroui/modal";
import { Spinner } from "@heroui/spinner";
import type { Extension } from "@codemirror/state";
import { StreamLanguage } from "@codemirror/language";
import { shell as shellMode } from "@codemirror/legacy-modes/mode/shell";
import { cpp } from "@codemirror/lang-cpp";
import { css } from "@codemirror/lang-css";
import { go } from "@codemirror/lang-go";
import { html } from "@codemirror/lang-html";
import { java } from "@codemirror/lang-java";
import { javascript } from "@codemirror/lang-javascript";
import { json } from "@codemirror/lang-json";
import { markdown } from "@codemirror/lang-markdown";
import { php } from "@codemirror/lang-php";
import { python } from "@codemirror/lang-python";
import { rust } from "@codemirror/lang-rust";
import { sql } from "@codemirror/lang-sql";
import { xml } from "@codemirror/lang-xml";
import { yaml } from "@codemirror/lang-yaml";
import CodeMirror from "@uiw/react-codemirror";
import { EditorView } from "@codemirror/view";
import path from "path-browserify";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import BlurModal from "../common/BlurModal";

export const fileEditTextareaClassNames = { className: "mpfm-large-glass-field" };
export const fileEditModalSize = "5xl";
export const fileEditModalClassNames = "box-border flex h-[88dvh] w-[96vw] md:h-[86dvh] md:w-[88vw] lg:h-[84dvh] lg:w-[80vw] max-h-[960px] overflow-hidden flex-col";
export const fileEditEditorShellClassName = "relative flex h-full min-h-0 w-full flex-1 min-w-0 overflow-hidden rounded-md border border-default-200/60 bg-content1 shadow-sm dark:border-default-700/70";
export const fileEditLoadingShellClassName = "relative flex h-full min-h-0 w-full flex-1 min-w-0 overflow-hidden rounded-md border border-default-200/60 bg-content1 shadow-sm dark:border-default-700/70";
export const fileEditCodeMirrorClassName = "h-full w-full min-w-0 overflow-hidden";

type EditorLanguageKey = "plain" | "markdown" | "json" | "typescript" | "javascript" | "java" | "python" | "shell" | "yaml" | "xml" | "html" | "css" | "sql" | "rust" | "go" | "php" | "cpp";

export function resolveEditorLanguageKey(filePath?: string): EditorLanguageKey {
  const ext = path.extname(filePath || "").toLowerCase();
  if (ext === ".md" || ext === ".markdown") return "markdown";
  if (ext === ".json") return "json";
  if (ext === ".ts" || ext === ".tsx") return "typescript";
  if (ext === ".js" || ext === ".jsx" || ext === ".mjs" || ext === ".cjs") return "javascript";
  if (ext === ".java") return "java";
  if (ext === ".py") return "python";
  if (ext === ".sh" || ext === ".bash" || ext === ".zsh") return "shell";
  if (ext === ".yml" || ext === ".yaml") return "yaml";
  if (ext === ".xml") return "xml";
  if (ext === ".html" || ext === ".htm") return "html";
  if (ext === ".css") return "css";
  if (ext === ".sql") return "sql";
  if (ext === ".rs") return "rust";
  if (ext === ".go") return "go";
  if (ext === ".php") return "php";
  if (ext === ".c" || ext === ".cc" || ext === ".cpp" || ext === ".h" || ext === ".hpp") return "cpp";
  return "plain";
}

function resolveLanguageExtension(languageKey: EditorLanguageKey): Extension | null {
  if (languageKey === "markdown") return markdown();
  if (languageKey === "json") return json();
  if (languageKey === "typescript") return javascript({ typescript: true, jsx: true });
  if (languageKey === "javascript") return javascript({ jsx: true });
  if (languageKey === "java") return java();
  if (languageKey === "python") return python();
  if (languageKey === "shell") return StreamLanguage.define(shellMode);
  if (languageKey === "yaml") return yaml();
  if (languageKey === "xml") return xml();
  if (languageKey === "html") return html();
  if (languageKey === "css") return css();
  if (languageKey === "sql") return sql();
  if (languageKey === "rust") return rust();
  if (languageKey === "go") return go();
  if (languageKey === "php") return php();
  if (languageKey === "cpp") return cpp();
  return null;
}

export function resolveEditorThemeMode(isDark: boolean): "dark" | "light" {
  return isDark ? "dark" : "light";
}

function createEditorTheme(isDark: boolean): Extension {
  const background = isDark ? "#0f172a" : "#ffffff";
  const foreground = isDark ? "#e2e8f0" : "#0f172a";
  const gutterBg = isDark ? "#111827" : "#f8fafc";
  const gutterFg = isDark ? "#64748b" : "#94a3b8";
  const borderColor = isDark ? "#334155" : "#e2e8f0";
  const activeLine = isDark ? "rgba(148, 163, 184, 0.16)" : "rgba(15, 23, 42, 0.05)";
  const selection = isDark ? "rgba(56, 189, 248, 0.28)" : "rgba(59, 130, 246, 0.18)";
  return EditorView.theme({
    "&": {
      color: foreground,
      backgroundColor: background,
      height: "100%",
      width: "100%",
      minWidth: 0,
      maxWidth: "100%",
      overflow: "hidden",
    },
    ".cm-editor": {
      height: "100%",
      width: "100%",
      minWidth: 0,
      maxWidth: "100%",
      overflow: "hidden",
    },
    ".cm-scroller": {
      height: "100%",
      width: "100%",
      minWidth: 0,
      maxWidth: "100%",
      fontFamily:
        "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, Liberation Mono, monospace",
      lineHeight: "1.7",
      overflowX: "auto",
    },
    ".cm-content, .cm-gutters": {
      minHeight: "100%",
    },
    ".cm-gutters": {
      backgroundColor: gutterBg,
      color: gutterFg,
      borderRight: `1px solid ${borderColor}`,
    },
    ".cm-activeLine, .cm-activeLineGutter": {
      backgroundColor: activeLine,
    },
    ".cm-cursor, .cm-dropCursor": {
      borderLeftColor: foreground,
    },
    ".cm-selectionBackground, ::selection": {
      backgroundColor: selection,
    },
  }, { dark: isDark });
}

export default function FileEditModal({ isOpen, file, isSaving, isLoading, onClose, onSave }: { isOpen: boolean; file: { path: string; content: string } | null; isSaving: boolean; isLoading: boolean; onClose: () => void; onSave: (content: string) => void }) {
  const { t } = useTranslation();
  const [isDark, setIsDark] = useState(() => typeof document !== "undefined" && document.documentElement.classList.contains("dark"));
  const [draft, setDraft] = useState(file?.content ?? "");
  const languageKey = resolveEditorLanguageKey(file?.path);
  const languageExtension = useMemo(() => resolveLanguageExtension(languageKey), [languageKey]);
  const editorTheme = useMemo(() => createEditorTheme(isDark), [isDark]);
  const extensions = useMemo(
    () => (languageExtension ? [EditorView.lineWrapping, languageExtension, editorTheme] : [EditorView.lineWrapping, editorTheme]),
    [editorTheme, languageExtension],
  );

  useEffect(() => {
    const root = document.documentElement;
    const syncTheme = () => setIsDark(root.classList.contains("dark"));
    syncTheme();
    const observer = new MutationObserver(syncTheme);
    observer.observe(root, { attributes: true, attributeFilter: ["class"] });
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!isOpen) return;
    setDraft(file?.content ?? "");
  }, [file?.content, file?.path, isOpen]);

  return (
    <BlurModal
      radius="sm"
      size={fileEditModalSize}
      isOpen={isOpen}
      onClose={onClose}
      scrollBehavior="inside"
    >
      <ModalContent className={fileEditModalClassNames}>
        <div className="flex h-full min-h-0 w-full min-w-0 flex-col">
          <ModalHeader className="flex-shrink-0 border-b border-default-200/50">{t("fileManager.editFile")}: {file?.path}</ModalHeader>
          <ModalBody className="flex min-h-0 flex-1 overflow-hidden p-4">
            <div className={isLoading ? fileEditLoadingShellClassName : fileEditEditorShellClassName}>
              {isLoading ? (
                <div className="flex h-full w-full flex-col gap-3 p-4">
                  <div className="flex items-center justify-between">
                    <div className="h-4 w-40 rounded bg-default-200/80 dark:bg-default-700/70" />
                    <Spinner size="sm" />
                  </div>
                  <div className="flex-1 rounded-md border border-dashed border-default-200/80 bg-default-50/70 dark:border-default-700/70 dark:bg-default-950/20">
                    <div className="h-full w-full animate-pulse bg-[linear-gradient(180deg,transparent_0,transparent_18px,rgba(148,163,184,0.08)_18px,rgba(148,163,184,0.08)_19px,transparent_19px)] bg-[length:100%_1.8rem]" />
                  </div>
                  <div className="flex items-center gap-2 text-sm text-default-500">
                    <span>{t("fileManager.editLoading")}</span>
                  </div>
                </div>
              ) : (
                <>
                  <CodeMirror
                    value={draft}
                    height="100%"
                    minHeight="100%"
                    width="100%"
                    minWidth="0"
                    maxWidth="100%"
                    className={fileEditCodeMirrorClassName}
                    theme={resolveEditorThemeMode(isDark)}
                    editable={!isSaving && !isLoading}
                    basicSetup={{
                      lineNumbers: true,
                      highlightActiveLine: true,
                      foldGutter: true,
                    }}
                    extensions={extensions}
                    onChange={(value) => setDraft(value)}
                    onKeyDown={(event) => {
                      if (isSaving || isLoading) return;
                      if ((event.ctrlKey || event.metaKey) && event.key === "s") {
                        event.preventDefault();
                        onSave(draft);
                      }
                    }}
                  />
                </>
              )}
            </div>
          </ModalBody>
          <ModalFooter className="flex-shrink-0 border-t border-default-200/50">
            <Button radius="sm" color="primary" variant="flat" onPress={onClose}>{t("common.cancel")}</Button>
            <Button radius="sm" color="primary" isDisabled={isSaving || isLoading} isLoading={isSaving} onPress={() => onSave(draft)}>{t("common.save")}</Button>
          </ModalFooter>
        </div>
      </ModalContent>
    </BlurModal>
  );
}
