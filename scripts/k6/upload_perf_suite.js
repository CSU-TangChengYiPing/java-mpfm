import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const USERNAME = __ENV.USERNAME || "a123";
const PASSWORD = __ENV.PASSWORD || "123";
const TARGET_PATH = __ENV.TARGET_PATH || ".";
const MOUNT_ID_ENV = __ENV.MOUNT_ID || "";
const FILE_PATH = __ENV.FILE_PATH || "artifacts/perf/tmp/perf-16MB.bin";
const CHUNK_SIZE_MB = Number(__ENV.CHUNK_SIZE_MB || 4);
const CHUNK_SIZE = CHUNK_SIZE_MB * 1024 * 1024;
const RETRY_MAX = Number(__ENV.RETRY_MAX || 4);
const RETRY_BASE_MS = Number(__ENV.RETRY_BASE_MS || 250);
const SCENARIO_MODE = (__ENV.SCENARIO_MODE || "realistic").toLowerCase();

const FILE_BYTES = open(FILE_PATH, "b");
const FILE_SIZE = FILE_BYTES.byteLength;

const realisticDuration = new Trend("upload_realistic_duration_ms");
const stressDuration = new Trend("upload_stress_duration_ms");
const uploadFailureRate = new Rate("upload_failure_rate");
const upload429Rate = new Rate("upload_429_rate");
const upload5xxRate = new Rate("upload_5xx_rate");
const realisticFailureRate = new Rate("upload_realistic_failure_rate");
const realistic429Rate = new Rate("upload_realistic_429_rate");
const realistic5xxRate = new Rate("upload_realistic_5xx_rate");
const stressFailureRate = new Rate("upload_stress_failure_rate");
const stress429Rate = new Rate("upload_stress_429_rate");
const stress5xxRate = new Rate("upload_stress_5xx_rate");
const uploadRetryCounter = new Counter("upload_retry_count");
const uploadedBytesCounter = new Counter("upload_success_bytes");
const uploadReqCounter = new Counter("upload_req_count");
const uploadReq429Counter = new Counter("upload_req_429_count");
const uploadReq5xxCounter = new Counter("upload_req_5xx_count");
const uploadReqNon2xxCounter = new Counter("upload_req_non2xx_count");

const allScenarios = {
  realistic: {
    executor: "constant-arrival-rate",
    rate: Number(__ENV.REAL_RATE || 2),
    timeUnit: "1s",
    duration: __ENV.REAL_DURATION || "40s",
    preAllocatedVUs: Number(__ENV.REAL_PRE_VUS || 4),
    maxVUs: Number(__ENV.REAL_MAX_VUS || 12),
    exec: "realisticScenario",
    gracefulStop: "10s",
  },
  stress: {
    executor: "ramping-vus",
    startTime: __ENV.STRESS_START || "45s",
    stages: [
      { duration: "10s", target: Number(__ENV.STRESS_LOW_VUS || 2) },
      { duration: "15s", target: Number(__ENV.STRESS_HIGH_VUS || 12) },
      { duration: "10s", target: Number(__ENV.STRESS_LOW_VUS || 2) },
    ],
    exec: "stressScenario",
    gracefulStop: "10s",
  },
};

function resolveScenarios() {
  if (SCENARIO_MODE === "realistic") {
    return { realistic: allScenarios.realistic };
  }
  if (SCENARIO_MODE === "stress") {
    return { stress: allScenarios.stress };
  }
  return allScenarios;
}

export const options = {
  scenarios: resolveScenarios(),
  thresholds: {
    upload_realistic_failure_rate: ["rate<0.005"],
    upload_realistic_429_rate: ["rate<0.05"],
    upload_realistic_5xx_rate: ["rate<0.005"],
  },
};

function sleepMs(ms) {
  sleep(ms / 1000);
}

function isUploadApi(url) {
  return url.includes("/api/v1/files/upload");
}

function markUploadResponse(resp, mode) {
  uploadReqCounter.add(1);
  const status = resp.status || 0;
  const is429 = status === 429;
  const is5xx = status >= 500 && status <= 599;
  const isNon2xx = status < 200 || status >= 300;
  upload429Rate.add(is429);
  upload5xxRate.add(is5xx);
  if (mode === "realistic") {
    realistic429Rate.add(is429);
    realistic5xxRate.add(is5xx);
  } else if (mode === "stress") {
    stress429Rate.add(is429);
    stress5xxRate.add(is5xx);
  }
  if (is429) uploadReq429Counter.add(1);
  if (is5xx) uploadReq5xxCounter.add(1);
  if (isNon2xx) uploadReqNon2xxCounter.add(1);
}

function trackedPost(url, body, params, mode) {
  const resp = http.post(url, body, params);
  if (isUploadApi(url)) {
    markUploadResponse(resp, mode);
  }
  return resp;
}

function trackedPut(url, body, params, mode) {
  const resp = http.put(url, body, params);
  if (isUploadApi(url)) {
    markUploadResponse(resp, mode);
  }
  return resp;
}

function login() {
  const resp = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ username: USERNAME, password: PASSWORD }), {
    headers: { "Content-Type": "application/json" },
  });
  check(resp, { "login status 200": (r) => r.status === 200 });
  return resp.json().token.accessToken;
}

function pickMountId(authHeader) {
  if (MOUNT_ID_ENV) return MOUNT_ID_ENV;
  const resp = http.get(`${BASE_URL}/api/v1/mounts`, { headers: { Authorization: authHeader } });
  check(resp, { "list mounts 200": (r) => r.status === 200 });
  const list = resp.json();
  const enabled = list.find((item) => item.enabled === true);
  const picked = enabled || list[0];
  if (!picked || !picked.mountId) throw new Error("no mount available");
  return picked.mountId;
}

export function setup() {
  const authHeader = `Bearer ${login()}`;
  const mountId = pickMountId(authHeader);
  return { authHeader, mountId };
}

function initUpload(authHeader, mountId, name, mode) {
  const resp = trackedPost(
    `${BASE_URL}/api/v1/files/upload/init`,
    JSON.stringify({ mountId, path: TARGET_PATH, filename: name, totalBytes: FILE_SIZE, chunkSizeBytes: CHUNK_SIZE }),
    { headers: { Authorization: authHeader, "Content-Type": "application/json" }, timeout: "60s" },
    mode
  );
  return { ok: resp.status === 200, resp };
}

function uploadChunkWithRetry(authHeader, mountId, uploadId, idx, chunkData, mode) {
  const url = `${BASE_URL}/api/v1/files/upload/chunk?mountId=${mountId}&uploadId=${uploadId}&chunkIndex=${idx}`;
  let attempt = 0;
  while (attempt <= RETRY_MAX) {
    const resp = trackedPut(
      url,
      { file: http.file(chunkData, `chunk-${idx}.part`, "application/octet-stream") },
      { headers: { Authorization: authHeader, "If-Match": "*" }, timeout: "90s" },
      mode
    );
    if (resp.status === 200) return true;
    if (attempt >= RETRY_MAX) return false;
    uploadRetryCounter.add(1);
    let delay = Math.floor(RETRY_BASE_MS * Math.pow(2, attempt) + Math.random() * 100);
    const retryAfter = Number(resp.headers["Retry-After"] || resp.headers["retry-after"] || 0);
    if (!Number.isNaN(retryAfter) && retryAfter > 0) {
      delay = Math.max(delay, retryAfter * 1000);
    }
    sleepMs(delay);
    attempt += 1;
  }
  return false;
}

function completeUpload(authHeader, mountId, uploadId, mode) {
  const resp = trackedPost(
    `${BASE_URL}/api/v1/files/upload/complete`,
    JSON.stringify({ mountId, uploadId }),
    { headers: { Authorization: authHeader, "Content-Type": "application/json" }, timeout: "120s" },
    mode
  );
  return resp.status === 200;
}

function runResumable(authHeader, mountId, name, mode) {
  const init = initUpload(authHeader, mountId, name, mode);
  if (!init.ok) return false;
  const payload = init.resp.json();
  const bytes = new Uint8Array(FILE_BYTES);
  for (let idx = 0; idx < payload.totalChunks; idx += 1) {
    const start = idx * CHUNK_SIZE;
    const end = Math.min(FILE_SIZE, start + CHUNK_SIZE);
    const chunkData = bytes.slice(start, end).buffer;
    const ok = uploadChunkWithRetry(authHeader, mountId, payload.uploadId, idx, chunkData, mode);
    if (!ok) return false;
  }
  const done = completeUpload(authHeader, mountId, payload.uploadId, mode);
  if (done) {
    uploadedBytesCounter.add(FILE_SIZE);
  }
  return done;
}

function runScenario(ctx, mode) {
  const authHeader = ctx.authHeader;
  const mountId = ctx.mountId;
  const started = Date.now();
  const suffix = `${mode}-${__VU}-${__ITER}-${Date.now()}`;
  const ok = runResumable(authHeader, mountId, `k6-${suffix}.bin`, mode);
  uploadFailureRate.add(!ok);
  if (mode === "realistic") {
    realisticFailureRate.add(!ok);
  } else if (mode === "stress") {
    stressFailureRate.add(!ok);
  }
  if (mode === "realistic") {
    realisticDuration.add(Date.now() - started);
    sleep(Number(__ENV.REAL_THINK_SECONDS || 0.3));
  } else {
    stressDuration.add(Date.now() - started);
  }
}

export function realisticScenario(ctx) {
  runScenario(ctx, "realistic");
}

export function stressScenario(ctx) {
  runScenario(ctx, "stress");
}

export function handleSummary(data) {
  const uploadedBytes = data.metrics.upload_success_bytes?.values?.count || 0;
  const durationSeconds = data.state?.testRunDurationMs ? data.state.testRunDurationMs / 1000 : 1;
  const throughputMBps = uploadedBytes / 1024 / 1024 / Math.max(1, durationSeconds);
  const reqCount = data.metrics.upload_req_count?.values?.count || 0;
  const req429Count = data.metrics.upload_req_429_count?.values?.count || 0;
  const req5xxCount = data.metrics.upload_req_5xx_count?.values?.count || 0;
  const reqNon2xxCount = data.metrics.upload_req_non2xx_count?.values?.count || 0;
  const req429Rate = reqCount > 0 ? req429Count / reqCount : null;
  const req5xxRate = reqCount > 0 ? req5xxCount / reqCount : null;
  const reqSuccessEstimate = reqCount > 0 ? 1 - (reqNon2xxCount / reqCount) : null;
  const summary = {
    mode: SCENARIO_MODE,
    realistic_p95_ms: data.metrics.upload_realistic_duration_ms?.values?.["p(95)"] || null,
    realistic_p99_ms: data.metrics.upload_realistic_duration_ms?.values?.["p(99)"] || null,
    stress_p95_ms: data.metrics.upload_stress_duration_ms?.values?.["p(95)"] || null,
    stress_p99_ms: data.metrics.upload_stress_duration_ms?.values?.["p(99)"] || null,
    failure_rate: data.metrics.upload_failure_rate?.values?.rate || null,
    realistic_failure_rate: data.metrics.upload_realistic_failure_rate?.values?.rate || null,
    realistic_429_rate: data.metrics.upload_realistic_429_rate?.values?.rate || null,
    realistic_5xx_rate: data.metrics.upload_realistic_5xx_rate?.values?.rate || null,
    stress_failure_rate: data.metrics.upload_stress_failure_rate?.values?.rate || null,
    stress_429_rate: data.metrics.upload_stress_429_rate?.values?.rate || null,
    stress_5xx_rate: data.metrics.upload_stress_5xx_rate?.values?.rate || null,
    rate_429: data.metrics.upload_429_rate?.values?.rate || null,
    rate_5xx: data.metrics.upload_5xx_rate?.values?.rate || null,
    retry_count: data.metrics.upload_retry_count?.values?.count || 0,
    throughput_mb_per_s: throughputMBps,
    upload_req_count: reqCount,
    upload_req_429_count: req429Count,
    upload_req_5xx_count: req5xxCount,
    upload_req_non2xx_count: reqNon2xxCount,
    upload_req_429_rate: req429Rate,
    upload_req_5xx_rate: req5xxRate,
    upload_req_success_estimate: reqSuccessEstimate,
  };
  return {
    "artifacts/perf/k6-upload-suite-summary.json": JSON.stringify(data, null, 2),
    "artifacts/perf/k6-upload-suite-kpi.json": JSON.stringify(summary, null, 2),
    stdout: JSON.stringify(summary, null, 2),
  };
}
