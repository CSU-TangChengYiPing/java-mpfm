import type { ReactNode } from "react";
import { FiActivity, FiFolder, FiHardDrive, FiLink2, FiList, FiLock, FiSettings, FiShield, FiTool, FiUsers } from "react-icons/fi";

export interface MenuItem {
  label: string;
  href?: string;
  icon?: ReactNode;
  autoOpen?: boolean;
  items?: MenuItem[];
}

export const siteConfig = {
  navItems: [
    { label: "File Management", href: "/app/files", icon: <FiFolder size={16} /> },
    { label: "Download Center", href: "/app/tasks", icon: <FiList size={16} /> },
    { label: "Mounts Management", href: "/app/mounts", icon: <FiHardDrive size={16} /> },
    {
      label: "Shares Management",
      icon: <FiLink2 size={16} />,
      autoOpen: true,
      items: [
        { label: "0 Role Templates", href: "/app/shares/roles", icon: <FiShield size={14} /> },
        { label: "1 Role Permissions", href: "/app/shares/role-permissions", icon: <FiLock size={14} /> },
        { label: "2 Shared Links", href: "/app/shares/shared-users", icon: <FiUsers size={14} /> },
        { label: "3 My Shared Roles", href: "/app/shares/my-roles", icon: <FiUsers size={14} /> },
      ],
    },
    { label: "Users Management", href: "/app/users", icon: <FiUsers size={16} /> },
    { label: "QoS Policy Center", href: "/app/qos", icon: <FiSettings size={16} /> },
    { label: "Monitor Center", href: "/app/monitor", icon: <FiActivity size={16} /> },
    { label: "Settings", href: "/app/settings", icon: <FiSettings size={16} /> },
    { label: "DEBUG", href: "/app/debug", icon: <FiTool size={16} /> },
  ] satisfies MenuItem[],
};
