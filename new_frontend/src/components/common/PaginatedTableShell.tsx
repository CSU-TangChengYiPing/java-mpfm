import { LargeGlassInput } from "./LargeGlassField";
import { Pagination } from "@heroui/pagination";
import { Spinner } from "@heroui/spinner";
import {
  Table,
  TableBody,
  type TableBodyProps,
  TableHeader,
  type SortDescriptor,
} from "@heroui/table";
import { useMemo, useState, type ReactNode } from "react";
import type { ReactElement } from "react";
import clsx from "clsx";
import i18n from "../../i18n";
import {
  sharedGlassTableClassNames,
  sharedTableBottomBarClassName,
} from "./tableShell";

type SelectionLike = Set<string> | "all";

export function resolveTotalLabel(
  totalLabel: ReactNode | ((total: number) => ReactNode) | undefined,
  total: number
): ReactNode {
  if (typeof totalLabel === "function") return totalLabel(total);
  return totalLabel ?? `${total}`;
}

type PaginatedTableShellProps<T extends { key: string }> = {
  ariaLabel: string;
  rows: T[];
  header: ReactElement | ReactElement[];
  renderRow: (row: T) => ReactElement;
  emptyContent: TableBodyProps<T>["emptyContent"];
  loading?: boolean;
  loadingContent?: ReactNode;
  totalLabel?: ReactNode | ((total: number) => ReactNode);
  defaultPageSize?: number;
  enablePageSizeInput?: boolean;
  pageSizeLabel?: ReactNode;
  pageSizePlaceholder?: string;
  sortDescriptor?: SortDescriptor;
  onSortChange?: (descriptor: SortDescriptor) => void;
  selectionMode?: "none" | "multiple";
  selectedKeys?: SelectionLike;
  onSelectionChange?: (keys: Set<string>) => void;
  wrapperClassName?: string;
  controlledPage?: number;
  controlledPages?: number;
  onPageChange?: (page: number) => void;
  disableInternalSlice?: boolean;
  onPageSizeChange?: (pageSize: number) => void;
};

/** 通用分页表格壳：统一分页、排序、选择与底部统计交互，减少页面重复实现。 */
export default function PaginatedTableShell<T extends { key: string }>({
  ariaLabel,
  rows,
  header,
  renderRow,
  emptyContent,
  loading = false,
  loadingContent,
  totalLabel,
  defaultPageSize = 12,
  enablePageSizeInput = false,
  pageSizeLabel,
  pageSizePlaceholder = "20/all",
  sortDescriptor,
  onSortChange,
  selectionMode = "none",
  selectedKeys,
  onSelectionChange,
  wrapperClassName,
  controlledPage,
  controlledPages,
  onPageChange,
  disableInternalSlice = false,
  onPageSizeChange,
}: PaginatedTableShellProps<T>) {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState<number>(defaultPageSize);
  const [pageSizeInput, setPageSizeInput] = useState<string>(String(defaultPageSize));

  const effectivePageSize = pageSize === -1 ? Math.max(rows.length, 1) : pageSize;
  const computedPages = Math.max(1, Math.ceil(rows.length / effectivePageSize));
  const pages = controlledPages ?? computedPages;
  const basePage = controlledPage ?? page;
  const safePage = Math.min(Math.max(1, basePage), pages);
  const displayRows = useMemo(
    () => (disableInternalSlice ? rows : rows.slice((safePage - 1) * effectivePageSize, safePage * effectivePageSize)),
    [rows, safePage, effectivePageSize, disableInternalSlice]
  );

  const applyPageSize = () => {
    const raw = pageSizeInput.trim().toLowerCase();
    if (raw === "all" || raw === i18n.t("common.all").toLowerCase()) {
      setPageSize(-1);
      setPage(1);
      if (onPageSizeChange) onPageSizeChange(-1);
      return;
    }
    const n = Number(raw);
    if (Number.isFinite(n) && n > 0) {
      const nextSize = Math.floor(n);
      setPageSize(nextSize);
      setPage(1);
      if (onPageSizeChange) onPageSizeChange(nextSize);
    } else {
      setPageSizeInput(pageSize === -1 ? "all" : String(pageSize));
    }
  };

  const rightSlot = enablePageSizeInput ? (
    <div className="flex min-w-0 items-center justify-end gap-2 text-xs text-default-500">
      <span>{pageSizeLabel}</span>
      <LargeGlassInput
        size="sm"
        className="w-20 mpfm-compact-glass-field"
        value={pageSizeInput}
        onValueChange={setPageSizeInput}
        onBlur={applyPageSize}
        onKeyDown={(e) => {
          if (e.key === "Enter") applyPageSize();
        }}
        placeholder={pageSizePlaceholder}
        classNames={{
          inputWrapper: "h-7 min-h-7 border border-default-200 bg-white/70 dark:bg-black/35",
          input: "text-xs",
        }}
      />
      <span>{resolveTotalLabel(totalLabel, rows.length)}</span>
    </div>
  ) : (
    <div className="text-right text-xs text-default-500">
      {resolveTotalLabel(totalLabel, rows.length)}
    </div>
  );

  return (
    <Table
      aria-label={ariaLabel}
      isHeaderSticky
      classNames={{ ...sharedGlassTableClassNames, wrapper: clsx(sharedGlassTableClassNames.wrapper, wrapperClassName) }}
      topContentPlacement="outside"
      bottomContentPlacement="outside"
      topContent={<div className="h-1" />}
      sortDescriptor={sortDescriptor}
      onSortChange={onSortChange}
      selectionMode={selectionMode}
      selectedKeys={selectedKeys === "all" ? "all" : selectedKeys}
      onSelectionChange={(keys) => {
        if (!onSelectionChange) return;
        if (keys === "all") {
          onSelectionChange(new Set(displayRows.map((row) => row.key)));
          return;
        }
        onSelectionChange(new Set(Array.from(keys).map((v) => String(v))));
      }}
      bottomContent={
        <div className={sharedTableBottomBarClassName}>
          <div />
          <Pagination
            isCompact
            showControls
            showShadow
            color="primary"
            page={safePage}
            total={pages}
            onChange={(nextPage) => {
              if (onPageChange) onPageChange(nextPage);
              if (controlledPage === undefined) setPage(nextPage);
            }}
            classNames={{ cursor: "bg-primary shadow-lg" }}
          />
          {rightSlot}
        </div>
      }
    >
      <TableHeader>{header as ReactElement[]}</TableHeader>
      <TableBody
        isLoading={loading}
        loadingContent={loadingContent ?? <Spinner />}
        emptyContent={emptyContent}
      >
        {displayRows.map((row) => renderRow(row))}
      </TableBody>
    </Table>
  );
}

