import { Button } from "@heroui/button";
import { Checkbox } from "@heroui/checkbox";
import { Select, SelectItem } from "@heroui/select";
import { TableCell, TableColumn, TableRow } from "@heroui/table";
import { FiBookOpen, FiEdit2, FiEye, FiPlus, FiTrash2 } from "react-icons/fi";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import FormModal from "../../../components/common/FormModal";
import PaginatedTableShell from "../../../components/common/PaginatedTableShell";
import { LargeGlassInput } from "../../../components/common/LargeGlassField";
import ShadowTooltip from "../../../components/common/ShadowTooltip";
import type { MountInfo, ShareRoleTemplate } from "../../../controllers/mounts";

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
  onCreateRoleTemplate: (id: string, name: string, permissions: string[]) => void;
  onUpdateRoleTemplate: (id: string, name: string, permissions: string[]) => void;
  onDeleteRoleTemplate: (id: string) => void;
}) {
  const { t } = useTranslation();
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [newRoleID, setNewRoleID] = useState("");
  const [newRoleName, setNewRoleName] = useState("");
  const [editRoleID, setEditRoleID] = useState("");
  const [editRoleName, setEditRoleName] = useState("");
  const [createPerm, setCreatePerm] = useState<PermState>({ visible: true, read: true, write: false });
  const [editPerm, setEditPerm] = useState<PermState>({ visible: true, read: true, write: false });

  function openEdit(row: Row) {
    const set = new Set((row.permissions ?? []).map((p) => normalizePermName(p)));
    const visible = row.defaultVisible ?? set.has("visible");
    const read = row.defaultRead ?? set.has("read");
    const write = row.defaultWrite ?? set.has("write");
    setEditRoleID(row.id);
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
      <div className="rounded-sm border border-white/40 bg-white/60 px-4 py-3 backdrop-blur-sm dark:border-white/10 dark:bg-black/40 flex items-center justify-between">
        <Select className="max-w-md" label={t("shares.mountLabel")} selectedKeys={selectedMountID ? [selectedMountID] : []} onSelectionChange={(keys) => onMountChange(String(Array.from(keys)[0] ?? ""))}>
          {manageableMounts.map((m) => <SelectItem key={m.id}>{`${m.name} (${m.id})`}</SelectItem>)}
        </Select>
        <Button color="secondary" startContent={<FiPlus />} onPress={() => setCreateOpen(true)} isDisabled={!selectedMountID}>{t("shares.addRoleButton")}</Button>
      </div>
      <PaginatedTableShell
        ariaLabel="role-list"
        wrapperClassName="h-[calc(100vh-360px)]"
        rows={rows}
        loading={loading}
        totalLabel={(n) => t("shares.totalRoles", { count: n })}
        emptyContent={t("shares.emptyRoles")}
        header={<><TableColumn key="id">ID</TableColumn><TableColumn key="name">{t("shares.nameColumn")}</TableColumn><TableColumn key="permissions">{t("shares.permissionColumn")}</TableColumn><TableColumn key="builtin">{t("shares.builtinColumn")}</TableColumn><TableColumn key="actions">{t("common.actions")}</TableColumn></>}
        renderRow={(it) => {
          const permSet = new Set((it.permissions ?? []).map((p) => normalizePermName(p)));
          return (
            <TableRow key={it.key}>
              <TableCell>{it.id}</TableCell>
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
          onCreateRoleTemplate(newRoleID, newRoleName || newRoleID, toPerms(createPerm));
          setCreateOpen(false);
          setNewRoleID("");
          setNewRoleName("");
        }}
        submitText={t("common.create")}
      >
        <LargeGlassInput label={t("shares.roleIdLabel")} value={newRoleID} onValueChange={setNewRoleID} commitMode="blur" />
        <LargeGlassInput label={t("shares.roleNameLabel")} value={newRoleName} onValueChange={setNewRoleName} commitMode="blur" />
        <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
          <Checkbox isSelected={createPerm.visible} onValueChange={(v) => setCreatePerm((s) => ({ ...s, visible: v }))}>visible</Checkbox>
          <Checkbox isSelected={createPerm.read} onValueChange={(v) => setCreatePerm((s) => ({ ...s, read: v }))}>read</Checkbox>
          <Checkbox isSelected={createPerm.write} onValueChange={(v) => setCreatePerm((s) => ({ ...s, write: v }))}>write</Checkbox>
        </div>
      </FormModal>

      <FormModal
        isOpen={editOpen}
        onClose={() => setEditOpen(false)}
        title={t("shares.editRoleTitle", { roleId: editRoleID })}
        onSubmit={() => {
          onUpdateRoleTemplate(editRoleID, editRoleName || editRoleID, toPerms(editPerm));
          setEditOpen(false);
        }}
        submitText={t("common.save")}
      >
        <LargeGlassInput label={t("shares.roleNameLabel")} value={editRoleName} onValueChange={setEditRoleName} commitMode="blur" />
        <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
          <Checkbox isSelected={editPerm.visible} onValueChange={(v) => setEditPerm((s) => ({ ...s, visible: v }))}>visible</Checkbox>
          <Checkbox isSelected={editPerm.read} onValueChange={(v) => setEditPerm((s) => ({ ...s, read: v }))}>read</Checkbox>
          <Checkbox isSelected={editPerm.write} onValueChange={(v) => setEditPerm((s) => ({ ...s, write: v }))}>write</Checkbox>
        </div>
      </FormModal>
    </>
  );
}
