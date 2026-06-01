import { Button } from "@heroui/button";
import { Card, CardBody } from "@heroui/card";
import { Chip } from "@heroui/chip";
import { Switch } from "@heroui/switch";
import { Tab, Tabs } from "@heroui/tabs";
import { Tooltip } from "@heroui/tooltip";
import { useLocalStorage } from "@uidotdev/usehooks";
import clsx from "clsx";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FiCheckCircle, FiDownload, FiHelpCircle, FiXCircle } from "react-icons/fi";
import toast from "react-hot-toast";
import { useNavigate, useSearchParams } from "react-router-dom";
import { LargeGlassInput } from "../../../components/common/LargeGlassField";
import key from "../../../const/key";
import AvatarUploader from "../../../components/profile/AvatarUploader";
import ProfileController from "../../../controllers/profile";
import SystemController from "../../../controllers/system";
import { useAuth } from "../../../hooks/useAuth";
import { toggleTheme } from "../../../utils/theme";

type HealthState = "idle" | "loading" | "ok" | "error";
type TabKey = "profile" | "appearance" | "security" | "system";

function ConfigPageItem({ children, size = "md" }: { children: React.ReactNode; size?: "sm" | "md" | "lg" }) {
  const [backgroundImage] = useLocalStorage<string>(key.backgroundImage, "");
  const hasBackground = !!backgroundImage;
  return (
    <Card className={clsx("w-full mx-auto backdrop-blur-sm border border-white/40 dark:border-white/10 shadow-sm rounded-2xl transition-all", hasBackground ? "bg-white/20 dark:bg-black/10" : "bg-white/60 dark:bg-black/40", { "max-w-xl": size === "sm", "max-w-3xl": size === "md", "max-w-6xl": size === "lg" })}>
      <CardBody className="py-5 px-3 md:py-8 md:px-12">
        <div className="w-full flex flex-col gap-5">{children}</div>
      </CardBody>
    </Card>
  );
}

/** 设置页：承载资料偏好、安全操作与系统健康检查。 */
export default function SettingsPage() {
  const { user, refreshUser, updateLocalUser, logout } = useAuth();
  const { i18n, t } = useTranslation();
  const navigate = useNavigate();
  const search = useSearchParams({ tab: "profile" })[0];
  const tab = (search.get("tab") || "profile") as TabKey;

  const [status, setStatus] = useState<HealthState>("idle");
  const [message, setMessage] = useState("");
  const [compactMode, setCompactMode] = useLocalStorage<boolean>(key.compactMode, false);
  const [nicknameDraft, setNicknameDraft] = useState<string | null>(null);
  const [emailDraft, setEmailDraft] = useState<string | null>(null);
  const [phoneDraft, setPhoneDraft] = useState<string | null>(null);
  const [oldCredential, setOldCredential] = useState("");
  const [newCredential, setNewCredential] = useState("");
  const [confirmCredential, setConfirmCredential] = useState("");
  const [securityFieldErrors, setSecurityFieldErrors] = useState<{
    oldCredential?: string;
    newCredential?: string;
    confirmCredential?: string;
  }>({});
  const [securityMessage, setSecurityMessage] = useState("");
  const [fileViewMode, setFileViewMode] = useState<"list" | "grid">("list");
  const [sessions, setSessions] = useState<Array<{ sessionId: string; status: string; expiresAt: string; clientIp?: string; userAgent?: string; deviceLabel?: string }>>([]);
  const currentLocale = (i18n.resolvedLanguage || i18n.language).toLowerCase().startsWith("zh") ? "zh" : "en";

  const checkHealth = useCallback(async () => {
    setStatus("loading");
    setMessage(t("settings.checking"));
    try {
      const body = await SystemController.health();
      setStatus(body.status === "ok" ? "ok" : "error");
      setMessage(`${body.service} @ ${body.timestamp}`);
    } catch (err) {
      setStatus("error");
      setMessage(err instanceof Error ? err.message : t("settings.unknownError"));
    }
  }, [t]);

  const loadSessions = useCallback(async () => {
    try {
      const raw = await ProfileController.sessions();
      const deduped = Array.from(new Map(raw.map((item) => [item.sessionId, item])).values())
        .filter((item) => (item.status || "").toLowerCase() === "active");
      setSessions(deduped);
    } catch {
      setSessions([]);
    }
  }, []);

  useEffect(() => {
    const id = window.setTimeout(() => { void checkHealth(); }, 0);
    return () => window.clearTimeout(id);
  }, [checkHealth]);

  useEffect(() => {
    if (tab === "security") {
      void loadSessions();
    }
  }, [loadSessions, tab]);

  const isDark = useMemo(() => document.documentElement.classList.contains("dark"), []);

  useEffect(() => {
    const mode = (user?.fileViewMode || "list").toLowerCase() === "grid" ? "grid" : "list";
    setFileViewMode(mode as "list" | "grid");
  }, [user?.fileViewMode]);
  const nicknameInput = nicknameDraft ?? user?.nickname ?? "";
  const emailInput = emailDraft ?? user?.email ?? "";
  const phoneInput = phoneDraft ?? user?.phone ?? "";

  const saveProfile = useCallback(async () => {
    const nextNickname = nicknameInput.trim();
    if (!nextNickname) return;
    try {
      await ProfileController.updateMe({
        nickname: nextNickname,
        email: emailInput.trim() || undefined,
        phone: phoneInput.trim() || undefined,
      });
      updateLocalUser({
        nickname: nextNickname,
        displayName: nextNickname,
        email: emailInput.trim() || undefined,
        phone: phoneInput.trim() || undefined,
      });
      await refreshUser();
      setMessage(t("settings.profileSaved"));
    } catch (err) {
      setMessage(err instanceof Error ? err.message : t("common.saveFailed"));
    }
    setNicknameDraft(null);
    setEmailDraft(null);
    setPhoneDraft(null);
  }, [emailInput, nicknameInput, phoneInput, refreshUser, t, updateLocalUser]);

  const savePreferences = useCallback(async () => {
    try {
      await ProfileController.updatePreferences({ language: currentLocale, fileViewMode });
      await refreshUser();
      setMessage(t("settings.preferenceSaved"));
    } catch (err) {
      setMessage(err instanceof Error ? err.message : t("common.saveFailed"));
    }
  }, [currentLocale, fileViewMode, refreshUser, t]);

  async function submitCredentialChange() {
    setSecurityMessage("");
    const nextErrors: { oldCredential?: string; newCredential?: string; confirmCredential?: string } = {};
    if (!oldCredential.trim()) nextErrors.oldCredential = t("settings.security.errors.oldRequired");
    if (!newCredential.trim()) nextErrors.newCredential = t("settings.security.errors.newRequired");
    if (!confirmCredential.trim()) nextErrors.confirmCredential = t("settings.security.errors.confirmRequired");
    if (newCredential && confirmCredential && newCredential !== confirmCredential) {
      nextErrors.confirmCredential = t("settings.security.errors.confirmMismatch");
    }
    if (nextErrors.oldCredential || nextErrors.newCredential || nextErrors.confirmCredential) {
      setSecurityFieldErrors(nextErrors);
      return;
    }
    setSecurityFieldErrors({});
    try {
      await ProfileController.changeCredential({ oldCredential, newCredential });
      setSecurityMessage(t("settings.security.messages.changeSuccess"));
      await logout();
    } catch (err) {
      const msg = err instanceof Error ? err.message : t("settings.security.messages.changeFailed");
      if (msg.includes("old credential invalid") || msg.includes("AUTH_INVALID")) {
        setSecurityFieldErrors({ oldCredential: t("settings.security.errors.oldInvalid") });
      } else if (msg.includes("password strength invalid") || msg.includes("VALIDATION_ERROR")) {
        setSecurityFieldErrors({ newCredential: t("settings.security.errors.newWeak") });
      } else {
        setSecurityMessage(msg);
      }
    }
  }

  async function revokeSession(sessionId: string) {
    if (sessionId === user?.sessionId) {
      setSecurityMessage(t("settings.security.messages.currentSessionRevokeDenied"));
      return;
    }
    try {
      await ProfileController.revokeSession(sessionId);
      setSecurityMessage(t("settings.security.messages.sessionRevoked"));
      await loadSessions();
    } catch (err) {
      setSecurityMessage(err instanceof Error ? err.message : t("settings.security.messages.revokeFailed"));
    }
  }

  async function downloadDevCert() {
    try {
      const blob = await SystemController.downloadDevCert();
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = "mpfm-local.cer";
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.URL.revokeObjectURL(url);
      toast.success(t("settings.system.devCertDownloadSuccess"));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t("settings.system.devCertDownloadFailed"));
    }
  }

  return (
    <section className="w-full max-w-[1200px] mx-auto py-2 md:py-4 px-1 md:px-3 relative overflow-x-hidden">
      <div className="w-full flex flex-col items-center">
        <div className="w-full overflow-x-auto">
        <Tabs aria-label={t("settings.tabsAriaLabel")} fullWidth={false} disableAnimation={false} className="w-full min-w-max" selectedKey={tab} onSelectionChange={(k) => navigate(`/app/settings?tab=${String(k)}`)}>
          <Tab title={t("settings.tabs.profile")} key="profile">
            <ConfigPageItem>
              {user?.user_id ? <AvatarUploader userID={user.user_id} avatarURL={user.avatarUrl} onUploaded={() => { void refreshUser(); setMessage(t("settings.avatarUpdated")); }} /> : null}
              <LargeGlassInput label={t("settings.profileFields.userId")} value={user?.user_id ?? "-"} isDisabled />
              <LargeGlassInput label={t("settings.profileFields.nickname")} value={nicknameInput} onValueChange={setNicknameDraft} commitMode="blur" />
              <LargeGlassInput label={t("settings.profileFields.email")} value={emailInput} onValueChange={setEmailDraft} commitMode="blur" />
              <LargeGlassInput label={t("settings.profileFields.phone")} value={phoneInput} onValueChange={setPhoneDraft} commitMode="blur" />
              <LargeGlassInput label={t("settings.profileFields.role")} value={user?.role || "-"} isDisabled />
              <div className="flex flex-wrap items-center gap-2">
                <Button size="sm" color="primary" onPress={() => void saveProfile()}>{t("common.save")}</Button>
                <Button size="sm" variant="flat" onPress={() => { setNicknameDraft(null); setEmailDraft(null); setPhoneDraft(null); }}>{t("common.clear")}</Button>
              </div>
              {message && <p className="text-xs text-default-500">{message}</p>}
            </ConfigPageItem>
          </Tab>

          <Tab title={t("settings.tabs.appearance")} key="appearance">
            <ConfigPageItem>
              <div className="flex gap-2 flex-wrap">
                <Button variant="flat" onPress={() => { const next = currentLocale === "zh" ? "en" : "zh"; void i18n.changeLanguage(next); window.localStorage.setItem(key.locale, next); }}>
                  {t("settings.language")}: {currentLocale === "zh" ? t("language.zh") : t("language.en")}
                </Button>
                <Button variant="flat" onPress={toggleTheme}>{t("settings.theme")}: {isDark ? t("settings.dark") : t("settings.light")}</Button>
              </div>
              <Switch isSelected={!!compactMode} onValueChange={setCompactMode}>{t("settings.compactMode")}</Switch>
              <div className="flex items-center gap-2">
                <Button size="sm" variant={fileViewMode === "list" ? "solid" : "flat"} onPress={() => setFileViewMode("list")}>{t("settings.fileViewModes.list")}</Button>
                <Button size="sm" variant={fileViewMode === "grid" ? "solid" : "flat"} onPress={() => setFileViewMode("grid")}>{t("settings.fileViewModes.grid")}</Button>
                <Button size="sm" color="primary" onPress={() => void savePreferences()}>{t("settings.savePreference")}</Button>
              </div>
            </ConfigPageItem>
          </Tab>

          <Tab title={t("settings.tabs.security")} key="security">
            <ConfigPageItem>
              <div className="rounded-xl border border-default-200 p-4">
                <div className="mb-3 text-sm font-semibold">{t("settings.security.sections.changePassword")}</div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <div>
                    <LargeGlassInput
                      label={t("settings.security.fields.oldPassword")}
                      type="password"
                      value={oldCredential}
                      onValueChange={setOldCredential}
                      commitMode="blur"
                    />
                    {securityFieldErrors.oldCredential ? <p className="mt-1 text-xs text-danger">{securityFieldErrors.oldCredential}</p> : null}
                  </div>
                  <div>
                    <LargeGlassInput
                      label={t("settings.security.fields.newPassword")}
                      type="password"
                      value={newCredential}
                      onValueChange={setNewCredential}
                      commitMode="blur"
                    />
                    {securityFieldErrors.newCredential ? <p className="mt-1 text-xs text-danger">{securityFieldErrors.newCredential}</p> : null}
                  </div>
                  <div className="md:col-span-2">
                    <LargeGlassInput
                      label={t("settings.security.fields.confirmPassword")}
                      type="password"
                      value={confirmCredential}
                      onValueChange={setConfirmCredential}
                      commitMode="blur"
                      endContent={
                        confirmCredential
                          ? (newCredential === confirmCredential
                            ? <FiCheckCircle className="text-success" aria-label={t("settings.security.match.ok")} />
                            : <FiXCircle className="text-danger" aria-label={t("settings.security.match.bad")} />)
                          : null
                      }
                    />
                    {securityFieldErrors.confirmCredential ? <p className="mt-1 text-xs text-danger">{securityFieldErrors.confirmCredential}</p> : null}
                  </div>
                </div>
                <div className="mt-3"><Button color="primary" onPress={() => void submitCredentialChange()}>{t("settings.security.actions.submitChange")}</Button></div>
              </div>
              <div className="rounded-xl border border-default-200 p-4">
                <div className="mb-3 text-sm font-semibold">{t("settings.security.sections.revokeSession")}</div>
                <div className="space-y-2">
                  {sessions.map((s) => (
                    <div key={s.sessionId} className="flex flex-col items-start gap-2 rounded-lg border border-default-200 p-2 sm:flex-row sm:items-center">
                      <div className="min-w-0 flex-1 text-xs">
                        <div className="truncate">
                          {s.sessionId}
                          {s.sessionId === user?.sessionId ? `（${t("settings.security.currentSession")}）` : ""}
                        </div>
                        <div className="text-default-500">{s.status} · {s.expiresAt}</div>
                        <div className="text-default-500">
                          {s.deviceLabel || "Unknown"} · {s.clientIp || "unknown ip"}
                        </div>
                      </div>
                      <Button
                        size="sm"
                        variant="flat"
                        color="danger"
                        className="self-end sm:self-auto"
                        isDisabled={s.sessionId === user?.sessionId}
                        onPress={() => void revokeSession(s.sessionId)}
                      >
                        {t("settings.security.actions.revoke")}
                      </Button>
                    </div>
                  ))}
                </div>
              </div>
              {securityMessage && <p className="text-xs text-default-500">{securityMessage}</p>}
            </ConfigPageItem>
          </Tab>

          <Tab title={t("settings.tabs.system")} key="system">
            <ConfigPageItem size="sm">
              <div className="flex flex-col items-start gap-2 rounded-xl bg-white/30 dark:bg-black/25 backdrop-blur-md border border-white/30 dark:border-white/10 px-3 py-2 sm:flex-row sm:items-center">
                <Chip color={status === "ok" ? "success" : status === "error" ? "danger" : "warning"}>{status.toUpperCase()}</Chip>
                <p className="min-w-0 break-all text-xs text-default-500">{message}</p>
                <Button className="sm:ml-auto" size="sm" variant="flat" onPress={() => void checkHealth()} isLoading={status === "loading"}>{t("common.refresh")}</Button>
              </div>
              <div className="flex flex-wrap items-center gap-2 rounded-xl bg-white/30 dark:bg-black/25 backdrop-blur-md border border-white/30 dark:border-white/10 px-3 py-3">
                <Button size="sm" color="primary" startContent={<FiDownload />} onPress={() => void downloadDevCert()}>
                  {t("settings.system.devCertDownload")}
                </Button>
                <Tooltip content={<div className="max-w-[300px] whitespace-pre-wrap text-xs">{t("settings.system.devCertHelp")}</div>} showArrow>
                  <span className="inline-flex h-5 w-5 cursor-help items-center justify-center rounded-full border border-default-300 bg-default-100 text-default-700">
                    <FiHelpCircle className="text-[12px]" />
                  </span>
                </Tooltip>
              </div>
            </ConfigPageItem>
          </Tab>
        </Tabs>
        </div>
      </div>
    </section>
  );
}

