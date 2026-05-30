export const AUTH_KEY = "mpfm_auth_user";

export type AuthUser = {
  userId: string;
  username: string;
  displayName: string;
  role: string;
  qosProfile?: string;
  status?: string;
  email?: string;
  phone?: string;
  avatarUrl?: string;
  language?: string;
  fileViewMode?: string;
  tokenType?: string;
  accessToken?: string;
  refreshToken?: string;
  sessionId?: string;
  user_id?: string;
  nickname?: string;
  is_root?: boolean;
  qos_profile?: string;
};

/** 统一判定 root 身份，兼容新旧字段（role/is_root）以避免历史会话格式导致误判。 */
export function isRootUser(user: AuthUser | null | undefined): boolean {
  if (!user) return false;
  if (typeof user.is_root === "boolean") return user.is_root;
  return (user.role || "").toLowerCase() === "root";
}

function withLegacyFields(user: AuthUser): AuthUser {
  return {
    ...user,
    user_id: user.userId,
    nickname: user.displayName,
    is_root: (user.role || "").toLowerCase() === "root",
    qos_profile: user.qosProfile,
  };
}

/** 从本地存储恢复会话并补齐兼容字段；关键标识缺失时返回 null，防止脏数据进入鉴权流程。 */
export function loadAuthUser(): AuthUser | null {
  const raw = localStorage.getItem(AUTH_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<AuthUser>;
    const userId = parsed.userId || parsed.user_id;
    const username = parsed.username || parsed.user_id;
    if (!userId || !username) return null;
    return withLegacyFields({
      userId,
      username,
      displayName: parsed.displayName || parsed.nickname || username,
      role: parsed.role || (parsed.is_root ? "root" : "user"),
      qosProfile: parsed.qosProfile || parsed.qos_profile,
      status: parsed.status,
      email: parsed.email,
      phone: parsed.phone,
      avatarUrl: parsed.avatarUrl,
      language: parsed.language,
      fileViewMode: parsed.fileViewMode,
      tokenType: parsed.tokenType,
      accessToken: parsed.accessToken,
      refreshToken: parsed.refreshToken,
      sessionId: parsed.sessionId,
    });
  } catch {
    return null;
  }
}

/** 为写回存储或旧页面消费补齐 legacy 字段，保证新旧模型并行期行为一致。 */
export function withLegacyAuthUser(user: AuthUser): AuthUser {
  return withLegacyFields(user);
}
