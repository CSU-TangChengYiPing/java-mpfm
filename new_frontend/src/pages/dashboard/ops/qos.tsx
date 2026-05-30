import { Button } from "@heroui/button";
import { Input } from "@heroui/input";
import { TableCell, TableColumn, TableRow } from "@heroui/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import toast from "react-hot-toast";
import { FiCheckSquare, FiEdit2, FiPlus, FiSearch, FiTrash2, FiXSquare } from "react-icons/fi";
import FormModal from "../../../components/common/FormModal";
import { LargeGlassInput } from "../../../components/common/LargeGlassField";
import PaginatedTableShell from "../../../components/common/PaginatedTableShell";
import RootOnlyNoticeCard from "../../../components/common/RootOnlyNoticeCard";
import ShadowTooltip from "../../../components/common/ShadowTooltip";
import UsersController, { type QosPolicyInfo } from "../../../controllers/users";
import { useAuth } from "../../../hooks/useAuth";

type QosRow = QosPolicyInfo & { key: string };

/** QoS 策略中心（极简版）：完全复用通用表格壳与表单弹窗组件。 */
export default function QosPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [loading, setLoading] = useState(false);
  const [policies, setPolicies] = useState<QosPolicyInfo[]>([]);
  const [search, setSearch] = useState("");
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editing, setEditing] = useState<QosPolicyInfo | null>(null);
  const [name, setName] = useState("");
  const [uploadMbps, setUploadMbps] = useState("8");
  const [downloadMbps, setDownloadMbps] = useState("8");

  const load = useCallback(async () => {
    if (!user?.is_root) return;
    setLoading(true);
    try {
      const list = await UsersController.listQosPolicies();
      setPolicies(list);
      setSelectedKeys(new Set());
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("users.loadFailed"));
    } finally {
      setLoading(false);
    }
  }, [t, user?.is_root]);

  useEffect(() => {
    void load();
  }, [load]);

  const rows = useMemo<QosRow[]>(() => {
    const key = search.trim().toLowerCase();
    const filtered = key
      ? policies.filter((it) => it.id.toLowerCase().includes(key) || it.name.toLowerCase().includes(key))
      : policies;
    return filtered.map((it) => ({ ...it, key: it.id }));
  }, [policies, search]);

  function openCreate() {
    setEditing(null);
    setName("");
    setUploadMbps("8");
    setDownloadMbps("8");
    setIsModalOpen(true);
  }

  function openEdit(policy: QosPolicyInfo) {
    setEditing(policy);
    setName(policy.name);
    setUploadMbps(String(Math.round(policy.maxUploadBps / 1024 / 1024)));
    setDownloadMbps(String(Math.round(policy.maxDownloadBps / 1024 / 1024)));
    setIsModalOpen(true);
  }

  function validateForm() {
    const up = Number(uploadMbps);
    const down = Number(downloadMbps);
    if (!name.trim()) return t("users.qos.validation.nameRequired");
    if (!Number.isFinite(up) || up <= 0 || up > 2048) return t("users.qos.validation.uploadRange");
    if (!Number.isFinite(down) || down <= 0 || down > 2048) return t("users.qos.validation.downloadRange");
    return "";
  }

  async function savePolicy() {
    const msg = validateForm();
    if (msg) {
      toast.error(msg);
      return;
    }
    const payload = {
      name: name.trim(),
      maxUploadBps: Math.round(Number(uploadMbps) * 1024 * 1024),
      maxDownloadBps: Math.round(Number(downloadMbps) * 1024 * 1024),
    };
    try {
      if (editing) {
        await UsersController.upsertQosPolicy(editing.id, payload);
      } else {
        await UsersController.createQosPolicy(payload);
      }
      toast.success(t("common.saved"));
      setIsModalOpen(false);
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("common.saveFailed"));
    }
  }

  async function removeOne(policyId: string) {
    try {
      await UsersController.deleteQosPolicy(policyId);
      toast.success(t("common.deleteSuccess"));
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("common.deleteFailed"));
    }
  }

  async function removeBatch() {
    if (!selectionMode) {
      toast.error(t("users.qos.validation.selectModeRequired"));
      return;
    }
    if (selectedKeys.size === 0) {
      toast.error(t("users.qos.validation.selectFirst"));
      return;
    }
    try {
      await UsersController.batchDeleteQosPolicy(Array.from(selectedKeys));
      toast.success(t("common.deleteSuccess"));
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("common.deleteFailed"));
    }
  }

  if (!user?.is_root) return <RootOnlyNoticeCard message={t("auth.rootOnlyUsers")} />;

  return (
    <div className="h-full w-full overflow-y-auto p-2 md:p-4">
      <div className="flex min-h-full flex-col gap-4">
        <div className="flex flex-wrap items-center gap-2 rounded-sm border border-white/40 bg-white/60 px-4 py-2 shadow-sm backdrop-blur-sm dark:border-white/10 dark:bg-black/40">
          <ShadowTooltip content={selectionMode ? t("common.cancel") : t("fileManager.selectionMode")}>
            <Button
              aria-label={selectionMode ? t("common.cancel") : t("fileManager.selectionMode")}
              isIconOnly
              radius="sm"
              size="sm"
              color={selectionMode ? "warning" : "default"}
              variant="flat"
              onPress={() => {
                if (selectionMode) {
                  setSelectionMode(false);
                  setSelectedKeys(new Set());
                } else {
                  setSelectionMode(true);
                }
              }}
            >
              {selectionMode ? <FiXSquare /> : <FiCheckSquare />}
            </Button>
          </ShadowTooltip>
          <ShadowTooltip content={t("common.delete")}>
            <Button
              aria-label={t("common.delete")}
              isIconOnly
              radius="sm"
              size="sm"
              color="danger"
              variant="flat"
              isDisabled={!selectionMode}
              onPress={() => void removeBatch()}
            >
              <FiTrash2 />
            </Button>
          </ShadowTooltip>
          <ShadowTooltip content={t("common.create")}>
            <Button aria-label={t("common.create")} isIconOnly radius="sm" size="sm" color="primary" variant="flat" onPress={openCreate}>
              <FiPlus />
            </Button>
          </ShadowTooltip>
          <Input
            size="sm"
            variant="bordered"
            value={search}
            onValueChange={setSearch}
            placeholder={t("common.search")}
            startContent={<FiSearch />}
            className="ml-auto w-64"
          />
        </div>

        <PaginatedTableShell
          ariaLabel="qos"
          rows={rows}
          emptyContent={t("common.empty")}
          loading={loading}
          totalLabel={(total) => t("fileManager.totalLabel", { count: total })}
          enablePageSizeInput
          defaultPageSize={10}
          pageSizeLabel={t("fileManager.pageSizeLabel")}
          selectionMode={selectionMode ? "multiple" : "none"}
          selectedKeys={selectionMode ? selectedKeys : new Set<string>()}
          onSelectionChange={(keys) => {
            if (!selectionMode) return;
            setSelectedKeys(keys);
          }}
          header={
            <>
              <TableColumn>ID</TableColumn>
              <TableColumn>{t("users.qos.name")}</TableColumn>
              <TableColumn>{t("users.qos.maxUploadMbps")}</TableColumn>
              <TableColumn>{t("users.qos.maxDownloadMbps")}</TableColumn>
              <TableColumn>{t("common.actions")}</TableColumn>
            </>
          }
          renderRow={(item) => (
            <TableRow key={item.key}>
              <TableCell>{item.id}</TableCell>
              <TableCell>{item.name}</TableCell>
              <TableCell>{Math.round(item.maxUploadBps / 1024 / 1024)}</TableCell>
              <TableCell>{Math.round(item.maxDownloadBps / 1024 / 1024)}</TableCell>
              <TableCell>
                <div className="flex items-center gap-1">
                  <Button isIconOnly radius="sm" size="sm" variant="flat" color="primary" onPress={() => openEdit(item)}>
                    <FiEdit2 />
                  </Button>
                  <Button isIconOnly radius="sm" size="sm" variant="flat" color="danger" onPress={() => void removeOne(item.id)}>
                    <FiTrash2 />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          )}
        />

        <FormModal
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
          title={editing ? t("common.modify") : t("common.create")}
          onSubmit={() => void savePolicy()}
          submitText={t("common.save")}
          cancelText={t("common.cancel")}
        >
          <LargeGlassInput label={t("users.qos.name")} size="sm" value={name} onValueChange={setName} commitMode="blur" />
          <LargeGlassInput label={t("users.qos.maxUploadMbps")} size="sm" value={uploadMbps} onValueChange={setUploadMbps} commitMode="blur" />
          <LargeGlassInput label={t("users.qos.maxDownloadMbps")} size="sm" value={downloadMbps} onValueChange={setDownloadMbps} commitMode="blur" />
        </FormModal>
      </div>
    </div>
  );
}
