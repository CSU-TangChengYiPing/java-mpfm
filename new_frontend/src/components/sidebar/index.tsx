import { Button } from "@heroui/button";
import { useLocalStorage } from "@uidotdev/usehooks";
import clsx from "clsx";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FiArrowDown, FiArrowUp } from "react-icons/fi";
import { IoMdLogOut } from "react-icons/io";
import { MdDarkMode, MdLanguage, MdLightMode } from "react-icons/md";

import key from "../../const/key";
import type { MenuItem } from "../../config/site";
import FileManager from "../../controllers/file_manager";
import { useAuth } from "../../hooks/useAuth";
import { toggleTheme } from "../../utils/theme";
import { subscribeTransferRate } from "../../utils/transferRateMeter";
import Menus from "./menus";
import UserAvatar from "../common/UserAvatar";

interface SideBarProps {
  open: boolean;
  items: MenuItem[];
  onClose?: () => void;
}

const ONLY_ROOT_ITEMS = new Set(["/app/users", "/app/debug", "/app/qos", "/app/monitor"]);

/** 侧边导航容器：统一菜单过滤、主题语言切换与账号区交互。 */
export default function SideBar({ open, items, onClose }: SideBarProps) {
  const { user, logout } = useAuth();
  const { t, i18n } = useTranslation();
  const [backgroundImage] = useLocalStorage<string>(key.backgroundImage, "");
  const [avatarVersion] = useLocalStorage<string>(key.profileAvatarVersion, "");
  const [avatarSignal, setAvatarSignal] = useState("");
  const [profileOpen, setProfileOpen] = useState(false);
  const [transferRate, setTransferRate] = useState<{ uploadBps: number; downloadBps: number }>({ uploadBps: 0, downloadBps: 0 });
  const [downloadStats, setDownloadStats] = useState<{ running: number; pending: number }>({ running: 0, pending: 0 });
  const hasBackground = !!backgroundImage;
  const isDark = document.documentElement.classList.contains("dark");

  
  const visibleItems = useMemo<MenuItem[]>(() => {
    if (user?.is_root) return items;
    return items.filter((it) => !it.href || !ONLY_ROOT_ITEMS.has(it.href));
  }, [items, user?.is_root]);

  /* ------ 头像 ------ */
  const avatarText = useMemo(() => {
    const source = (user?.nickname || user?.displayName || user?.username || user?.user_id || "U").trim();
    return source.slice(0, 1).toUpperCase();
  }, [user?.displayName, user?.nickname, user?.user_id, user?.username]);

  const avatarUrl = useMemo(() => {
    const base = user?.avatarUrl || "";
    if (!base) return "";
    const version = avatarSignal || avatarVersion;
    if (!version) return base;
    const sep = base.includes("?") ? "&" : "?";
    return `${base}${sep}v=${encodeURIComponent(version)}`;
  }, [avatarSignal, avatarVersion, user?.avatarUrl]);

  useEffect(() => {
    const onAvatarUpdated = () => setAvatarSignal(String(Date.now()));
    window.addEventListener("mpfm:avatar-updated", onAvatarUpdated);
    return () => window.removeEventListener("mpfm:avatar-updated", onAvatarUpdated);
  }, []);

  useEffect(() => {
    if (!user || user.is_root) return;
    return subscribeTransferRate((next) => {
      setTransferRate({
        uploadBps: Math.max(0, Number(next.uploadBps ?? 0)),
        downloadBps: Math.max(0, Number(next.downloadBps ?? 0)),
      });
    });
  }, [user]);

  useEffect(() => {
    const unsubscribe = FileManager.subscribeDownloadCenterTasks((tasks) => {
      let running = 0;
      let pending = 0;
      for (const item of tasks) {
        const status = (item.status || "").toUpperCase();
        if (status === "RUNNING" || status === "PENDING" || status === "RESUMING") running += 1;
        if (status === "FAILED" || status === "PAUSED") pending += 1;
      }
      setDownloadStats({ running, pending });
    });
    return () => {
      unsubscribe();
    };
  }, []);

  const totalRate = transferRate.uploadBps + transferRate.downloadBps;
  const rateColorClass = totalRate <= 0
    ? "text-default-500 bg-default-100/80 dark:text-default-300 dark:bg-white/10"
    : totalRate <= 8 * 1024 * 1024
      ? "text-success-700 bg-success-100/80 dark:text-success-300 dark:bg-success-500/15"
      : totalRate <= 32 * 1024 * 1024
        ? "text-warning-700 bg-warning-100/80 dark:text-warning-300 dark:bg-warning-500/15"
        : "text-danger-700 bg-danger-100/80 dark:text-danger-300 dark:bg-danger-500/15";

  const formatBps = (bps: number) => {
    if (bps >= 1024 * 1024) return `${(bps / (1024 * 1024)).toFixed(1)}M/s`;
    if (bps >= 1024) return `${(bps / 1024).toFixed(1)}K/s`;
    return `${Math.round(bps)}B/s`;
  };

  const toggleLocale = () => {
    const current = i18n.resolvedLanguage || i18n.language;
    const next = current.toLowerCase().startsWith("zh") ? "en" : "zh";
    void i18n.changeLanguage(next);
    window.localStorage.setItem(key.locale, next);
  };

  return (
    <>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            className="fixed inset-y-0 left-64 right-0 z-40 bg-black/20 backdrop-blur-[1px] md:hidden"
            aria-hidden="true"
            onClick={onClose}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, transition: { duration: 0.15 } }}
            transition={{ duration: 0.2, delay: 0.15 }}
          />
        )}
      </AnimatePresence>
      <motion.div
        className={clsx(
          "fixed left-0 top-0 z-50 h-full overflow-hidden rounded-r-2xl md:static md:rounded-none md:shadow-none",
          hasBackground
            ? "bg-transparent backdrop-blur-md"
            : "bg-content1/70 shadow-xl backdrop-blur-xl backdrop-saturate-150",
          "md:bg-transparent md:backdrop-blur-none md:backdrop-saturate-100 md:shadow-none"
        )}
        initial={{ width: 0 }}
        animate={{ width: open ? "16rem" : 0 }}
        transition={{ type: open ? "spring" : "tween", stiffness: 150, damping: open ? 15 : 10 }}
        style={{ overflow: "hidden" }}
      >
        <motion.div className="relative float-right z-30 flex h-full w-64 flex-col items-stretch px-4 py-3 transition-transform duration-300 ease-in-out">
          <div className="mt-3 mb-4 ml-1 flex items-center justify-start gap-3 px-2">
            <div className="h-5 w-1 rounded-full bg-primary shadow-sm" />
            <div className={clsx("select-none text-xl font-bold tracking-wide", hasBackground ? "text-white" : "text-default-900 dark:text-white")}>MPFM</div>
          </div>
          <div className="hide-scrollbar flex flex-1 flex-col overflow-y-auto px-1">
            <Menus items={visibleItems} downloadStats={downloadStats} />
            <div className="mt-auto mb-10 space-y-3 px-2 md:mb-0">
              {user && (
                <div className="relative space-y-2">
                  <button
                    type="button"
                    onClick={() => setProfileOpen((prev) => !prev)}
                    className="flex w-full items-center gap-3 rounded-xl border border-primary-200/60 bg-primary-50/70 px-3 py-2 text-left transition-colors hover:bg-primary-100/70 dark:border-primary-300/40 dark:bg-white/10 dark:hover:bg-white/15"
                  >
                    <UserAvatar userId={user.user_id || user.userId} nickname={user.nickname || user.displayName || user.username || avatarText} avatarUrl={avatarUrl} size={36} />
                    <div className="min-w-0">
                      <div className="truncate text-sm font-semibold text-primary-700 dark:text-white">{user.nickname || user.displayName || user.username || user.user_id || user.userId}</div>
                      <div className="truncate text-xs text-primary-500 dark:text-white/80">@{user.username || user.user_id || user.userId}</div>
                    </div>
                  </button>
                  <AnimatePresence initial={false}>
                    {profileOpen && (
                      <motion.div
                        initial={{ opacity: 0, y: 8, scale: 0.98 }}
                        animate={{ opacity: 1, y: 0, scale: 1 }}
                        exit={{ opacity: 0, y: 8, scale: 0.98 }}
                        transition={{ duration: 0.15 }}
                        className="absolute bottom-[calc(100%+10px)] left-0 z-[120] w-full rounded-xl border border-default-200/70 bg-white/95 px-3 py-2 text-xs text-default-600 shadow-xl backdrop-blur-sm dark:border-white/20 dark:bg-black/85 dark:text-white/90"
                      >
                        <div className="absolute -bottom-2 left-6 h-3 w-3 rotate-45 border-b border-r border-default-200/70 bg-white/95 dark:border-white/20 dark:bg-black/85" />
                        <div>{t("sidebar.role")}: {user.is_root ? t("sidebar.root") : t("sidebar.user")}</div>
                        <div className="mt-1 truncate">QoS: {user.qosProfile || user.qos_profile || "default"}</div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              )}
              <div className="grid grid-cols-2 gap-2">
                <Button
                  className="w-full bg-primary-50/50 font-medium text-primary-600 shadow-sm backdrop-blur-sm transition-all duration-300 hover:bg-primary-100/80 hover:shadow-md"
                  radius="full"
                  variant="flat"
                  onPress={() => {
                    toggleTheme();
                  }}
                  startContent={!isDark ? <MdLightMode size={16} /> : <MdDarkMode size={16} />}
                >
                  {t("sidebar.theme")}
                </Button>
                <Button
                  className="w-full bg-primary-50/50 font-medium text-primary-600 shadow-sm backdrop-blur-sm transition-all duration-300 hover:bg-primary-100/80 hover:shadow-md"
                  radius="full"
                  variant="flat"
                  onPress={toggleLocale}
                  startContent={<MdLanguage size={16} />}
                >
                  {t("sidebar.localeSwitch")}
                </Button>
              </div>
              <Button
                className="mb-2 w-full bg-danger-50/50 font-medium text-danger-500 shadow-sm backdrop-blur-sm transition-all duration-300 hover:bg-danger-100/80 hover:shadow-md"
                radius="full"
                variant="flat"
                onPress={() => { void logout(); }}
                startContent={<IoMdLogOut size={18} />}
              >
                {t("sidebar.logout")}
              </Button>
              {!user?.is_root && (
                <div className={`mt-2 flex items-center justify-between rounded-xl px-3 py-2 text-xs font-semibold ${rateColorClass}`}>
                  <div className="flex items-center gap-1">
                    <FiArrowUp size={12} />
                    <span>{formatBps(transferRate.uploadBps)}</span>
                  </div>
                  <div className="flex items-center gap-1">
                    <FiArrowDown size={12} />
                    <span>{formatBps(transferRate.downloadBps)}</span>
                  </div>
                </div>
              )}
            </div>
          </div>
        </motion.div>
      </motion.div>
    </>
  );
}


