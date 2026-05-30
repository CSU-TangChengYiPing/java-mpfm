import { afterEach, describe, expect, it, vi } from "vitest";
import MountsController from "./mounts";

function mockJsonResponse(body: unknown, ok = true, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
    statusText: ok ? "OK" : "Bad Request",
  });
}

describe("MountsController", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("list supports object and array payload with field mapping", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(mockJsonResponse({ mounts: [{ mountId: "m1", type: "local", virtualPath: "/a", name: "A", state: "enabled" }] }))
      .mockResolvedValueOnce(mockJsonResponse([{ id: "m2", protocol: "local", root: "/b", name: "B", enabled: false }]));

    const first = await MountsController.list();
    const second = await MountsController.list();

    expect(first[0]).toMatchObject({ id: "m1", protocol: "local", root: "/a", enabled: true });
    expect(second[0]).toMatchObject({ id: "m2", protocol: "local", root: "/b", enabled: false });
    expect(fetchMock).toHaveBeenNthCalledWith(1, "/api/v1/mounts", undefined);
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/v1/mounts", undefined);
  });

  it("update and action use RESTful mountId paths", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(mockJsonResponse({}));

    await MountsController.update("mount-x", { name: "new-name" });
    await MountsController.action("enable", "mount-x");
    await MountsController.action("disable", "mount-y");
    await MountsController.deleteMount("mount-z");

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      "/api/v1/mounts/mount-x",
      expect.objectContaining({ method: "PUT" }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "/api/v1/mounts/mount-x/enable",
      expect.objectContaining({ method: "POST" }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      "/api/v1/mounts/mount-y/disable",
      expect.objectContaining({ method: "POST" }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      4,
      "/api/v1/mounts/mount-z",
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("module3 role APIs should align with REST contract", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(mockJsonResponse({ id: "r1", name: "viewer" }))
      .mockResolvedValueOnce(mockJsonResponse({ items: [{ id: "r1", name: "viewer" }] }))
      .mockResolvedValueOnce(mockJsonResponse({ id: "r1", name: "editor" }))
      .mockResolvedValueOnce(mockJsonResponse({}))
      .mockResolvedValueOnce(mockJsonResponse({}))
      .mockResolvedValueOnce(mockJsonResponse({}));

    await MountsController.createRole({ mountId: "m1", name: "viewer" });
    await MountsController.listRoles("m1");
    await MountsController.updateRole("r1", { name: "editor" });
    await MountsController.disableRole("r1");
    await MountsController.deleteRole("r1");
    await MountsController.updatePathPolicies("r1", [{ pathPattern: "./shared/**", canVisible: true, canRead: true, canWrite: false }]);

    expect(fetchMock).toHaveBeenNthCalledWith(1, "/api/v1/mounts/m1/share-roles", expect.objectContaining({ method: "POST" }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/v1/mounts/m1/share-roles", undefined);
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/api/v1/share-roles/r1", expect.objectContaining({ method: "PUT" }));
    expect(fetchMock).toHaveBeenNthCalledWith(4, "/api/v1/share-roles/r1/disable", expect.objectContaining({ method: "POST" }));
    expect(fetchMock).toHaveBeenNthCalledWith(5, "/api/v1/share-roles/r1", expect.objectContaining({ method: "DELETE" }));
    expect(fetchMock).toHaveBeenNthCalledWith(6, "/api/v1/share-roles/r1/path-policies", expect.objectContaining({ method: "PUT" }));
  });

  it("module3 link APIs should align with REST contract", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(mockJsonResponse({ id: "l1", mountId: "m1", roleId: "r1" }))
      .mockResolvedValueOnce(mockJsonResponse({ items: [{ id: "l1", mount_id: "m1", role: "r1" }] }))
      .mockResolvedValueOnce(mockJsonResponse({ id: "l1", mountId: "m1", roleId: "r1" }))
      .mockResolvedValueOnce(mockJsonResponse({ id: "l1", mountId: "m1", roleId: "r1" }))
      .mockResolvedValueOnce(mockJsonResponse({}))
      .mockResolvedValueOnce(mockJsonResponse({}))
      .mockResolvedValueOnce(mockJsonResponse({ mountId: "m1", roleId: "r1" }))
      .mockResolvedValueOnce(mockJsonResponse({}))
      .mockResolvedValueOnce(mockJsonResponse({ permissions: ["visible", "read"] }));

    await MountsController.createLink({ mountId: "m1", roleId: "r1" });
    const list = await MountsController.listLinks();
    await MountsController.getLink("l1");
    await MountsController.updateLink("l1", { maxUses: 10 });
    await MountsController.revokeLink("l1");
    await MountsController.deleteLink("l1");
    await MountsController.resolveLink("token-1");
    await MountsController.switchRole("m1", "r1");
    await MountsController.effectivePermission("m1", "/docs");

    expect(list[0]).toMatchObject({ mountId: "m1", roleId: "r1" });
    expect(fetchMock).toHaveBeenNthCalledWith(1, "/api/v1/mounts/m1/share-links", expect.objectContaining({ method: "POST" }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/v1/share-links?page=1&pageSize=20", undefined);
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/api/v1/share-links/l1", undefined);
    expect(fetchMock).toHaveBeenNthCalledWith(4, "/api/v1/share-links/l1", expect.objectContaining({ method: "PUT" }));
    expect(fetchMock).toHaveBeenNthCalledWith(5, "/api/v1/share-links/l1/revoke", expect.objectContaining({ method: "POST" }));
    expect(fetchMock).toHaveBeenNthCalledWith(6, "/api/v1/share-links/l1", expect.objectContaining({ method: "DELETE" }));
    expect(fetchMock).toHaveBeenNthCalledWith(7, "/api/v1/share-links/resolve", expect.objectContaining({ method: "POST" }));
    expect(fetchMock).toHaveBeenNthCalledWith(8, "/api/v1/shared-mounts/m1/switch-role", expect.objectContaining({ method: "POST" }));
    expect(fetchMock).toHaveBeenNthCalledWith(9, "/api/v1/permissions/effective?mountId=m1&path=%2Fdocs", undefined);
  });

  it("module3 list response shape should be normalized for roles links and grants", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(mockJsonResponse([{ id: "r1", name: "viewer" }]))
      .mockResolvedValueOnce(mockJsonResponse({ items: [{ id: "l1", mount_id: "m1", role: "visitor" }] }))
      .mockResolvedValueOnce(mockJsonResponse({ items: [{ id: "g1", mount_id: "m1", role: "visitor" }] }));

    const roles = await MountsController.listRoles("m1");
    const links = await MountsController.listLinks(1, 10);
    const grants = await MountsController.listShareGrants("m1");

    expect(roles).toHaveLength(1);
    expect(links).toHaveLength(1);
    expect(grants).toHaveLength(1);
    expect(links[0]).toMatchObject({ mountId: "m1", roleId: "visitor" });
  });

  it("module3 grants and audits should try restful path then fallback to legacy path", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(mockJsonResponse({ items: [{ id: "a1", mount_id: "m1", action: "x", actor: "u", result: "ok", occurred_at: "t" }] }))
      .mockResolvedValueOnce(mockJsonResponse({ items: [{ grantId: "g1", mount_id: "m1", role: "visitor", pathScopes: ["/"], permissions: ["read"] }] }));

    const audits = await MountsController.listShareAudits("m1");
    const grants = await MountsController.listShareGrants("m1");

    expect(audits[0]).toMatchObject({ mountId: "m1" });
    expect(grants[0]).toMatchObject({ id: "g1" });
    expect(fetchMock).toHaveBeenNthCalledWith(1, "/api/v1/mounts/m1/share-audits", undefined);
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/v1/mounts/m1/share-grants", undefined);
    expect(grants[0]).toMatchObject({ id: "g1", mountId: "m1", path_scopes: ["/"] });
  });

  it("create returns permission failure message from unified error body", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      mockJsonResponse({ error: { code: "PERMISSION_DENIED", message: "权限不足" } }, false, 403),
    );

    await expect(
      MountsController.create({ name: "demo", protocol: "local", enabled: true }),
    ).rejects.toThrow("[PERMISSION_DENIED] 权限不足");
  });

  it("update returns validation failure message from unified error body", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      mockJsonResponse({ error: { code: "VALIDATION_ERROR", message: "root 必填" } }, false, 400),
    );

    await expect(MountsController.update("mount-x", { name: "demo" })).rejects.toThrow(
      "[VALIDATION_ERROR] root 必填",
    );
  });

  it("module3 createLink should expose permission and validation errors with unified codes", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(mockJsonResponse({ error: { code: "PERMISSION_DENIED", message: "权限不足" } }, false, 403))
      .mockResolvedValueOnce(mockJsonResponse({ error: { code: "VALIDATION_ERROR", message: "roleId 必填" } }, false, 400));

    await expect(MountsController.createLink({ mountId: "m1", roleId: "r1" })).rejects.toThrow("[PERMISSION_DENIED] 权限不足");
    await expect(MountsController.createLink({ mountId: "m1", roleId: "" })).rejects.toThrow("[VALIDATION_ERROR] roleId 必填");
  });
});
