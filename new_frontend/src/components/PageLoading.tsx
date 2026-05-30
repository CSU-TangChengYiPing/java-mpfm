import { Spinner } from "@heroui/spinner";
import clsx from "clsx";

export interface PageLoadingProps {
  loading?: boolean;
}

export default function PageLoading({ loading = true }: PageLoadingProps) {
  return (
    <div
      className={clsx(
        "fixed inset-0 z-40 flex items-center justify-center bg-white/12 backdrop-blur-md dark:bg-black/16",
        !loading && "hidden"
      )}
    >
      <div className="rounded-2xl border border-white/25 bg-white/35 px-6 py-4 shadow-lg backdrop-blur-xl dark:border-white/15 dark:bg-black/25">
        <Spinner size="lg" />
      </div>
    </div>
  );
}
