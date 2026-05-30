import { Button } from "@heroui/button";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { LargeGlassInput } from "../../../components/common/LargeGlassField";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import AvatarUploader from "../../../components/profile/AvatarUploader";
import ProfileController from "../../../controllers/profile";

type ProfileData = {
  user_id: string;
  nickname: string;
  email?: string;
  phone?: string;

  is_root: boolean;
  bio: string;
  avatar_url: string;
  activities: Array<{ id: string; type: string; title: string; created_at: string }>;
};

/** 个人主页：展示并维护用户资料、头像与最近动态入口。 */
export default function ProfilePage() {
  const { t } = useTranslation();
  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [nicknameInput, setNicknameInput] = useState("");
  const [emailInput, setEmailInput] = useState("");
  const [phoneInput, setPhoneInput] = useState("");
  const navigate = useNavigate();

  const loadProfile = useCallback(async () => {
    setLoading(true);
    setMessage("");
    try {
      const data = await ProfileController.me();
      setProfile(data);
      setNicknameInput(data.nickname || "");
      setEmailInput(data.email || "");
      setPhoneInput(data.phone || "");
    } catch (err) {
      setMessage(err instanceof Error ? err.message : t("profile.loadFailed"));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    const id = window.setTimeout(() => {
      void loadProfile();
    }, 0);
    return () => window.clearTimeout(id);
  }, [loadProfile]);

  async function saveProfile() {
    setMessage("");
    try {
      await ProfileController.updateMe({ nickname: nicknameInput.trim(), email: emailInput.trim() || undefined, phone: phoneInput.trim() || undefined });
      setMessage(t("profile.saveSuccess"));
      await loadProfile();
    } catch (err) {
      setMessage(err instanceof Error ? err.message : t("common.saveFailed"));
    }
  }

  return (
    <section className="mx-auto flex w-full max-w-[1200px] flex-col gap-4 p-2 md:p-4">
      <Card className="border border-white/30 bg-white/65 dark:bg-black/30">
        <CardHeader className="pb-2 text-base font-semibold">{t("profile.title")}</CardHeader>
        <CardBody className="gap-3">
          {profile?.user_id ? <AvatarUploader userID={profile.user_id} onUploaded={() => void loadProfile()} /> : null}
          <LargeGlassInput label={t("profile.userIdLabel")} value={profile?.user_id ?? "-"} isReadOnly />
          <LargeGlassInput label={t("profile.nicknameLabel")} value={nicknameInput} onValueChange={setNicknameInput} commitMode="blur" />
          <LargeGlassInput label={t("profile.emailLabel")} value={emailInput} onValueChange={setEmailInput} commitMode="blur" />
          <LargeGlassInput label={t("profile.phoneLabel")} value={phoneInput} onValueChange={setPhoneInput} commitMode="blur" />
          <div className="flex items-center gap-2">
            <Button color="primary" onPress={() => void saveProfile()} isLoading={loading}>
              {t("profile.saveButton")}
            </Button>
            <Button variant="flat" onPress={() => void loadProfile()} isLoading={loading}>
              {t("common.refresh")}
            </Button>
          </div>
          {message && <p className="text-sm text-default-500">{message}</p>}
        </CardBody>
      </Card>

      <Card className="border border-white/30 bg-white/65 dark:bg-black/30">
        <CardHeader className="pb-2 text-base font-semibold">{t("profile.activitiesTitle")}</CardHeader>
        <CardBody className="gap-2">
          {(profile?.activities ?? []).map((item) => (
            <div key={item.id} className="rounded-xl border border-white/30 bg-white/40 px-3 py-2 dark:bg-white/5">
              <div className="text-sm text-default-700 dark:text-default-100">{item.title}</div>
              <div className="text-xs text-default-400">{new Date(item.created_at).toLocaleString()}</div>
            </div>
          ))}
          {!profile?.activities?.length && <p className="text-sm text-default-400">{t("profile.activitiesEmpty")}</p>}
        </CardBody>
      </Card>
      <div className="flex justify-end">
        <Button variant="flat" color="primary" onPress={() => navigate("/app/profile/search")}>
          {t("profile.goSearch")}
        </Button>
      </div>
    </section>
  );
}



