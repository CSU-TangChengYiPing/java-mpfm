import { Button } from "@heroui/button";
import { Checkbox } from "@heroui/checkbox";
import { Chip } from "@heroui/chip";
import { Input } from "@heroui/input";
import { Progress } from "@heroui/progress";
import { Select, SelectItem } from "@heroui/select";
import { TableCell, TableColumn, TableRow } from "@heroui/table";
import clsx from "clsx";
import { useEffect, useMemo, useRef, useState } from "react";
import toast from "react-hot-toast";
import { useTranslation } from "react-i18next";
import { FiCheckSquare, FiPause, FiPlay, FiRefreshCw, FiRotateCw, FiTrash2, FiX } from "react-icons/fi";
import PaginatedTableShell from "../../../components/common/PaginatedTableShell";
import ShadowTooltip from "../../../components/common/ShadowTooltip";
import key from "../../../const/key";
import FileManager, { type APIError, type TaskInfo } from "../../../controllers/file_manager";

function normalizeStatus(status?: string): string {
  return (status || "").toUpperCase();
}

function isActiveTask(status?: string): boolean {
  const normalized = normalizeStatus(status);
  return normalized === "RUNNING" || normalized === "PENDING" || normalized === "PAUSING" || normalized === "RESUMING" || normalized === "CANCELING";
}

function formatRate(bps?: number): string {
  const value = Math.max(0, Number(bps ?? 0));
  if (value >= 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB/s`;
  if (value >= 1024) return `${(value / 1024).toFixed(1)} KB/s`;
  return `${Math.round(value)} B/s`;
}

function formatMb(bytes?: number): string {
  const value = Math.max(0, Number(bytes ?? 0));
  if (value >= 1024 * 1024 * 1024) return `${(value / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  if (value >= 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`;
  if (value >= 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${Math.round(value)} B`;
}

type TaskRow = TaskInfo & { key: string };

function isDownloadTask(task: TaskInfo): boolean {
  const action = `${task.action || ""} ${task.actionLabel || ""}`.toLowerCase();
  return action.includes("download") || task.taskId.startsWith("download-");
}

function renderRangeProgress(task: TaskInfo) {
  const states = Array.isArray(task.chunkStates) && task.chunkStates.length > 0 ? task.chunkStates : [];
  const percent = Math.max(0, Math.min(100, Math.round(task.progress || 0)));
  const loaded = Math.max(0, Number(task.transferredBytes ?? 0));
  const total = Math.max(0, Number(task.totalBytes ?? 0));
  const progressLabel = `${formatMb(loaded)} / ${formatMb(total)}`;
  const rangeMarks = states.length > 0 ? states : [];
  return (
    <div className="flex items-center gap-2 min-w-[220px]">
      <div className="relative flex-1 min-w-0 h-6 flex items-center">
        <div className="absolute -top-3 left-0 pointer-events-none">
          <span className="inline-flex rounded px-1.5 py-0.5 text-[10px] leading-none text-default-700 bg-default-100/90 dark:bg-default-200/20 dark:text-default-300 shadow-sm">
            {progressLabel}
          </span>
        </div>
        <div className="relative w-full">
          <Progress
            aria-label="download-progress"
            size="sm"
            radius="sm"
            value={percent}
            className="max-w-full"
          />
          {rangeMarks.length > 0 && (
            <div className="pointer-events-none absolute inset-0 flex opacity-35 rounded-[inherit] overflow-hidden">
              {rangeMarks.map((state, index) => (
                <span
                  key={`${task.taskId}-${index}`}
                  className={clsx("h-full", state === "done" ? "bg-primary-300" : "bg-default-300")}
                  style={{ width: `${100 / rangeMarks.length}%` }}
                />
              ))}
            </div>
          )}
        </div>
      </div>
      <span className="text-xs text-default-500 w-10 text-right">{percent}%</span>
    </div>
  );
}

/**
 * 任务中心页面：SSE 实时更新优先，轮询仅作为兜底，并按运行/空闲/隐藏三档动态调整请求频率。
 * 任务操作后优先做行级懒更新，再由实时流与轮询收敛到服务端最终状态。
 */
export default function TasksPage() {
  const { t } = useTranslation();
  const [tasks, setTasks] = useState<TaskInfo[]>([]);
  const tasksRef = useRef<TaskInfo[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [selectionMode, setSelectionMode] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");

  const sortedTasks = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    const filteredByStatus = statusFilter === "all"
      ? tasks
      : tasks.filter((item) => normalizeStatus(item.status) === statusFilter);
    const filtered = q
      ? filteredByStatus.filter((item) => `${item.targetName || ""} ${item.target || ""}`.toLowerCase().includes(q))
      : filteredByStatus;
    return [...filtered].sort((a, b) => Date.parse(b.updatedAt || "") - Date.parse(a.updatedAt || ""));
  }, [tasks, keyword, statusFilter]);
  const tableRows = useMemo<TaskRow[]>(
    () => sortedTasks.filter(isDownloadTask).map((task) => ({ ...task, key: task.taskId })),
    [sortedTasks]
  );

  useEffect(() => {
    tasksRef.current = tasks;
  }, [tasks]);

  useEffect(() => {
    const unsubscribe = FileManager.subscribeDownloadCenterTasks((localTasks) => {
      setTasks(localTasks);
    });
    return () => {
      unsubscribe();
    };
  }, []);

  const handleCancel = async (taskId: string) => {
    try {
      if (taskId.startsWith("download-local-")) {
        const current = tasksRef.current.find((item) => item.taskId === taskId);
        if (normalizeStatus(current?.status) === "PAUSED") {
          await FileManager.resumeDownloadByTaskId(taskId);
          toast.success(t("tasks.resumeDone"));
          setTasks((prev) => prev.map((task) => (task.taskId === taskId ? { ...task, status: "RUNNING", updatedAt: new Date().toISOString() } : task)));
          return;
        }
        const ok = FileManager.pauseDownloadByTaskId(taskId);
        if (!ok) throw new Error("task not running");
        setTasks((prev) => prev.map((task) => (task.taskId === taskId ? { ...task, status: "PAUSED", updatedAt: new Date().toISOString() } : task)));
        return;
      }
      await FileManager.cancelTask(taskId);
      toast.success(t("tasks.cancelDone"));
      setTasks((prev) => prev.map((task) => (task.taskId === taskId ? { ...task, status: "CANCELING", updatedAt: new Date().toISOString() } : task)));
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      toast.error(t("tasks.pauseFailed", { message }));
    }
  };

  const handleDelete = async (taskId: string) => {
    try {
      await FileManager.deleteTask(taskId);
      toast.success(t("tasks.deleteDone"));
      setTasks((prev) => prev.filter((task) => task.taskId !== taskId));
    } catch (error) {
      const apiError = error as APIError;
      const message = apiError?.message || (error instanceof Error ? error.message : String(error));
      toast.error(t("tasks.deleteFailed", { message }));
    }
  };

  const handleRetry = async (taskId: string) => {
    try {
      if (taskId.startsWith("download-local-")) {
        await FileManager.resumeDownloadByTaskId(taskId);
        toast.success(t("tasks.resumeDone"));
        setTasks((prev) => prev.map((task) => (task.taskId === taskId ? { ...task, status: "RUNNING", updatedAt: new Date().toISOString() } : task)));
        return;
      }
      toast.error(t("tasks.retryUnsupported"));
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      toast.error(t("tasks.pauseFailed", { message }));
    }
  };

  const handleDeleteSelected = async () => {
    const ids = Array.from(selected);
    for (const id of ids) {
      await handleDelete(id);
    }
    setSelected(new Set());
  };

  const allSelected = tableRows.length > 0 && selected.size === tableRows.length;
  const partiallySelected = selected.size > 0 && selected.size < tableRows.length;
  const toggleSelectAll = (next: boolean) => {
    if (!next) {
      setSelected(new Set());
      return;
    }
    setSelected(new Set(tableRows.map((row) => row.taskId)));
  };

  const hasBackground = !!(localStorage.getItem(key.backgroundImage) ?? "");

  const highlight = (text: string) => {
    const q = keyword.trim();
    if (!q) return text;
    const lower = text.toLowerCase();
    const lowerQ = q.toLowerCase();
    const idx = lower.indexOf(lowerQ);
    if (idx < 0) return text;
    return (
      <>
        {text.slice(0, idx)}
        <span className="bg-warning-200/60 rounded px-0.5">{text.slice(idx, idx + q.length)}</span>
        {text.slice(idx + q.length)}
      </>
    );
  };

  return (
    <div className="h-full w-full p-2 md:p-4" data-testid="tasks-page">
      <div
        className={clsx(
          "mb-4 flex items-center gap-2 sticky top-14 z-10 backdrop-blur-sm shadow-sm py-2 px-4 rounded-sm transition-colors",
          hasBackground ? "bg-white/20 dark:bg-black/10 border border-white/40 dark:border-white/10" : "bg-white/60 dark:bg-black/40 border border-white/40 dark:border-white/10"
        )}
      >
        <Button
          aria-label={t("common.refresh")}
          radius="sm"
          color="primary"
          size="sm"
          isIconOnly
          variant="flat"
          onPress={() => setTasks(FileManager.listDownloadCenterTasks())}
          className="text-lg min-w-8"
        >
          <FiRefreshCw />
        </Button>
        <ShadowTooltip content={selectionMode ? t("shares.exitMultiSelect") : t("shares.enterMultiSelect")}>
          <Button
            aria-label={selectionMode ? t("shares.exitMultiSelect") : t("shares.enterMultiSelect")}
            radius="sm"
            color="primary"
            size="sm"
            isIconOnly
            variant={selectionMode ? "solid" : "flat"}
            onPress={() => {
              setSelectionMode((prev) => {
                const next = !prev;
                if (!next) setSelected(new Set());
                return next;
              });
            }}
            className="text-lg min-w-8"
          >
            {selectionMode ? <FiX /> : <FiCheckSquare />}
          </Button>
        </ShadowTooltip>
        <Input
          size="sm"
          className="max-w-[260px]"
          placeholder={t("tasks.searchPlaceholder")}
          value={keyword}
          onValueChange={setKeyword}
        />
        <Select
          size="sm"
          selectedKeys={new Set([statusFilter])}
          onSelectionChange={(keys) => {
            const first = Array.from(keys)[0];
            setStatusFilter(typeof first === "string" ? first : "all");
          }}
          className="max-w-[180px]"
          aria-label={t("tasks.filter")}
        >
          <SelectItem key="all">{t("tasks.filterAll")}</SelectItem>
          <SelectItem key="RUNNING">{t("tasks.status.running")}</SelectItem>
          <SelectItem key="PAUSED">{t("tasks.status.paused")}</SelectItem>
          <SelectItem key="SUCCESS">{t("tasks.status.success")}</SelectItem>
          <SelectItem key="FAILED">{t("tasks.status.failed")}</SelectItem>
        </Select>
        {selected.size > 0 && (
          <Button
            radius="sm"
            color="danger"
            size="sm"
            variant="flat"
            className="text-sm px-2 min-w-fit"
            startContent={<FiTrash2 className="text-lg" />}
            onPress={() => void handleDeleteSelected()}
            isDisabled={!selectionMode}
          >
            ({selected.size})
          </Button>
        )}
        <span className="ml-auto text-xs text-default-500">
          {t("fileManager.totalLabel", { count: tableRows.length })}
        </span>
      </div>
      <PaginatedTableShell
        ariaLabel={t("menu.Download Center")}
        rows={tableRows}
        emptyContent={t("common.empty")}
        totalLabel={(total) => t("fileManager.totalLabel", { count: total })}
        defaultPageSize={12}
        enablePageSizeInput
        pageSizeLabel={t("fileManager.pageSizeLabel")}
        pageSizePlaceholder="20/all"
        wrapperClassName="min-h-[420px]"
        header={(
          <>
            <TableColumn key="pick" className="w-[48px]">
              {selectionMode ? <Checkbox isSelected={allSelected} isIndeterminate={partiallySelected} onValueChange={toggleSelectAll} /> : ""}
            </TableColumn>
            <TableColumn key="fileName" className="w-[420px]">{t("tasks.colFileName")}</TableColumn>
            <TableColumn key="status" className="w-[120px]">{t("tasks.colStatus")}</TableColumn>
            <TableColumn key="speed" className="w-[120px]">{t("tasks.metricSpeed")}</TableColumn>
            <TableColumn key="progress" className="w-[260px]">{t("tasks.colProgressBar")}</TableColumn>
            <TableColumn key="actions" className="w-[220px]">{t("common.actions")}</TableColumn>
          </>
        )}
        renderRow={(task) => {
          const status = normalizeStatus(task.status);
          const statusColor = status === "SUCCESS" ? "success" : status === "FAILED" ? "danger" : status === "CANCELED" ? "warning" : "primary";
          return (
            <TableRow key={task.key}>
              <TableCell>
                {selectionMode ? (
                  <Checkbox
                    isSelected={selected.has(task.taskId)}
                    onValueChange={(checked) => {
                      setSelected((prev) => {
                        const next = new Set(prev);
                        if (checked) next.add(task.taskId);
                        else next.delete(task.taskId);
                        return next;
                      });
                    }}
                  />
                ) : null}
              </TableCell>
              <TableCell>
                <div className="min-w-0">
                  <div className="truncate max-w-[380px]">{highlight(task.targetName || task.target || task.taskId)}</div>
                  <div className="truncate max-w-[380px] text-xs text-default-400">{highlight(task.target || "-")}</div>
                </div>
              </TableCell>
              <TableCell><Chip size="sm" color={statusColor}>{task.status || "-"}</Chip></TableCell>
              <TableCell>{formatRate(task.speedBytesPerSec)}</TableCell>
              <TableCell>{renderRangeProgress(task)}</TableCell>
              <TableCell>
                <div className="flex items-center gap-2">
                  <ShadowTooltip content={task.taskId.startsWith("download-local-") && normalizeStatus(task.status) === "PAUSED" ? t("tasks.resume") : t("common.pause")}>
                    <Button
                      aria-label={task.taskId.startsWith("download-local-") && normalizeStatus(task.status) === "PAUSED" ? t("tasks.resume") : t("common.pause")}
                      size="sm"
                      variant="flat"
                      isIconOnly
                      className="text-lg min-w-8"
                      isDisabled={!isActiveTask(task.status) && normalizeStatus(task.status) !== "PAUSED"}
                      onPress={() => void handleCancel(task.taskId)}
                    >
                      {task.taskId.startsWith("download-local-") && normalizeStatus(task.status) === "PAUSED" ? <FiPlay /> : <FiPause />}
                    </Button>
                  </ShadowTooltip>
                  <ShadowTooltip content={t("tasks.retry")}>
                    <Button
                      aria-label={t("tasks.retry")}
                      size="sm"
                      variant="flat"
                      color="primary"
                      isIconOnly
                      className="text-lg min-w-8"
                      isDisabled={normalizeStatus(task.status) !== "FAILED"}
                      onPress={() => void handleRetry(task.taskId)}
                    >
                      <FiRotateCw />
                    </Button>
                  </ShadowTooltip>
                  <ShadowTooltip content={t("common.delete")}>
                    <Button
                      aria-label={t("common.delete")}
                      size="sm"
                      color="danger"
                      variant="flat"
                      isIconOnly
                      className="text-lg min-w-8"
                      onPress={() => void handleDelete(task.taskId)}
                    >
                      <FiTrash2 />
                    </Button>
                  </ShadowTooltip>
                </div>
              </TableCell>
            </TableRow>
          );
        }}
      />
    </div>
  );
}
