import { Card, CardBody } from "@heroui/card";
import { motion } from "motion/react";
import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { LargeGlassInput } from "../../components/common/LargeGlassField";
import { issueCaptcha } from "../../controllers/auth";
import { useAuth } from "../../hooks/useAuth";

const LOGIN_CAPTCHA_UNTIL_KEY = "mpfm:loginCaptchaUntil";
const LOGIN_CAPTCHA_WINDOW_SECONDS = 15 * 60;

/** 登录页状态机：处理凭据提交、验证码触发与账号锁定倒计时反馈。 */
export default function LoginPage() {
  const { login } = useAuth();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [captchaId, setCaptchaId] = useState("");
  const [captchaAnswer, setCaptchaAnswer] = useState("");
  const [captchaImage, setCaptchaImage] = useState("");
  const [showCaptcha, setShowCaptcha] = useState(false);
  const [error, setError] = useState("");
  const [lockedUntilEpoch, setLockedUntilEpoch] = useState<number | null>(null);
  const [lockRemainSec, setLockRemainSec] = useState(0);
  const [loading, setLoading] = useState(false);

  async function loadCaptcha() {
    const resp = await issueCaptcha("login");
    setCaptchaId(resp.captchaId);
    setCaptchaImage(resp.imageDataUrl);
  }

  useEffect(() => {
    const now = Math.floor(Date.now() / 1000);
    const raw = window.localStorage.getItem(LOGIN_CAPTCHA_UNTIL_KEY);
    const until = raw ? Number(raw) : 0;
    if (Number.isFinite(until) && until > now) {
      setShowCaptcha(true);
      void loadCaptcha();
    } else {
      window.localStorage.removeItem(LOGIN_CAPTCHA_UNTIL_KEY);
    }
  }, []);

  useEffect(() => {
    const prefillUsername = (location.state as { prefillUsername?: string } | null)?.prefillUsername;
    if (typeof prefillUsername === "string" && prefillUsername.trim()) {
      setUsername(prefillUsername.trim());
    }
  }, [location.state]);

  useEffect(() => {
    if (!lockedUntilEpoch) return;
    const timer = window.setInterval(() => {
      const remain = Math.max(0, lockedUntilEpoch - Math.floor(Date.now() / 1000));
      setLockRemainSec(remain);
      if (remain <= 0) {
        setLockedUntilEpoch(null);
        setError("");
      }
    }, 1000);
    return () => window.clearInterval(timer);
  }, [lockedUntilEpoch]);

  useEffect(() => {
    if (!showCaptcha) return;
    const timer = window.setInterval(() => {
      const raw = window.localStorage.getItem(LOGIN_CAPTCHA_UNTIL_KEY);
      const until = raw ? Number(raw) : 0;
      const now = Math.floor(Date.now() / 1000);
      if (!Number.isFinite(until) || until <= now) {
        window.localStorage.removeItem(LOGIN_CAPTCHA_UNTIL_KEY);
        setShowCaptcha(false);
        setCaptchaAnswer("");
        setCaptchaImage("");
      }
    }, 1000);
    return () => window.clearInterval(timer);
  }, [showCaptcha]);

  function formatAuthError(message: string): string {
    const lockMatch = message.match(/\[AUTH_INVALID\]\s*account temporarily locked:(\d+)/i);
    if (lockMatch) {
      const lockEpoch = Number(lockMatch[1]);
      if (!Number.isNaN(lockEpoch)) {
        setLockedUntilEpoch(lockEpoch);
        const remain = Math.max(0, lockEpoch - Math.floor(Date.now() / 1000));
        setLockRemainSec(remain);
        return t("authErrors.accountLocked", { seconds: remain });
      }
    }
    const codeMatch = message.match(/\[([A-Z_]+)\]/);
    if (codeMatch) {
      const code = codeMatch[1];
      const mapped = t(`authErrors.codes.${code}`, { defaultValue: "" });
      if (mapped) return mapped;
      return t("authErrors.unknownCode", { code });
    }
    return message;
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await login(
        username,
        password,
        showCaptcha ? (captchaId || undefined) : undefined,
        showCaptcha ? (captchaAnswer || undefined) : undefined
      );
      const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? "/app/files";
      navigate(from, { replace: true });
    } catch (err) {
      const message = err instanceof Error ? err.message : "login failed";
      setError(formatAuthError(message));
      if (message.includes("CAPTCHA_REQUIRED") || message.includes("CAPTCHA_INVALID")) {
        setShowCaptcha(true);
        const until = Math.floor(Date.now() / 1000) + LOGIN_CAPTCHA_WINDOW_SECONDS;
        window.localStorage.setItem(LOGIN_CAPTCHA_UNTIL_KEY, String(until));
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
            <h1 className="text-2xl font-semibold text-black/90 dark:text-white/90">{t("authPages.login.title")}</h1>
            <p className="text-sm text-default-500">{t("authPages.login.subtitle")}</p>
            <LargeGlassInput label={t("authPages.login.username")} value={username} onValueChange={setUsername} autoComplete="username" commitMode="blur" />
            <LargeGlassInput label={t("authPages.login.password")} type="password" value={password} onValueChange={setPassword} autoComplete="current-password" commitMode="blur" />
            {showCaptcha && captchaImage && (
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
            {error && <p className="text-sm text-danger">{error}</p>}
            {!!lockedUntilEpoch && lockRemainSec > 0 && (
              <p className="text-xs text-warning">{t("authErrors.accountLockedCountdown", { seconds: lockRemainSec })}</p>
            )}
            <button
              type="submit"
              disabled={loading}
              className="h-11 w-full rounded-xl bg-slate-900 text-white shadow-md transition-opacity hover:opacity-95 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {t("authPages.login.login")}
            </button>
            <p className="text-sm text-default-500">{t("authPages.login.noAccount")} <Link className="text-primary" to="/register">{t("authPages.login.registerNow")}</Link></p>
          </CardBody>
        </Card>
      </motion.form>
    </div>
  );
}
