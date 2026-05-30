import { createContext } from "react";
import type { AuthUser } from "./authStorage";

export type AuthContextValue = {
  user: AuthUser | null;
  isAuthenticated: boolean;
  initializing: boolean;
  refreshUser: () => Promise<void>;
  updateLocalUser: (patch: Partial<AuthUser>) => void;
  login: (username: string, password: string, captchaId?: string, captchaAnswer?: string) => Promise<void>;
  register: (username: string, nickname: string, password: string, confirmPassword: string, captchaId?: string, captchaAnswer?: string) => Promise<void>;
  logout: () => Promise<void>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);
