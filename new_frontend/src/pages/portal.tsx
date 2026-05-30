import { Button } from "@heroui/button";
import { Card, CardBody } from "@heroui/card";
import { motion } from "motion/react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";

export default function PortalPage() {
  const { t } = useTranslation();
  const { isAuthenticated } = useAuth();

  return (
    <div className="page-bg flex min-h-screen items-center justify-center p-6">
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
        <Card className="w-[560px] border border-white/40 dark:border-white/10 bg-white/70 dark:bg-black/45 backdrop-blur-2xl shadow-xl">
          <CardBody className="gap-4 p-8">
            <h1 className="text-3xl font-semibold text-black/90 dark:text-white/90">{t("authPages.portal.title")}</h1>
            <p className="text-default-500">{t("authPages.portal.subtitle")}</p>
            <div className="flex flex-wrap gap-3">
              <Button as={Link} color="primary" to={isAuthenticated ? "/app/files" : "/login"}>
                {t("authPages.portal.openApp")}
              </Button>
              <Button as={Link} variant="flat" to="/login">{t("authPages.portal.login")}</Button>
              <Button as={Link} variant="flat" to="/register">{t("authPages.portal.register")}</Button>
            </div>
          </CardBody>
        </Card>
      </motion.div>
    </div>
  );
}
