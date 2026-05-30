import { Card, CardBody, CardHeader } from "@heroui/card";
import { LargeGlassInput } from "../../../components/common/LargeGlassField";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import UserAvatar from "../../../components/common/UserAvatar";
import { useInfiniteUsers } from "../../../hooks/useInfiniteUsers";
import { SEARCH_INPUT_DEBOUNCE_MS } from "./profileSearchModel";

/** 用户搜索页：按关键词分页检索并通过哨兵触发无限加载。 */
export default function ProfileSearchPage() {
  const { t } = useTranslation();
  const [searchInput, setSearchInput] = useState("");
  const { items, loading, hasMore, error, reset, loadMore } = useInfiniteUsers();

  useEffect(() => {
    const normalized = searchInput.trim();
    reset(normalized);
    const id = window.setTimeout(() => {
      void loadMore();
    }, SEARCH_INPUT_DEBOUNCE_MS);
    return () => window.clearTimeout(id);
  }, [loadMore, reset, searchInput]);

  useEffect(() => {
    const sentinel = document.getElementById("user-search-sentinel");
    if (!sentinel) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          void loadMore();
        }
      },
      { root: null, rootMargin: "160px 0px 160px 0px", threshold: 0 }
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [loadMore]);

  return (
    <section className="mx-auto flex w-full max-w-[920px] flex-col gap-4 p-2 md:p-4">
      <Card className="border border-white/30 bg-white/65 dark:bg-black/30">
        <CardHeader className="pb-2 text-base font-semibold">{t("profileSearch.title")}</CardHeader>
        <CardBody className="gap-3">
          <LargeGlassInput label={t("profileSearch.searchLabel")} placeholder={t("profileSearch.searchPlaceholder")} value={searchInput} onValueChange={setSearchInput} />
          <div className="max-h-[65vh] space-y-2 overflow-y-auto pr-1">
            {items.map((item) => (
              <div key={item.user_id} className="flex items-center gap-3 rounded-xl border border-white/30 bg-white/40 px-3 py-2 dark:bg-white/5">
                <UserAvatar userId={item.user_id} nickname={item.nickname} avatarUrl={item.avatar_url} />
                <div className="min-w-0">
                  <div className="truncate text-sm text-default-700 dark:text-default-100">{item.nickname}</div>
                  <div className="truncate text-xs text-default-400">@{item.user_id}</div>
                </div>
              </div>
            ))}
            <div id="user-search-sentinel" className="h-6 w-full" />
            {loading && <p className="text-center text-xs text-default-400">{t("profileSearch.loading")}</p>}
            {!loading && !!error && <p className="text-center text-xs text-danger">{error}</p>}
            {!loading && !hasMore && items.length > 0 && <p className="text-center text-xs text-default-400">{t("profileSearch.allLoaded")}</p>}
            {!loading && items.length === 0 && <p className="text-center text-xs text-default-400">{t("profileSearch.empty")}</p>}
          </div>
        </CardBody>
      </Card>
    </section>
  );
}


