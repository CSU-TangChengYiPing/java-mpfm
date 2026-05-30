import { describe, expect, it } from "vitest";
import { parseLegacyLogLine, parseLogLevel, parseStructuredLog, resolveContinuationAction } from "./debugLogParser";

describe("debugLogParser", () => {
  it("should parse spring access line into structured fields", () => {
    const line = "2026-05-29T06:14:01.303Z INFO  163564 --- [mpfm-backend] [http-nio-8080-exec-9] [7ea28f98-c895-4f36-a7a5-9a8b11d0b908,7ea28f98-c895-4f36-a7a5-9a8b11d0b908] ACCESS : request method=GET path=/api/v1/debug/logs/stream status=401 costMs=12 query=tailLines=300 body=";
    const parsed = parseLegacyLogLine(line);
    expect(parsed.shortTime).toBe("06:14:01.303");
    expect(parsed.level).toBe("info");
    expect(parsed.levelShort).toBe("INF");
    expect(parsed.category).toBe("ACCESS");
    expect(parsed.method).toBe("GET");
    expect(parsed.path).toBe("/api/v1/debug/logs/stream");
    expect(parsed.statusText).toBe("401");
    expect(parsed.costText).toBe("12");
    expect(parsed.traceId).toBe("7ea28f98-c895-4f36-a7a5-9a8b11d0b908");
  });

  it("should prefer explicit SSE fields when provided", () => {
    const parsed = parseStructuredLog({
      time: "2026-05-29T06:14:01.303Z",
      level: "warn",
      category: "SECURITY",
      message: "SECURITY : deny method=POST path=/api/v1/admin status=403 costMs=7",
      path: "/api/v1/admin",
      status: 403,
      costMs: 7,
      traceId: "t-1",
      requestId: "r-1",
    });
    expect(parsed.shortTime).toBe("06:14:01.303");
    expect(parsed.level).toBe("warn");
    expect(parsed.levelShort).toBe("WRN");
    expect(parsed.category).toBe("SECURITY");
    expect(parsed.path).toBe("/api/v1/admin");
    expect(parsed.statusText).toBe("403");
    expect(parsed.costText).toBe("7");
    expect(parsed.traceId).toBe("t-1");
    expect(parsed.requestId).toBe("r-1");
  });

  it("should keep unknown when no level tokens", () => {
    expect(parseLogLevel("plain message")).toBe("unknown");
  });

  it("should start standalone sql block when sql fragment has no previous line", () => {
    const parsed = parseLegacyLogLine("    select");
    expect(resolveContinuationAction(parsed, false)).toBe("startSqlBlock");
  });

  it("should append sql fragment when previous line exists", () => {
    const parsed = parseLegacyLogLine("    from");
    expect(resolveContinuationAction(parsed, true)).toBe("appendPrevious");
  });
});
