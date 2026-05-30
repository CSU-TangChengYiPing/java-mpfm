import { HeroUIProvider } from "@heroui/system";
import { ReactNode } from "react";

export function Provider({ children }: { children: ReactNode }) {
  return <HeroUIProvider>{children}</HeroUIProvider>;
}
