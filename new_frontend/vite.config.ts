import fs from "node:fs";
import path from "node:path";
import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

function resolveMaybeRelative(cwd: string, maybePath: string): string {
  if (!maybePath) return "";
  return path.isAbsolute(maybePath) ? maybePath : path.resolve(cwd, maybePath);
}

function resolveDevHttps(
  cwd: string,
  env: Record<string, string>
): false | { key: Buffer; cert: Buffer } | { pfx: Buffer; passphrase?: string } {
  const enabled = (env.VITE_DEV_HTTPS ?? "false").toLowerCase() === "true";
  if (!enabled) return false;

  const pfxFile = resolveMaybeRelative(cwd, env.VITE_DEV_HTTPS_PFX_FILE ?? "");
  const pfxPassphrase = env.VITE_DEV_HTTPS_PFX_PASSPHRASE ?? "";
  if (pfxFile) {
    return {
      pfx: fs.readFileSync(pfxFile),
      passphrase: pfxPassphrase || undefined,
    };
  }

  const keyFile = resolveMaybeRelative(cwd, env.VITE_DEV_HTTPS_KEY_FILE ?? "");
  const certFile = resolveMaybeRelative(cwd, env.VITE_DEV_HTTPS_CERT_FILE ?? "");
  if (!keyFile || !certFile) {
    throw new Error(
      "VITE_DEV_HTTPS=true 时，必须配置 VITE_DEV_HTTPS_PFX_FILE 或同时配置 VITE_DEV_HTTPS_KEY_FILE + VITE_DEV_HTTPS_CERT_FILE"
    );
  }

  return {
    key: fs.readFileSync(keyFile),
    cert: fs.readFileSync(certFile),
  };
}

export default defineConfig(({ mode }) => {
  const cwd = process.cwd();
  const env = loadEnv(mode, cwd, "");
  const apiTarget = env.VITE_DEV_BACKEND_TARGET || env.VITE_BACKEND_ORIGIN || "https://localhost:8443";
  const httpsConfig = resolveDevHttps(cwd, env);

  return {
    plugins: [react()],
    test: {
      include: ["src/**/*.{test,spec}.{ts,tsx}"],
      exclude: ["node_modules/**", "e2e/**", "test-results/**", "playwright-report/**"],
    },
    server: {
      port: 5173,
      https: httpsConfig || undefined,
      proxy: {
        "/api": {
          target: apiTarget,
          changeOrigin: true,
          secure: false
        }
      }
    }
  };
});
