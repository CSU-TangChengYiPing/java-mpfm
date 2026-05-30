import { Card, CardBody } from "@heroui/card";
import { motion } from "motion/react";
import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router-dom";
import { LargeGlassInput } from "../../components/common/LargeGlassField";
import { issueCaptcha } from "../../controllers/auth";
import { useAuth } from "../../hooks/useAuth";

/** 注册页流程：处理字段校验、验证码、人机错误码映射与注册提交。 */
export default function RegisterPage() {
  const { register } = useAuth();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [captchaId, setCaptchaId] = useState("");
  const [captchaAnswer, setCaptchaAnswer] = useState("");
  const [captchaImage, setCaptchaImage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  function formatAuthError(message: string): string {
    const codeMatch = message.match(/\[([A-Z_]+)\]/);
    if (codeMatch) {
      const code = codeMatch[1];
      const mapped = t(`authErrors.codes.${code}`, { defaultValue: "" });
      if (mapped) return mapped;
      return t("authErrors.unknownCode", { code });
    }
    return message;
  }

  async function loadCaptcha() {
    const resp = await issueCaptcha("register");
    setCaptchaId(resp.captchaId);
    setCaptchaImage(resp.imageDataUrl);
  }

  useEffect(() => {
    void loadCaptcha();
  }, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await register(username, nickname, password, confirmPassword, captchaId || undefined, captchaAnswer || undefined);
      navigate("/app/files", { replace: true });
    } catch (err) {
      const message = err instanceof Error ? err.message : "register failed";
      setError(formatAuthError(message));
      if (message.includes("CAPTCHA_REQUIRED") || message.includes("CAPTCHA_INVALID")) {
        await loadCaptcha();
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page-bg flex min-h-screen items-center justify-center p-6">
      <motion.form onSubmit={(e) => { void submit(e); }} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
        <Card className="w-[420px] border border-white/40 dark:border-white/10 bg-white/75 dark:bg-black/45 backdrop-blur-2xl shadow-xl">
          <CardBody className="gap-3 p-6">
            <h1 className="text-2xl font-semibold text-black/90 dark:text-white/90">{t("authPages.register.title")}</h1>
            <p className="text-sm text-default-500">{t("authPages.register.subtitle")}</p>
            <LargeGlassInput label={t("authPages.register.username")} value={username} onValueChange={setUsername} autoComplete="username" commitMode="blur" />
            <LargeGlassInput label={t("authPages.register.nickname")} value={nickname} onValueChange={setNickname} autoComplete="nickname" commitMode="blur" />
            <LargeGlassInput label={t("authPages.register.password")} type="password" value={password} onValueChange={setPassword} autoComplete="new-password" commitMode="blur" />
            <LargeGlassInput label={t("authPages.register.confirmPassword")} type="password" value={confirmPassword} onValueChange={setConfirmPassword} autoComplete="new-password" commitMode="blur" />
            {captchaImage && (
              <div className="flex items-end gap-2">
                <div className="flex-1">
                  <LargeGlassInput label={t("authPages.captcha")} value={captchaAnswer} onValueChange={setCaptchaAnswer} commitMode="blur" />
                </div>
                <img
                  src={captchaImage}
                  alt="captcha"
                  className="h-[42px] w-[148px] shrink-0 cursor-pointer rounded-lg border border-default-200 object-cover bg-white"
                  title={t("authPages.refreshCaptcha")}
                  onClick={() => {
                    void loadCaptcha();
                  }}
                />
              </div>
            )}
            <p className="text-xs text-default-500">{t("authPages.register.usernameRule")}</p>
            {error && <p className="text-sm text-danger">{error}</p>}
            <button
              type="submit"
              disabled={loading}
              className="h-11 w-full rounded-xl bg-slate-900 text-white shadow-md transition-opacity hover:opacity-95 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {t("authPages.register.register")}
            </button>
            <p className="text-sm text-default-500">{t("authPages.register.hasAccount")} <Link className="text-primary" to="/login" state={{ prefillUsername: username.trim() }}>{t("authPages.register.loginNow")}</Link></p>
          </CardBody>
        </Card>
      </motion.form>
    </div>
  );
}
