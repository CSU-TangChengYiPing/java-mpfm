import { describe, expect, it } from "vitest";
import { pickInitialMountId } from "./shares";
import type { MountInfo } from "../../controllers/mounts";

function mount(id: string, owner: string, shared = true): MountInfo {
  return {
    id,
    name: id,
    protocol: "local",
    root: "/",
    enabled: true,
    shared_enabled: shared,
    owner_user: owner,
  };
}

describe("pickInitialMountId", () => {
  it("优先返回请求参数命中的可管理挂载", () => {
    const mounts = [mount("m1", "u1"), mount("m2", "u1")];
    expect(pickInitialMountId(mounts, "m2", "u1", "")).toBe("m2");
  });

  it("请求参数不可管理时回退到第一个可管理挂载", () => {
    const mounts = [mount("m1", "u1"), mount("m2", "u2")];
    expect(pickInitialMountId(mounts, "m2", "u1", "")).toBe("m1");
  });

  it("无可管理挂载时回退到我的角色挂载", () => {
    const mounts = [mount("m1", "u2"), mount("m2", "u3")];
    expect(pickInitialMountId(mounts, "", "u1", "shared-m1")).toBe("shared-m1");
  });
});

