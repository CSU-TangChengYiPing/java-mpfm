import http from "k6/http";
import { check } from "k6";
import { Trend, Rate } from "k6/metrics";

const baselineDuration = new Trend("baseline_duration_ms");
const resumableDuration = new Trend("resumable_duration_ms");
const runFailureRate = new Rate("run_failure_rate");

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const USERNAME = __ENV.USERNAME || "a123";
const PASSWORD = __ENV.PASSWORD || "123";
const TARGET_PATH = __ENV.TARGET_PATH || ".";
const MOUNT_ID_ENV = __ENV.MOUNT_ID || "";
const FILE_PATH = __ENV.FILE_PATH || "artifacts/perf/tmp/perf-64MB.bin";
const CHUNK_SIZE_MB = Number(__ENV.CHUNK_SIZE_MB || 4);
const CHUNK_SIZE = CHUNK_SIZE_MB * 1024 * 1024;

const FILE_BYTES = open(FILE_PATH, "b");
const FILE_SIZE = FILE_BYTES.byteLength;

export const options = {
  vus: Number(__ENV.VUS || 1),
  iterations: Number(__ENV.ITERATIONS || 2),
  thresholds: {
    run_failure_rate: ["rate<0.01"],
    baseline_duration_ms: ["p(95)<20000"],
    resumable_duration_ms: ["p(95)<20000"],
    http_req_failed: ["rate<0.01"],
  },
};

function login() {
  const resp = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ username: USERNAME, password: PASSWORD }), {
    headers: { "Content-Type": "application/json" },
  });
  check(resp, { "login status 200": (r) => r.status === 200 });
  const body = resp.json();
  return body.token.accessToken;
}

function pickMountId(authHeader) {
  if (MOUNT_ID_ENV) return MOUNT_ID_ENV;
  const resp = http.get(`${BASE_URL}/api/v1/mounts`, { headers: { Authorization: authHeader } });
  check(resp, { "list mounts 200": (r) => r.status === 200 });
  const list = resp.json();
  const enabled = list.find((item) => item.enabled === true);
  const picked = enabled || list[0];
  if (!picked || !picked.mountId) {
    throw new Error("no mount available for current user");
  }
  return picked.mountId;
}

function uploadBaseline(authHeader, mountId, name) {
  const formData = {
    file: http.file(FILE_BYTES, name, "application/octet-stream"),
  };
  const url = `${BASE_URL}/api/v1/files/upload?path=${encodeURIComponent(TARGET_PATH)}&mountId=${mountId}`;
  const params = {
    headers: {
      Authorization: authHeader,
      "If-Match": "*",
    },
    timeout: "120s",
  };
  const started = Date.now();
  const resp = http.post(url, formData, params);
  const elapsed = Date.now() - started;
  baselineDuration.add(elapsed);
  const ok = check(resp, {
    "baseline upload status 200": (r) => r.status === 200,
    "baseline upload success": (r) => (r.json("status") || "") === "SUCCESS",
  });
  runFailureRate.add(!ok);
}

function initUpload(authHeader, mountId, name) {
  const body = JSON.stringify({
    mountId,
    path: TARGET_PATH,
    filename: name,
    totalBytes: FILE_SIZE,
    chunkSizeBytes: CHUNK_SIZE,
  });
  const resp = http.post(`${BASE_URL}/api/v1/files/upload/init`, body, {
    headers: { Authorization: authHeader, "Content-Type": "application/json" },
  });
  check(resp, { "upload init status 200": (r) => r.status === 200 });
  return resp.json();
}

function uploadChunks(authHeader, mountId, uploadId, totalChunks) {
  const bytes = new Uint8Array(FILE_BYTES);
  for (let idx = 0; idx < totalChunks; idx += 1) {
    const start = idx * CHUNK_SIZE;
    const end = Math.min(FILE_SIZE, start + CHUNK_SIZE);
    const chunkBytes = bytes.slice(start, end);
    const formData = {
      file: http.file(chunkBytes.buffer, `chunk-${idx}.part`, "application/octet-stream"),
    };
    const url = `${BASE_URL}/api/v1/files/upload/chunk?mountId=${mountId}&uploadId=${uploadId}&chunkIndex=${idx}`;
    const resp = http.put(url, formData, {
      headers: { Authorization: authHeader, "If-Match": "*" },
      timeout: "120s",
    });
    const ok = check(resp, { "upload chunk status 200": (r) => r.status === 200 });
    runFailureRate.add(!ok);
  }
}

function completeUpload(authHeader, mountId, uploadId) {
  const body = JSON.stringify({ mountId, uploadId });
  const resp = http.post(`${BASE_URL}/api/v1/files/upload/complete`, body, {
    headers: { Authorization: authHeader, "Content-Type": "application/json" },
  });
  const ok = check(resp, {
    "upload complete status 200": (r) => r.status === 200,
    "upload complete success": (r) => (r.json("status") || "") === "SUCCESS",
  });
  runFailureRate.add(!ok);
}

function uploadResumable(authHeader, mountId, name) {
  const started = Date.now();
  const init = initUpload(authHeader, mountId, name);
  uploadChunks(authHeader, mountId, init.uploadId, init.totalChunks);
  completeUpload(authHeader, mountId, init.uploadId);
  const elapsed = Date.now() - started;
  resumableDuration.add(elapsed);
}

export default function () {
  const authHeader = `Bearer ${login()}`;
  const mountId = pickMountId(authHeader);
  const suffix = `${__VU}-${__ITER}-${Date.now()}`;
  uploadBaseline(authHeader, mountId, `k6-baseline-${suffix}.bin`);
  uploadResumable(authHeader, mountId, `k6-resumable-${suffix}.bin`);
}

export function handleSummary(data) {
  return {
    "artifacts/perf/k6-upload-summary.json": JSON.stringify(data, null, 2),
    stdout: JSON.stringify(
      {
        baseline_p95_ms: data.metrics.baseline_duration_ms.values["p(95)"],
        resumable_p95_ms: data.metrics.resumable_duration_ms.values["p(95)"],
        http_failed_rate: data.metrics.http_req_failed.values.rate,
        run_failure_rate: data.metrics.run_failure_rate.values.rate,
      },
      null,
      2
    ),
  };
}

