import { Suspense, lazy, useEffect } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { Toaster } from "react-hot-toast";
import PageBackground from "./components/PageBackground";
import PageLoading from "./components/PageLoading";
import { AuthProvider } from "./hooks/AuthProvider";
import ProtectedRoute from "./hooks/ProtectedRoute";
import key from "./const/key";
import { loadTheme } from "./utils/theme";

const IndexPage = lazy(() => import("./pages/index"));
const PortalPage = lazy(() => import("./pages/portal"));
const LoginPage = lazy(() => import("./pages/auth/login"));
const RegisterPage = lazy(() => import("./pages/auth/register"));
const FileManagerPage = lazy(() => import("./pages/dashboard/storage/file_manager"));
const MountsPage = lazy(() => import("./pages/dashboard/storage/mounts"));
const SharesSharedUsersPage = lazy(() => import("./pages/dashboard/shares/shares_shared_users"));
const SharesRolesPage = lazy(() => import("./pages/dashboard/shares/shares_roles"));
const SharesRolePermissionsPage = lazy(() => import("./pages/dashboard/shares/shares_role_permissions"));
const SharesMyRolesPage = lazy(() => import("./pages/dashboard/shares/shares_my_roles"));
const UsersPage = lazy(() => import("./pages/dashboard/admin/users"));
const QosPage = lazy(() => import("./pages/dashboard/ops/qos"));
const SettingsPage = lazy(() => import("./pages/dashboard/admin/settings"));
const DebugPage = lazy(() => import("./pages/dashboard/ops/debug"));
const TasksPage = lazy(() => import("./pages/dashboard/ops/tasks"));
const MonitorPage = lazy(() => import("./pages/dashboard/ops/monitor"));

/** 应用根路由容器：负责主题初始化、鉴权路由装配与全局 toast 展示。 */
export default function App() {
  useEffect(() => {
    const theme = localStorage.getItem(key.theme);
    if (theme && !theme.startsWith("\"")) {
      localStorage.setItem(key.theme, JSON.stringify(theme));
    }
    loadTheme();
  }, []);

  return (
    <>
      <PageBackground />
      <Toaster
        position="top-center"
        toastOptions={{
          style: {
            maxWidth: "min(92vw, 640px)",
            whiteSpace: "pre-wrap",
            wordBreak: "break-word",
            overflowWrap: "anywhere",
            lineHeight: "1.4",
          },
        }}
      />
      <AuthProvider>
        <Suspense fallback={<PageLoading />}>
          <Routes>
            <Route path="/" element={<PortalPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route element={<ProtectedRoute />}>
              <Route path="/app" element={<IndexPage />}>
                <Route index element={<Navigate to="files" replace />} />
                <Route path="files" element={<FileManagerPage />} />
                <Route path="tasks" element={<TasksPage />} />
                <Route path="mounts" element={<MountsPage />} />
                <Route path="shares" element={<Navigate to="shared-users" replace />} />
                <Route path="shares/shared-users" element={<SharesSharedUsersPage />} />
                <Route path="shares/roles" element={<SharesRolesPage />} />
                <Route path="shares/role-permissions" element={<SharesRolePermissionsPage />} />
                <Route path="shares/my-roles" element={<SharesMyRolesPage />} />
                <Route path="profile" element={<Navigate to="/app/settings?tab=profile" replace />} />
                <Route path="profile/search" element={<Navigate to="/app/settings?tab=profile" replace />} />
                <Route path="users" element={<UsersPage />} />
                <Route path="qos" element={<QosPage />} />
                <Route path="monitor" element={<MonitorPage />} />
                <Route path="settings" element={<SettingsPage />} />
                <Route path="debug" element={<DebugPage />} />
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
      </AuthProvider>
    </>
  );
}
