import { useCallback, useRef, useState } from "react";
import i18n from "../i18n";
import ProfileController from "../controllers/profile";

type UserCard = { user_id: string; nickname: string; is_root: boolean; avatar_url: string };

/** 以 user_id 去重合并分页结果，避免游标回退或重复页导致列表重复渲染。 */
export function mergeUniqueUsers(existing: UserCard[], incoming: UserCard[]): UserCard[] {
  const seen = new Set(existing.map((it) => it.user_id));
  const out = [...existing];
  for (const item of incoming) {
    if (seen.has(item.user_id)) continue;
    seen.add(item.user_id);
    out.push(item);
  }
  return out;
}

/** 后端游标为空字符串表示无下一页，前端以此决定是否继续触发加载。 */
export function hasMoreFromCursor(nextCursor: string): boolean {
  return Boolean(nextCursor && nextCursor.trim() !== "");
}

/** 用户搜索分页状态机：统一维护关键词、去重集合、游标与错误状态，供页面复用。 */
export function useInfiniteUsers() {
  const [items, setItems] = useState<UserCard[]>([]);
  const [loading, setLoading] = useState(false);
  const [nextCursor, setNextCursor] = useState("");
  const [hasMore, setHasMore] = useState(true);
  const [keyword, setKeyword] = useState("");
  const [error, setError] = useState("");
  const seenRef = useRef<Set<string>>(new Set());

  const reset = useCallback((q: string) => {
    setItems([]);
    setLoading(false);
    setNextCursor("");
    setHasMore(true);
    setError("");
    setKeyword(q.trim());
    seenRef.current = new Set();
  }, []);

  const loadMore = useCallback(async () => {
    if (loading || !hasMore) return;
    setLoading(true);
    try {
      const resp = await ProfileController.searchUsers({ q: keyword, cursor: nextCursor || undefined, limit: 20 });
      const merged: UserCard[] = [];
      for (const item of resp.items || []) {
        if (seenRef.current.has(item.user_id)) continue;
        seenRef.current.add(item.user_id);
        merged.push(item);
      }
      setItems((prev) => mergeUniqueUsers(prev, merged));
      setNextCursor(resp.next_cursor || "");
      setHasMore(hasMoreFromCursor(resp.next_cursor || ""));
      setError("");
    } catch (err) {
      setError(err instanceof Error ? err.message : i18n.t("profileSearch.searchFailed"));
      setHasMore(false);
    } finally {
      setLoading(false);
    }
  }, [hasMore, keyword, loading, nextCursor]);

  return { items, loading, hasMore, keyword, error, reset, loadMore };
}
