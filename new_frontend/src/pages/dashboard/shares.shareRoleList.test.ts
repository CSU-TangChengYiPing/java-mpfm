import { describe, expect, it } from "vitest";
import { buildShareLinkRoleList } from "./shares";
import type { ShareRoleTemplate } from "../../controllers/mounts";

describe("buildShareLinkRoleList", () => {
  it("创建共享链接时应使用 roleId 而不是 templateId", () => {
    const templates: ShareRoleTemplate[] = [
      {
        id: "template-owner",
        templateId: "template-owner",
        roleId: "role-owner",
        mountId: "m1",
        name: "owner",
        permissions: [],
        builtin: true,
      },
      {
        id: "template-reader",
        templateId: "template-reader",
        roleId: "role-reader",
        mountId: "m1",
        name: "31312321",
        permissions: [],
        builtin: false,
      },
    ];

    expect(buildShareLinkRoleList(templates)).toEqual(["role-reader"]);
  });
});
