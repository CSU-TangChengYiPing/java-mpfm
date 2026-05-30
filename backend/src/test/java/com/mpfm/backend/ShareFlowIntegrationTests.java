package com.mpfm.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShareFlowIntegrationTests {
    @Autowired MockMvc mockMvc;

    @Test
    void shareRoleLinkResolveSwitchShouldWork() throws Exception {
        String owner = "owner_" + UUID.randomUUID().toString().substring(0, 6);
        String guest = "guest_" + UUID.randomUUID().toString().substring(0, 6);

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Passw0rd!\",\"displayName\":\"Owner\"}".formatted(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Passw0rd!\",\"displayName\":\"Guest\"}".formatted(guest)))
                .andExpect(status().isOk());

        String ownerToken = JsonPath.read(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Passw0rd!\"}".formatted(owner))).andReturn().getResponse().getContentAsString(), "$.token.accessToken");
        String guestToken = JsonPath.read(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Passw0rd!\"}".formatted(guest))).andReturn().getResponse().getContentAsString(), "$.token.accessToken");

        String mountId = JsonPath.read(mockMvc.perform(post("/api/v1/mounts").header("Authorization", "Bearer " + ownerToken).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"share-mount",
                                  "protocol":"local",
                                  "enabled":true,
                                  "shared_enabled":false,
                                  "root":"./target/test-local/default/share-mount"
                                }
                                """)).andReturn().getResponse().getContentAsString(), "$.mountId");

        String roleId = JsonPath.read(mockMvc.perform(post("/api/v1/mounts/" + mountId + "/share-roles")
                        .header("Authorization", "Bearer " + ownerToken).header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"reader\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.roleId");

        mockMvc.perform(put("/api/v1/share-roles/" + roleId + "/path-policies")
                        .header("Authorization", "Bearer " + ownerToken).header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"pathPattern\":\"./personal\",\"canVisible\":true,\"canRead\":true,\"canWrite\":false}]}"))
                .andExpect(status().isOk());

        String linkToken = JsonPath.read(mockMvc.perform(post("/api/v1/mounts/" + mountId + "/share-links")
                        .header("Authorization", "Bearer " + ownerToken).header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleId\":\"%s\",\"maxUses\":10}".formatted(roleId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post("/api/v1/share-links/resolve").header("Authorization", "Bearer " + guestToken)
                        .header("If-Match", "\"v1\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(linkToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/shared-mounts/" + mountId + "/switch-role").header("Authorization", "Bearer " + guestToken)
                        .header("If-Match", "\"v1\"").contentType(MediaType.APPLICATION_JSON).content("{\"roleId\":\"%s\"}".formatted(roleId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/permissions/effective").header("Authorization", "Bearer " + guestToken)
                        .param("mountId", mountId).param("path", "./personal/docs/readme.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRead").value(true));
    }

    @Test
    void shareLinkLifecycleConstraintsShouldWork() throws Exception {
        String owner = "owner_" + UUID.randomUUID().toString().substring(0, 6);
        String guest = "guest_" + UUID.randomUUID().toString().substring(0, 6);
        register(owner, "Owner");
        register(guest, "Guest");
        String ownerToken = login(owner);
        String guestToken = login(guest);

        String mountId = createMount(ownerToken, "share-link-lifecycle");
        String roleId = createRole(ownerToken, mountId, "guest-role");

        String linkId = JsonPath.read(mockMvc.perform(post("/api/v1/mounts/" + mountId + "/share-links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"maxUses\":1}".formatted(roleId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.linkId");
        String linkToken = JsonPath.read(mockMvc.perform(get("/api/v1/share-links/" + linkId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post("/api/v1/share-links/resolve")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(linkToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/share-links/resolve")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(linkToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("LINK_EXHAUSTED"));

        String link2Id = JsonPath.read(mockMvc.perform(post("/api/v1/mounts/" + mountId + "/share-links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"maxUses\":10}".formatted(roleId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.linkId");
        String link2Token = JsonPath.read(mockMvc.perform(get("/api/v1/share-links/" + link2Id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post("/api/v1/share-links/" + link2Id + "/revoke")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("revoked"));

        mockMvc.perform(post("/api/v1/share-links/resolve")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(link2Token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("LINK_REVOKED"));

        String future = OffsetDateTime.now().plusHours(2).toString();
        String futureLinkId = JsonPath.read(mockMvc.perform(post("/api/v1/mounts/" + mountId + "/share-links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"maxUses\":10,\"roleStartAt\":\"%s\"}".formatted(roleId, future)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.linkId");
        String futureToken = JsonPath.read(mockMvc.perform(get("/api/v1/share-links/" + futureLinkId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post("/api/v1/share-links/resolve")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(futureToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROLE_EXPIRED"));

        mockMvc.perform(delete("/api/v1/share-links/" + futureLinkId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"v1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("delete_share_link"));

        mockMvc.perform(get("/api/v1/share-links/" + futureLinkId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void builtInRolesAndSharedFilePermissionShouldWork() throws Exception {
        String owner = "owner_" + UUID.randomUUID().toString().substring(0, 6);
        String guest = "guest_" + UUID.randomUUID().toString().substring(0, 6);
        register(owner, "Owner");
        register(guest, "Guest");
        String ownerToken = login(owner);
        String guestToken = login(guest);

        String mountName = "share-perm-" + UUID.randomUUID().toString().substring(0, 4);
        String mountId = createMount(ownerToken, mountName);
        String ownerFilePath = "/personal/%s/docs/readme.txt".formatted(mountId);
        String sharedFilePath = "/shared/%s/docs/readme.txt".formatted(mountId);

        mockMvc.perform(put("/api/v1/files/content")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"virtualPath\":\"%s\",\"content\":\"hello-shared\"}".formatted(ownerFilePath)))
                .andExpect(status().isOk());

        MvcResult rolesResult = mockMvc.perform(get("/api/v1/mounts/" + mountId + "/share-roles")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String rolesJson = rolesResult.getResponse().getContentAsString();
        String visitorRoleId = ((List<String>) JsonPath.read(rolesJson, "$[?(@.name=='visitor')].roleId")).getFirst();
        String collaboratorRoleId = ((List<String>) JsonPath.read(rolesJson, "$[?(@.name=='collaborator')].roleId")).getFirst();
        String ownerRoleId = ((List<String>) JsonPath.read(rolesJson, "$[?(@.name=='owner')].roleId")).getFirst();

        mockMvc.perform(post("/api/v1/mounts/" + mountId + "/share-links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\"}".formatted(ownerRoleId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OWNER_IMMUTABLE"));

        String visitorToken = JsonPath.read(mockMvc.perform(post("/api/v1/mounts/" + mountId + "/share-links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"maxUses\":10}".formatted(visitorRoleId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");
        String collaboratorToken = JsonPath.read(mockMvc.perform(post("/api/v1/mounts/" + mountId + "/share-links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"maxUses\":10}".formatted(collaboratorRoleId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post("/api/v1/share-links/resolve")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(visitorToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/share-links/resolve")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(collaboratorToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/shared-mounts/" + mountId + "/switch-role")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\"}".formatted(visitorRoleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(visitorRoleId));

        mockMvc.perform(get("/api/v1/permissions/effective")
                        .header("Authorization", "Bearer " + guestToken)
                        .param("mountId", mountId)
                        .param("path", sharedFilePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canVisible").value(true))
                .andExpect(jsonPath("$.canRead").value(true));

        mockMvc.perform(get("/api/v1/files/content")
                        .header("Authorization", "Bearer " + guestToken)
                        .param("virtualPath", sharedFilePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("hello-shared"));

        mockMvc.perform(put("/api/v1/files/content")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"virtualPath\":\"%s\",\"content\":\"deny\"}".formatted(sharedFilePath)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/shared-mounts/" + mountId + "/switch-role")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\"}".formatted(collaboratorRoleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(collaboratorRoleId));

        mockMvc.perform(put("/api/v1/files/content")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"virtualPath\":\"%s\",\"content\":\"updated\"}".formatted(sharedFilePath)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files/content")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("virtualPath", ownerFilePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("updated"));
    }

    @Test
    void shareLinkV5ShouldGrantRoleUnionAndIgnoreLinkStateAfterResolve() throws Exception {
        String owner = "owner_" + UUID.randomUUID().toString().substring(0, 6);
        String guest = "guest_" + UUID.randomUUID().toString().substring(0, 6);
        register(owner, "Owner");
        register(guest, "Guest");
        String ownerToken = login(owner);
        String guestToken = login(guest);

        String mountId = createMount(ownerToken, "share-v5-" + UUID.randomUUID().toString().substring(0, 4));
        String roleRead = createRole(ownerToken, mountId, "reader_v5");
        String roleWrite = createRole(ownerToken, mountId, "writer_v5");

        mockMvc.perform(put("/api/v1/share-roles/" + roleRead + "/path-policies")
                        .header("Authorization", "Bearer " + ownerToken).header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"pathPattern\":\"./personal\",\"canVisible\":true,\"canRead\":true,\"canWrite\":false}]}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/share-roles/" + roleWrite + "/path-policies")
                        .header("Authorization", "Bearer " + ownerToken).header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"pathPattern\":\"./personal\",\"canVisible\":true,\"canRead\":true,\"canWrite\":true}]}"))
                .andExpect(status().isOk());

        MvcResult linkReadCreate = mockMvc.perform(post("/api/v5/mounts/" + mountId + "/share-links")
                        .header("Authorization", "Bearer " + ownerToken).header("If-Match", "\"m-7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"maxUses\":10}".formatted(roleRead)))
                .andExpect(status().isOk())
                .andReturn();
        String linkReadJson = linkReadCreate.getResponse().getContentAsString();
        String linkReadId = JsonPath.read(linkReadJson, "$.linkId");
        String linkReadToken = JsonPath.read(linkReadJson, "$.token");
        String linkWriteToken = JsonPath.read(mockMvc.perform(post("/api/v5/mounts/" + mountId + "/share-links")
                        .header("Authorization", "Bearer " + ownerToken).header("If-Match", "\"m-7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"maxUses\":10}".formatted(roleWrite)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post("/api/v5/share-links/resolve")
                        .header("Authorization", "Bearer " + guestToken).header("If-Match", "\"m-7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(linkReadToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v5/share-links/resolve")
                        .header("Authorization", "Bearer " + guestToken).header("If-Match", "\"m-7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(linkWriteToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v5/mounts/" + mountId + "/granted-roles")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].granteeUsername").value(guest))
                .andExpect(jsonPath("$[0].mountId").value(mountId));

        mockMvc.perform(post("/api/v5/share-links/" + linkReadId + "/revoke")
                        .header("Authorization", "Bearer " + ownerToken).header("If-Match", "\"m-7\""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v5/permissions/effective")
                        .header("Authorization", "Bearer " + guestToken)
                        .param("mountId", mountId)
                        .param("path", "./personal/docs/readme.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRead").value(true))
                .andExpect(jsonPath("$.canWrite").value(true));
    }

    @Test
    void templatePreviewBatchShouldApplyParentChainAndInSqlPath() throws Exception {
        String owner = "owner_" + UUID.randomUUID().toString().substring(0, 6);
        register(owner, "Owner");
        String ownerToken = login(owner);
        String mountId = createMount(ownerToken, "tmpl-sql-" + UUID.randomUUID().toString().substring(0, 4));

        MvcResult templateCreate = mockMvc.perform(post("/api/v5/mounts/" + mountId + "/role-templates")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"m-7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"qa_template_%s",
                                  "defaultVisible":true,
                                  "defaultRead":true,
                                  "defaultWrite":false
                                }
                                """.formatted(UUID.randomUUID().toString().substring(0, 4))))
                .andExpect(status().isOk())
                .andReturn();
        String templateId = JsonPath.read(templateCreate.getResponse().getContentAsString(), "$.templateId");

        mockMvc.perform(put("/api/v5/role-templates/" + templateId + "/privileges")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"m-7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetPath":"/folder",
                                  "allowVisible":false,
                                  "allowRead":false,
                                  "allowWrite":false
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v5/role-templates/" + templateId + "/privileges")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"m-7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetPath":"/folder/file.txt",
                                  "allowVisible":true,
                                  "allowRead":true,
                                  "allowWrite":true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v5/mounts/" + mountId + "/permissions/template-preview-batch")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId":"%s",
                                  "paths":["/folder/file.txt"]
                                }
                                """.formatted(templateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['/folder/file.txt'].canVisible").value(false))
                .andExpect(jsonPath("$['/folder/file.txt'].canRead").value(false))
                .andExpect(jsonPath("$['/folder/file.txt'].canWrite").value(false));
    }

    @Test
    void effectiveShouldFallbackToLegacyWhenRoleHasNoTemplate() throws Exception {
        String owner = "owner_" + UUID.randomUUID().toString().substring(0, 6);
        String guest = "guest_" + UUID.randomUUID().toString().substring(0, 6);
        register(owner, "Owner");
        register(guest, "Guest");
        String ownerToken = login(owner);
        String guestToken = login(guest);

        String mountId = createMount(ownerToken, "legacy-fallback-" + UUID.randomUUID().toString().substring(0, 4));
        String legacyRoleId = createRole(ownerToken, mountId, "legacy_only_" + UUID.randomUUID().toString().substring(0, 4));
        mockMvc.perform(put("/api/v1/share-roles/" + legacyRoleId + "/path-policies")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[
                                    {
                                      "pathPattern":"./personal/docs",
                                      "canVisible":true,
                                      "canRead":true,
                                      "canWrite":false
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        String linkToken = JsonPath.read(mockMvc.perform(post("/api/v5/mounts/" + mountId + "/share-links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"m-7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\",\"maxUses\":10}".formatted(legacyRoleId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");
        mockMvc.perform(post("/api/v5/share-links/resolve")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("If-Match", "\"m-7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(linkToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v5/permissions/effective")
                        .header("Authorization", "Bearer " + guestToken)
                        .param("mountId", mountId)
                        .param("path", "./personal/docs/readme.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canVisible").value(true))
                .andExpect(jsonPath("$.canRead").value(true))
                .andExpect(jsonPath("$.canWrite").value(false));
    }

    private void register(String username, String displayName) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Passw0rd!\",\"displayName\":\"%s\"}".formatted(username, displayName)))
                .andExpect(status().isOk());
    }

    private String login(String username) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Passw0rd!\"}".formatted(username)))
                .andReturn().getResponse().getContentAsString(), "$.token.accessToken");
    }

    private String createMount(String token, String name) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/mounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "protocol":"local",
                                  "enabled":true,
                                  "shared_enabled":false,
                                  "root":"./target/test-local/default/%s"
                                }
                                """.formatted(name, name)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.mountId");
    }

    private String createRole(String token, String mountId, String roleName) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/mounts/" + mountId + "/share-roles")
                        .header("Authorization", "Bearer " + token)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(roleName)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.roleId");
    }

}


