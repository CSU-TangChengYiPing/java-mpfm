import { describe, expect, it } from "vitest";
import {
  getMoveBrowseVirtualMountRootPath,
  isMoveBrowseWithinVirtualMount,
  isMoveBrowseVirtualPrefixPath,
  normalizeMoveBrowsePath,
  resolveMoveBrowseChildPath,
  resolveMoveBrowseParentPath,
  splitMoveBrowseTrail,
} from "./move_modal.model";

describe("normalizeMoveBrowsePath", () => {
  it("keeps root and dot stable", () => {
    expect(normalizeMoveBrowsePath(".")).toBe(".");
    expect(normalizeMoveBrowsePath("/")).toBe("/");
  });
});

describe("virtual prefix guard", () => {
  it("only matches leading virtual prefix", () => {
    expect(isMoveBrowseVirtualPrefixPath("/./personal/demo/docs")).toBe(true);
    expect(isMoveBrowseVirtualPrefixPath("/xxx/xxx/personal/demo")).toBe(false);
  });

  it("resolves mount root only for leading virtual prefix", () => {
    expect(getMoveBrowseVirtualMountRootPath("/./personal/demo/docs")).toBe("/./personal/demo");
    expect(getMoveBrowseVirtualMountRootPath("/xxx/xxx/personal/demo")).toBeNull();
  });

  it("blocks escaping the current virtual mount", () => {
    expect(isMoveBrowseWithinVirtualMount("/./personal/demo/docs", "/./personal/demo/docs")).toBe(true);
    expect(isMoveBrowseWithinVirtualMount("/./personal/demo", "/./personal/demo/docs")).toBe(true);
    expect(isMoveBrowseWithinVirtualMount("/./personal", "/./personal/demo/docs")).toBe(false);
    expect(isMoveBrowseWithinVirtualMount("/xxx/xxx/personal", "/./personal/demo/docs")).toBe(false);
  });
});

describe("resolveMoveBrowseParentPath", () => {
  it("keeps root parents stable", () => {
    expect(resolveMoveBrowseParentPath(".")).toBe(".");
    expect(resolveMoveBrowseParentPath("/personal/demo")).toBe("/personal/demo");
  });

  it("keeps virtual mount roots stable", () => {
    expect(resolveMoveBrowseParentPath("/./personal/demo")).toBe("/./personal/demo");
    expect(resolveMoveBrowseParentPath("/./personal/demo/docs")).toBe("/./personal/demo");
    expect(resolveMoveBrowseParentPath("/./personal/4421412/231231321")).toBe("/./personal/4421412");
  });

  it("keeps normal mount roots stable", () => {
    expect(resolveMoveBrowseParentPath("/personal/4421412")).toBe("/personal/4421412");
    expect(resolveMoveBrowseParentPath("/personal/4421412/231231321")).toBe("/personal/4421412");
  });
});

describe("resolveMoveBrowseChildPath", () => {
  it("joins children into current browse path", () => {
    expect(resolveMoveBrowseChildPath("/personal/demo", "docs")).toBe("/personal/demo/docs");
  });
});

describe("splitMoveBrowseTrail", () => {
  it("splits browse path into trail", () => {
    expect(splitMoveBrowseTrail("/personal/demo/docs")).toEqual(["personal", "demo", "docs"]);
  });
});
