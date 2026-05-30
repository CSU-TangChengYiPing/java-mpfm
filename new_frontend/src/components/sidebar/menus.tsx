import { Button } from "@heroui/button";
import { useLocalStorage } from "@uidotdev/usehooks";
import clsx from "clsx";
import React from "react";
import { useTranslation } from "react-i18next";
import { matchPath, useLocation, useNavigate } from "react-router-dom";

import key from "../../const/key";
import type { MenuItem } from "../../config/site";

function isHrefActive(targetHref: string, pathname: string, search: string): boolean {
  const [targetPath, targetQuery] = targetHref.split("?");
  if (!matchPath(targetPath, pathname)) return false;
  if (!targetQuery) return true;
  const current = new URLSearchParams(search);
  const target = new URLSearchParams(targetQuery);
  for (const [k, v] of target.entries()) {
    if (current.get(k) !== v) return false;
  }
  return true;
}

/** 递归渲染导航菜单：同步路由激活态并控制子菜单折叠展开。 */
const renderItems = (
  items: MenuItem[],
  t: (k: string, d?: string) => string,
  children = false,
  downloadStats?: { running: number; pending: number },
) => {
  return items?.map((item) => {
    const navigate = useNavigate();
    const locate = useLocation();
    const [open, setOpen] = React.useState(!!item.autoOpen);
    const canOpen = React.useMemo(() => item.items && item.items.length > 0, [item.items]);
    const [b64img] = useLocalStorage(key.backgroundImage, "");
    const isActive = React.useMemo(() => {
      if (item.href) {
        return isHrefActive(item.href, locate.pathname, locate.search);
      }
      return false;
    }, [item.href, locate.pathname, locate.search]);

    React.useEffect(() => {
      if (item.items) {
        const shouldOpen = item.items.some((subItem) => subItem?.href && isHrefActive(subItem.href, locate.pathname, locate.search));
        if (shouldOpen) {
          setOpen(true);
        }
      }
    }, [item.items, locate.pathname, locate.search]);

    const panelRef = React.useRef<HTMLDivElement>(null);
    const label = t(`menu.${item.label}`, item.label);
    const isDownloadCenter = item.href === "/app/tasks";

    return (
      <div key={`${item.href ?? item.label}-${item.label}`}>
        <Button
          className={clsx(
            "flex w-full items-center justify-start text-left transition-all duration-300",
            isActive
              ? "translate-x-1 bg-primary/10 font-semibold text-primary shadow-none dark:bg-primary/20 dark:text-primary-400"
              : "hover:translate-x-1 hover:bg-default-100",
            b64img && "text-white backdrop-blur-md dark:text-white"
          )}
          color={isActive ? "primary" : "default"}
          endContent={
            canOpen ? (
              <div
                className={clsx(
                  "ml-auto relative h-3 w-3 transition-transform",
                  open && "rotate-180",
                  isActive ? "text-primary-500" : "text-primary-200 dark:text-white",
                  "before:absolute before:-left-[3px] before:top-1/2 before:block before:h-[4.5px] before:w-3 before:-translate-y-1/2 before:rotate-45 before:rounded-full before:bg-current before:content-[\"\"]",
                  "after:absolute after:left-[3px] after:top-1/2 after:block after:h-[4.5px] after:w-3 after:-translate-y-1/2 after:-rotate-45 after:rounded-full after:bg-current after:content-[\"\"]"
                )}
              />
            ) : (
              <div
                className={clsx(
                  "ml-auto h-1.5 w-3 rounded-full",
                  isActive ? "bg-primary-500" : "bg-primary-200 shadow-lg dark:bg-white"
                )}
                aria-hidden="true"
              />
            )
          }
          startContent={item.icon}
          variant={isActive ? (children ? "solid" : "shadow") : "light"}
          onPress={() => {
            if (item.href) {
              if (!isActive) {
                navigate(item.href);
              }
            } else if (canOpen) {
              setOpen(!open);
            }
          }}
        >
          <span className="flex min-w-0 items-center gap-2">
            <span className="truncate">{label}</span>
            {isDownloadCenter && downloadStats && (
              <span className="ml-1 flex items-center gap-1.5">
                <span className="inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-primary-500 px-1 text-[10px] leading-none text-white">
                  {downloadStats.running}
                </span>
                <span className="inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-warning-500 px-1 text-[10px] leading-none text-white">
                  {downloadStats.pending}
                </span>
              </span>
            )}
          </span>
        </Button>

        <div
          ref={panelRef}
          className="ml-4 overflow-hidden transition-all duration-300"
          style={{ height: open ? panelRef.current?.scrollHeight : 0 }}
        >
          {item.items && renderItems(item.items, t, true, downloadStats)}
        </div>
      </div>
    );
  });
};

interface MenusProps {
  items: MenuItem[];
  downloadStats?: { running: number; pending: number };
}

export default function Menus({ items, downloadStats }: MenusProps) {
  const { t } = useTranslation();
  return <div className="flex flex-1 flex-col justify-start gap-2">{renderItems(items, (k, d) => t(k, { defaultValue: d }), false, downloadStats)}</div>;
}
