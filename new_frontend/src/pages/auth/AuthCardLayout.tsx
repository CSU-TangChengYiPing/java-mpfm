import { Card, CardBody } from "@heroui/card";
import { motion } from "motion/react";
import type { FormEvent, ReactNode } from "react";

type AuthCardLayoutProps = {
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  children: ReactNode;
};

export default function AuthCardLayout({ onSubmit, children }: AuthCardLayoutProps) {
  return (
    <div className="page-bg flex min-h-screen min-h-[100dvh] items-center justify-center px-3 py-4 pb-[env(safe-area-inset-bottom)] sm:px-6 sm:py-6">
      <motion.form onSubmit={onSubmit} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-[420px]">
        <Card className="w-full border border-white/40 bg-white/75 shadow-xl backdrop-blur-2xl dark:border-white/10 dark:bg-black/45">
          <CardBody className="gap-3 p-4 sm:p-6">
            {children}
          </CardBody>
        </Card>
      </motion.form>
    </div>
  );
}
