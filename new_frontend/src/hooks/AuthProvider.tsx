import { useEffect, useMemo, useState, type ReactNode } from "react";
import { login as loginReq, logout as logoutReq, refresh as refreshReq, register as registerReq } from "../controllers/auth";
import ProfileController from "../controllers/profile";
import { AuthContext, type AuthContextValue } from "./authContextValue";
import { AUTH_KEY, loadAuthUser, type AuthUser, withLegacyAuthUser } from "./authStorage";

const usernamePattern = /^[A-Za-z][A-Za-z0-9._-]{2,31}$/;

function persistUser(next: AuthUser | null): void {
  if (!next) {
    localStorage.removeItem(AUTH_KEY);
    return;
  }
  localStorage.setItem(AUTH_KEY, JSON.stringify(withLegacyAuthUser(next)));
}

/** 鉴权上下文提供者：统一登录注册、会话刷新、资料回填与本地持久化。 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => loadAuthUser());
  const [initializing, setInitializing] = useState(true);

  const persistAndSetUser = (next: AuthUser | null) => {
    persistUser(next);
    setUser(next);
  };

  useEffect(() => {
    let active = true;
    async function bootstrap() {
      const cached = loadAuthUser();
      if (!cached?.refreshToken || !cached.sessionId) {
        persistAndSetUser(null);
        setInitializing(false);
        return;
      }
      try {
        const refreshed = await refreshReq(cached.refreshToken, cached.sessionId);
        const me = await ProfileController.me();
        if (!active) return;
        persistAndSetUser(
          withLegacyAuthUser({
            ...refreshed,
            userId: me.userId,
            username: me.username,
            displayName: me.displayName,
            role: me.role,
            status: me.status,
            qosProfile: me.qosProfile,
            email: me.email,
            phone: me.phone,
            avatarUrl: me.avatar_url,
            language: me.language,
            fileViewMode: me.fileViewMode,
          })
        );
      } catch {
        persistAndSetUser(null);
      } finally {
        if (active) setInitializing(false);
      }
    }
    void bootstrap();
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const onUnauthorized = () => {
      persistAndSetUser(null);
      setInitializing(false);
    };
    window.addEventListener("mpfm:unauthorized", onUnauthorized);
    return () => {
      window.removeEventListener("mpfm:unauthorized", onUnauthorized);
    };
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: !!user,
      initializing,
      async refreshUser() {
        if (!user?.refreshToken || !user.sessionId) return;
        const refreshed = await refreshReq(user.refreshToken, user.sessionId);
        const me = await ProfileController.me();
        persistAndSetUser(withLegacyAuthUser({ ...user, ...refreshed, ...me }));
      },
      updateLocalUser(patch: Partial<AuthUser>) {
        setUser((prev) => {
          if (!prev) return prev;
          const next = { ...prev, ...patch };
          persistUser(next);
          return next;
        });
      },
      async login(username: string, password: string, captchaId?: string, captchaAnswer?: string) {
        if (!username.trim() || !password) throw new Error("Username and password are required");
        const normalized = username.trim();
        const next = await loginReq(normalized, password, captchaId, captchaAnswer);
        persistAndSetUser(withLegacyAuthUser(next));
        try {
          const me = await ProfileController.me();
          persistAndSetUser(withLegacyAuthUser({ ...next, ...me }));
        } catch (error) {
          const message = error instanceof Error ? error.message : "";
          if (!message.includes("AUTH_REQUIRED")) {
            throw error;
          }
        }
      },
      async register(username: string, nickname: string, password: string, confirmPassword: string, captchaId?: string, captchaAnswer?: string) {
        if (!username.trim() || !password) throw new Error("Username and password are required");
        if (password !== confirmPassword) throw new Error("Passwords do not match");
        const normalized = username.trim();
        if (!usernamePattern.test(normalized)) {
          throw new Error("Username must be 3-32 chars, start with letter, allowed: A-Z a-z 0-9 . _ -");
        }
        const displayName = nickname.trim() || normalized;
        const next = await registerReq({ username: normalized, displayName, password, captchaId, captchaAnswer });
        persistAndSetUser(withLegacyAuthUser({
          ...next,
          username: next.username || normalized,
          displayName: next.displayName || displayName,
        }));
        try {
          const me = await ProfileController.me();
          persistAndSetUser(withLegacyAuthUser({ ...next, ...me }));
        } catch (error) {
          const message = error instanceof Error ? error.message : "";
          if (!message.includes("AUTH_REQUIRED")) {
            throw error;
          }
        }
      },
      async logout() {
        try {
          await logoutReq(user?.refreshToken, user?.sessionId);
        } catch {
          // 忽略网络错误，确保本地状态清理
        }
        persistAndSetUser(null);
      },
    }),
    [initializing, user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}


