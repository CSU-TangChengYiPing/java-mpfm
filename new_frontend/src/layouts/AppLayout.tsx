import { BreadcrumbItem, Breadcrumbs } from "@heroui/breadcrumbs";
import { Button } from "@heroui/button";
import { ModalBody, ModalContent, ModalFooter, ModalHeader } from "@heroui/modal";
import { useLocalStorage } from "@uidotdev/usehooks";
import clsx from "clsx";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { MdMenu, MdMenuOpen } from "react-icons/md";
import { useLocation, useNavigate } from "react-router-dom";

import SideBar from "../components/sidebar";
import key from "../const/key";
import { siteConfig } from "../config/site";
import FileManager from "../controllers/file_manager";
import { useAuth } from "../hooks/useAuth";
import BlurModal from "../components/common/BlurModal";

/** 控制台布局壳：负责侧边栏、面包屑与主内容区域容器编排。 */
export default function AppLayout({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const contentRef = useRef<HTMLDivElement>(null);
  const [openSideBar, setOpenSideBar] = useLocalStorage<boolean>(key.sideBarOpen, true);
  const [backgroundImage] = useLocalStorage<string>(key.backgroundImage, "");
  const [pendingResumeCount, setPendingResumeCount] = useState(0);
  const menus = useMemo(() => {
    if (user?.is_root) return siteConfig.navItems;
    return siteConfig.navItems.filter((m) => m.href !== "/app/users" && m.href !== "/app/permissions" && m.href !== "/app/debug");
  }, [user?.is_root]);

  useEffect(() => {
    contentRef.current?.scrollTo?.({ top: 0, behavior: "smooth" });
  }, [location.pathname]);

  useEffect(() => {
    const pendingResume = FileManager.consumePendingResumeHintCount();
    if (pendingResume > 0) {
      setPendingResumeCount(pendingResume);
    }
  }, []);

  const title = useMemo(() => {
    const item = menus.find((menu) => menu.href && location.pathname.startsWith(menu.href));
    return item ? [t(`menu.${item.label}`, { defaultValue: item.label })] : [t("menu.File Management", { defaultValue: "File Management" })];
  }, [location.pathname, menus, t]);

  return (
    <div
      className="page-bg relative flex h-screen items-stretch overflow-hidden"
      style={{
        backgroundImage: backgroundImage ? `url(${backgroundImage})` : undefined,
        backgroundSize: "cover",
        backgroundPosition: "center",
      }}
    >
      <SideBar items={menus} open={openSideBar} onClose={() => setOpenSideBar(false)} />

      <div
        ref={contentRef}
        className={clsx("relative z-10 flex-1 min-h-0 overflow-hidden pb-0 transition-all duration-300 ease-in-out")}
        style={{ scrollbarGutter: "stable" }}
      >
        <div
          className={clsx(
            "sticky left-0 top-2 z-30 m-2 mb-0 flex h-10 items-center rounded-full bg-background !bg-opacity-50 font-bold text-xl shadow-sm shadow-primary-50 backdrop-blur-lg",
            "dark:bg-background dark:shadow-primary-100"
          )}
        >
          <div className={clsx("z-50 mr-1 ml-0 ease-in-out md:relative md:z-auto md:!ml-0 md:pl-0", openSideBar && "pl-2") }>
            <Button isIconOnly radius="full" variant="light" onPress={() => setOpenSideBar(!openSideBar)}>
              {openSideBar ? <MdMenuOpen size={24} /> : <MdMenu size={24} />}
            </Button>
          </div>
          <Breadcrumbs isDisabled size="lg">
            {title.map((item) => (
              <BreadcrumbItem key={item}>
                <AnimatePresence mode="wait">
                  <motion.div key={item} initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 10 }} transition={{ duration: 0.3 }}>
                    {item}
                  </motion.div>
                </AnimatePresence>
              </BreadcrumbItem>
            ))}
          </Breadcrumbs>
        </div>
        {children}
      </div>
      <BlurModal
        isOpen={pendingResumeCount > 0}
        isDismissable={false}
        hideCloseButton
        onClose={() => setPendingResumeCount(0)}
        radius="sm"
      >
        <ModalContent>
          <ModalHeader>{t("tasks.resumeRequiredModalTitle")}</ModalHeader>
          <ModalBody>
            <div className="text-sm text-default-700 dark:text-default-300">
              {t("tasks.resumeRequiredModalBody", { count: pendingResumeCount })}
            </div>
          </ModalBody>
          <ModalFooter>
            <Button variant="light" onPress={() => setPendingResumeCount(0)}>
              {t("tasks.resumeRequiredModalLater")}
            </Button>
            <Button
              color="primary"
              variant="flat"
              onPress={() => {
                setPendingResumeCount(0);
                navigate("/app/tasks");
              }}
            >
              {t("tasks.resumeRequiredModalGo")}
            </Button>
          </ModalFooter>
        </ModalContent>
      </BlurModal>
    </div>
  );
}
