import { describe, expect, it } from "vitest";
import { mapShareErrorPresentation, parseErrorCode, resolveShareError } from "./errorPresentation";

describe("share error presentation", () => {
  it("should map unified error codes to fixed display level", () => {
    expect(mapShareErrorPresentation("VALIDATION_ERROR")).toBe("field");
    expect(mapShareErrorPresentation("PERMISSION_DENIED")).toBe("form");
    expect(mapShareErrorPresentation("LINK_EXPIRED")).toBe("form");
    expect(mapShareErrorPresentation("LINK_REVOKED")).toBe("form");
    expect(mapShareErrorPresentation("LINK_EXHAUSTED")).toBe("form");
    expect(mapShareErrorPresentation("ROLE_EXPIRED")).toBe("form");
    expect(mapShareErrorPresentation("ROLE_DISABLED")).toBe("form");
    expect(mapShareErrorPresentation("INTERNAL_ERROR")).toBe("toast");
  });

  it("should parse error code from unified backend message", () => {
    expect(parseErrorCode(new Error("[ROLE_DISABLED] 角色已停用"))).toBe("ROLE_DISABLED");
    expect(parseErrorCode(new Error("plain message"))).toBe("UNKNOWN");
  });

  it("should resolve level and fallback message together", () => {
    expect(resolveShareError(new Error("[VALIDATION_ERROR] 字段错误"), "fallback")).toEqual({
      level: "field",
      message: "[VALIDATION_ERROR] 字段错误",
    });
    expect(resolveShareError("bad", "fallback")).toEqual({
      level: "toast",
      message: "fallback",
    });
  });
});
