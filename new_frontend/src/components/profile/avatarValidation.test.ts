import { describe, expect, it } from "vitest";
import { validateAvatarFile } from "./avatarValidation";

describe("validateAvatarFile", () => {
  it("rejects non-image file", () => {
    expect(validateAvatarFile({ type: "text/plain", size: 10 })).toContain("请选择图片文件");
  });

  it("rejects oversized image", () => {
    expect(validateAvatarFile({ type: "image/png", size: 9 * 1024 * 1024 })).toContain("不能超过 8MB");
  });

  it("accepts valid image", () => {
    expect(validateAvatarFile({ type: "image/jpeg", size: 1024 })).toBeNull();
  });
});

