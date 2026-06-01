import { Button } from "@heroui/button";
import { Select, SelectItem } from "@heroui/select";
import { TableCell, TableColumn, TableRow } from "@heroui/table";
import { FiBookOpen, FiEdit2, FiEye, FiPlus, FiTrash2 } from "react-icons/fi";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import FormModal from "../../../components/common/FormModal";
import PaginatedTableShell from "../../../components/common/PaginatedTableShell";
import { LargeGlassInput } from "../../../components/common/LargeGlassField";
import PermissionLevelSelector from "../../../components/common/PermissionLevelSelector";
import ShadowTooltip from "../../../components/common/ShadowTooltip";
import type { MountInfo, ShareRoleTemplate } from "../../../controllers/mounts";
import { pickSingleSelectKey } from "./selectKey";

type Row = ShareRoleTemplate & { key: string };
type PermState = {
  visible: boolean;
  read: boolean;
  write: boolean;
};

function normalizePermName(raw: string): string {
  const key = (raw || "").trim().toLowerCase();
  if (key === "public" || key === "publoc") return "visible";
  return key;
}

function toPerms(state: PermState): string[] {
  const out: string[] = [];
  if (state.visible) out.push("visible");
  if (state.read) out.push("read");
  if (state.write) out.push("write");
  return out;
}

function toPermLevel(state: PermState): number {
  if (state.write) return 3;
  if (state.read) return 2;
  if (state.visible) return 1;
  return 0;
}

function fromPermLevel(level: number): PermState {
  const cursor = Math.max(0, Math.min(3, Math.floor(level)));
  return {
    visible: cursor >= 1,
    read: cursor >= 2,
    write: cursor >= 3,
  };
}

/** 角色模板面板：管理共享角色模板的新增、编辑与删除。 */
export default function RoleTemplatesPanel({
  selectedMountID,
  manageableMounts,
  loading,
  rows,
  onMountChange,
  onCreateRoleTemplate,
  onUpdateRoleTemplate,
  onDeleteRoleTemplate,
}: {
  selectedMountID: string;
  manageableMounts: MountInfo[];
  loading: boolean;
  rows: Row[];
  onMountChange: (v: string) => void;
  onCreateRoleTemplate: (name: string, permissions: string[]) => void;
  onUpdateRoleTemplate: (id: string, name: string, permissions: string[]) => void;
  onDeleteRoleTemplate: (id: string) => void;
}) {
  const { t } = useTranslation();
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [newRoleName, setNewRoleName] = useState("");
  const [editTemplateID, setEditTemplateID] = useState("");
  const [editRoleName, setEditRoleName] = useState("");
  const [createPerm, setCreatePerm] = useState<PermState>({ visible: true, read: true, write: false });
  const [editPerm, setEditPerm] = useState<PermState>({ visible: true, read: true, write: false });

  function openEdit(row: Row) {
    const set = new Set((row.permissions ?? []).map((p) => normalizePermName(p)));
    const visible = row.defaultVisible ?? set.has("visible");
    const read = row.defaultRead ?? set.has("read");
    const write = row.defaultWrite ?? set.has("write");
    setEditTemplateID(row.templateId || row.id);
    setEditRoleName(row.name || row.id);
    setEditPerm({
      visible,
      read,
      write,
    });
    setEditOpen(true);
  }

  return (
    <>
      <div className="rounded-sm border border-white/40 bg-white/60 px-4 py-3 backdrop-blur-sm dark:border-white/10 dark:bg-black/40">
        <div className="flex flex-col items-stretch gap-3 md:flex-row md:items-center md:justify-between">
        <div className="flex w-full items-center gap-2 md:max-w-md">
          <span className="w-16 shrink-0 text-xs text-default-600">{t("shares.mountLabel")}</span>
          <Select size="sm" className="min-w-0 flex-1" classNames={{ trigger: "h-8 min-h-8", value: "text-xs" }} aria-label={t("shares.mountLabel")} selectedKeys={selectedMountID ? [selectedMountID] : []} onSelectionChange={(keys) => onMountChange(pickSingleSelectKey(keys as "all" | Set<string | number>))}>
            {manageableMounts.map((m) => <SelectItem key={m.id}>{`${m.name} (${m.id})`}</SelectItem>)}
          </Select>
        </div>
        <Button className="w-full md:w-auto" color="secondary" startContent={<FiPlus />} onPress={() => setCreateOpen(true)} isDisabled={!selectedMountID}>{t("shares.addRoleButton")}</Button>
        </div>
      </div>
      <PaginatedTableShell
        ariaLabel="role-list"
        wrapperClassName="min-h-[400px]"
        rows={rows}
        loading={loading}
        totalLabel={(n) => t("shares.totalRoles", { count: n })}
        emptyContent={t("shares.emptyRoles")}
        header={<><TableColumn key="name">{t("shares.nameColumn")}</TableColumn><TableColumn key="permissions">{t("shares.permissionColumn")}</TableColumn><TableColumn key="builtin">{t("shares.builtinColumn")}</TableColumn><TableColumn key="actions">{t("common.actions")}</TableColumn></>}
        renderRow={(it) => {
          const permSet = new Set((it.permissions ?? []).map((p) => normalizePermName(p)));
          return (
            <TableRow key={it.key}>
              <TableCell>{it.name}</TableCell>
              <TableCell>
                <div className="flex items-center gap-2">
                  {permSet.has("visible") && (
                  <ShadowTooltip content="visible">
                    <span className="inline-flex h-6 w-6 items-center justify-center rounded-full border border-default-300 text-default-500"><FiEye /></span>
                  </ShadowTooltip>
                )}
                  {permSet.has("read") && (
                  <ShadowTooltip content="read">
                    <span className="inline-flex h-6 w-6 items-center justify-center rounded-full border border-default-300 text-default-500"><FiBookOpen /></span>
                  </ShadowTooltip>
                )}
                  {permSet.has("write") && (
                  <ShadowTooltip content="write">
                    <span className="inline-flex h-6 w-6 items-center justify-center rounded-full border border-default-300 text-default-500"><FiEdit2 /></span>
                  </ShadowTooltip>
                )}
                </div>
              </TableCell>
              <TableCell>{it.builtin ? "yes" : "no"}</TableCell>
              <TableCell>
                <div className="flex items-center gap-1">
                  <ShadowTooltip content={t("shares.editRoleTooltip")}><Button aria-label={t("shares.editRoleTooltip")} isIconOnly size="sm" variant="flat" onPress={() => openEdit(it)}><FiEdit2 /></Button></ShadowTooltip>
                  <ShadowTooltip content={t("shares.deleteRoleTooltip")}><Button aria-label={t("shares.deleteRoleTooltip")} isIconOnly size="sm" variant="flat" color="danger" isDisabled={it.builtin} onPress={() => onDeleteRoleTemplate(it.templateId || it.id)}><FiTrash2 /></Button></ShadowTooltip>
                </div>
              </TableCell>
            </TableRow>
          );
        }}
      />

      <FormModal
        isOpen={createOpen}
        onClose={() => setCreateOpen(false)}
        title={t("shares.addRoleButton")}
        onSubmit={() => {
          onCreateRoleTemplate(newRoleName, toPerms(createPerm));
          setCreateOpen(false);
          setNewRoleName("");
        }}
        submitText={t("common.create")}
      >
        <LargeGlassInput label={t("shares.roleNameLabel")} value={newRoleName} onValueChange={setNewRoleName} commitMode="blur" />
        <PermissionLevelSelector level={toPermLevel(createPerm) as 0 | 1 | 2 | 3} onChange={(level) => setCreatePerm(fromPermLevel(level))} />
      </FormModal>

      <FormModal
        isOpen={editOpen}
        onClose={() => setEditOpen(false)}
        title={t("shares.editRoleTitle", { roleName: editRoleName })}
        onSubmit={() => {
          onUpdateRoleTemplate(editTemplateID, editRoleName, toPerms(editPerm));
          setEditOpen(false);
        }}
        submitText={t("common.save")}
      >
        <LargeGlassInput label={t("shares.roleNameLabel")} value={editRoleName} onValueChange={setEditRoleName} commitMode="blur" />
        <PermissionLevelSelector level={toPermLevel(editPerm) as 0 | 1 | 2 | 3} onChange={(level) => setEditPerm(fromPermLevel(level))} />
      </FormModal>
    </>
  );
}
