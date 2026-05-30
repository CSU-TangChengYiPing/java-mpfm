import { describe, expect, it } from "vitest";
import { buildRoleNameMap } from "./shares";
import type { ShareMyRoleInfo, ShareRoleTemplate } from "../../controllers/mounts";

describe("buildRoleNameMap", () => {
  it("优先使用角色模板名称", () => {
    const templates: ShareRoleTemplate[] = [
      { id: "r1", templateId: "t1", roleId: "r1", mountId: "m1", name: "访客", permissions: [], builtin: false },
    ];
    const myRoles: ShareMyRoleInfo[] = [
      { mountId: "m1", roleId: "t1", roleName: "visitor-id", roleState: "ACTIVE" },
    ];
    expect(buildRoleNameMap(templates, myRoles).get("t1")).toBe("访客");
  });

  it("链接使用 roleId(UUID) 时也能命中角色名称", () => {
    const templates: ShareRoleTemplate[] = [
      { id: "role-uuid-1", templateId: "template-1", roleId: "role-uuid-1", mountId: "m1", name: "协作者", permissions: [], builtin: false },
    ];
    const myRoles: ShareMyRoleInfo[] = [];
    expect(buildRoleNameMap(templates, myRoles).get("role-uuid-1")).toBe("协作者");
  });

  it("模板缺失时使用已授权角色名称补齐", () => {
    const templates: ShareRoleTemplate[] = [];
    const myRoles: ShareMyRoleInfo[] = [
      { mountId: "m1", roleId: "role-uuid-1", roleName: "协作者", roleState: "ACTIVE" },
    ];
    expect(buildRoleNameMap(templates, myRoles).get("role-uuid-1")).toBe("协作者");
  });
});
