import { Spinner } from "@heroui/spinner";
import { Suspense } from "react";
import { Outlet } from "react-router-dom";

import AppLayout from "../layouts/AppLayout";

export default function IndexPage() {
  return (
    <AppLayout>
      <Suspense
        fallback={
          <div className="flex justify-center px-10">
            <Spinner />
          </div>
        }
      >
        <Outlet />
      </Suspense>
    </AppLayout>
  );
}
