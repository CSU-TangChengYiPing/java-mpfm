import { Input, Textarea, type InputProps, type TextAreaProps } from "@heroui/input";
import clsx from "clsx";
import { useEffect, useState } from "react";

export const largeGlassFieldRootClass = "mpfm-large-glass-field";

type CommitMode = "immediate" | "blur";

export function resolveDraftValue(value: unknown): string {
  return typeof value === "string" ? value : value == null ? "" : String(value);
}

export function LargeGlassInput(props: InputProps & { commitMode?: CommitMode }) {
  const { className, classNames, size, commitMode = "immediate", value, onValueChange, onBlur, onKeyDown, ...rest } = props;
  const [draft, setDraft] = useState(() => resolveDraftValue(value));

  useEffect(() => {
    if (commitMode === "blur") setDraft(resolveDraftValue(value));
  }, [commitMode, value]);

  const committedValue = commitMode === "blur" ? draft : resolveDraftValue(value);
  return (
    <Input
      {...rest}
      size={size ?? "sm"}
      className={clsx(largeGlassFieldRootClass, className)}
      value={committedValue}
      onValueChange={(next) => {
        if (commitMode === "blur") {
          setDraft(next);
          return;
        }
        onValueChange?.(next);
      }}
      onBlur={(event) => {
        if (commitMode === "blur") onValueChange?.(draft);
        onBlur?.(event);
      }}
      onKeyDown={(event) => {
        if (commitMode === "blur" && event.key === "Enter") onValueChange?.(draft);
        onKeyDown?.(event);
      }}
      classNames={{
        label: "text-black/50 dark:text-white/90",
        inputWrapper:
          "shadow-xl bg-default-100/70 dark:bg-default/60 backdrop-blur-xl backdrop-saturate-200 hover:bg-default-0/70 dark:hover:bg-default/70 group-data-[focus=true]:bg-default-100/50 dark:group-data-[focus=true]:bg-default/60 border border-transparent !cursor-text",
        innerWrapper: "bg-transparent",
        input: "bg-transparent text-black/90 dark:text-white/90 placeholder:text-default-700/50 dark:placeholder:text-white/60",
        ...classNames,
      }}
    />
  );
}

export function LargeGlassTextarea(props: TextAreaProps & { commitMode?: CommitMode }) {
  const { className, classNames, size, commitMode = "immediate", value, onValueChange, onBlur, onKeyDown, ...rest } = props;
  const [draft, setDraft] = useState(() => resolveDraftValue(value));

  useEffect(() => {
    if (commitMode === "blur") setDraft(resolveDraftValue(value));
  }, [commitMode, value]);

  const committedValue = commitMode === "blur" ? draft : resolveDraftValue(value);
  return (
    <Textarea
      {...rest}
      size={size ?? "sm"}
      className={clsx(largeGlassFieldRootClass, className)}
      value={committedValue}
      onValueChange={(next) => {
        if (commitMode === "blur") {
          setDraft(next);
          return;
        }
        onValueChange?.(next);
      }}
      onBlur={(event) => {
        if (commitMode === "blur") onValueChange?.(draft);
        onBlur?.(event);
      }}
      onKeyDown={(event) => {
        if (commitMode === "blur" && event.key === "Enter" && !event.shiftKey) onValueChange?.(draft);
        onKeyDown?.(event);
      }}
      classNames={{
        label: "text-black/50 dark:text-white/90",
        inputWrapper:
          "shadow-xl bg-default-100/70 dark:bg-default/60 backdrop-blur-xl backdrop-saturate-200 hover:bg-default-0/70 dark:hover:bg-default/70 group-data-[focus=true]:bg-default-100/50 dark:group-data-[focus=true]:bg-default/60 border border-transparent !cursor-text",
        input: "bg-transparent text-black/90 dark:text-white/90 placeholder:text-default-700/50 dark:placeholder:text-white/60",
        ...classNames,
      }}
    />
  );
}
