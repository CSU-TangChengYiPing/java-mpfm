import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import { en } from "./i18n/resources/en";
import { zh } from "./i18n/resources/zh";

export type Locale = "zh" | "en";

export function resolveLocale(stored: string | null | undefined, fallback: Locale = "zh"): Locale {
  return stored === "en" || stored === "zh" ? stored : fallback;
}

const fallback: Locale = "zh";
const stored = typeof window !== "undefined" ? window.localStorage.getItem("locale") : null;
const lng = resolveLocale(stored, fallback);

export const resources = {
  zh,
  en,
} as const;

void i18n.use(initReactI18next).init({
  resources,
  lng,
  fallbackLng: fallback,
  interpolation: { escapeValue: false },
});

export default i18n;
