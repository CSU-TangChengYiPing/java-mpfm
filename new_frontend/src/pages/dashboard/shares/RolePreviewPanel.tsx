import { Button } from "@heroui/button";
import { Select, SelectItem } from "@heroui/select";
import { type SortDescriptor } from "@heroui/table";
import { useTranslation } from "react-i18next";
import FileTable from "../../../components/file_manage/file_table";
import type { FileInfo } from "../../../controllers/file_manager";
import { pickSingleSelectKey } from "./selectKey";

/** 角色预览面板：以只读方式展示目标角色在当前路径下的可见结果。 */
export default function RolePreviewPanel(props: {
  selectedMountID: string;
  previewRole: string;
  presetRoleList: string[];
  loading: boolean;
  previewFiles: FileInfo[];
  previewSortDescriptor: SortDescriptor;
  currentIsRoot?: boolean;
  onPreviewRoleChange: (v: string) => void;
  onRefreshPreview: () => void;
  onPreviewSortChange: (v: SortDescriptor) => void;
}) {
  const p = props;
  const { t } = useTranslation();
  return (
    <>
      <div className="rounded-sm border border-white/40 bg-white/60 px-4 py-3 backdrop-blur-sm dark:border-white/10 dark:bg-black/40 flex items-end gap-3">
        <Select label={t("shares.previewRoleLabel")} selectedKeys={[p.previewRole]} onSelectionChange={(keys) => p.onPreviewRoleChange(pickSingleSelectKey(keys as "all" | Set<string | number>))}>
          {p.presetRoleList.map((r) => <SelectItem key={r}>{r}</SelectItem>)}
        </Select>
        <Button color="primary" onPress={p.onRefreshPreview} isDisabled={!p.selectedMountID}>{t("shares.refreshPreviewButton")}</Button>
      </div>
      <FileTable
        files={p.previewFiles}
        currentPath={"."}
        loading={p.loading}
        sortDescriptor={p.previewSortDescriptor}
        onSortChange={p.onPreviewSortChange}
        selectedFiles={new Set()}
        onSelectionChange={() => {}}
        selectionMode={false}
        onDirectoryClick={() => {}}
        onEdit={() => {}}
        onPreview={() => {}}
        onRenameRequest={() => {}}
        onMoveRequest={() => {}}
        onCopyPath={() => {}}
        onDelete={() => {}}
        onDownload={() => {}}
        canOperatePath={() => false}
        currentIsRoot={p.currentIsRoot}
        resolveDownloadUrl={() => null}
        showPermissionColumn
      />
    </>
  );
}
