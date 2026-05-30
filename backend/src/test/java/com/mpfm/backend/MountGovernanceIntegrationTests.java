package com.mpfm.backend;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.repository.MountRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
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
class MountGovernanceIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MountRepository mountRepository;

    @Test
    void adminShouldManageOthersMountAndRunStateMachine() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String owner = "mount_owner_" + suffix;
        String mountName = "owner-mount-" + suffix;

        String rootToken = login("root", "Root@123456");
        String adminName = "admin_m_" + suffix;
        String adminPassword = "Adm1nPass!";

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + rootToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"%s",
                                  "displayName":"Mount Admin",
                                  "role":"ADMIN"
                                }
                                """.formatted(adminName, adminPassword)))
                .andExpect(status().isOk());

        String adminToken = login(adminName, adminPassword);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"Passw0rd!",
                                  "displayName":"Mount Owner"
                                }
                                """.formatted(owner)))
                .andExpect(status().isOk());
        String ownerToken = login(owner, "Passw0rd!");

        MvcResult mountCreate = mockMvc.perform(post("/api/v1/mounts")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "protocol":"local",
                                  "enabled":true,
                                  "shared_enabled":false,
                                  "root":"./target/test-local/%s/%s"
                                }
                                """.formatted(mountName, owner, mountName)))
                .andExpect(status().isOk())
                .andReturn();
        String mountId = JsonPath.read(mountCreate.getResponse().getContentAsString(), "$.mountId");

        mockMvc.perform(get("/api/v1/mounts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].mountId", hasItem(mountId)));

        mockMvc.perform(post("/api/v1/mounts/{mountId}/disable", mountId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("If-Match", "\"v1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("disabled"));

        mockMvc.perform(post("/api/v1/mounts/{mountId}/enable", mountId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("If-Match", "\"v1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("enabled"));

        mockMvc.perform(delete("/api/v1/mounts/{mountId}", mountId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("If-Match", "\"v1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        mockMvc.perform(post("/api/v1/mounts/{mountId}/restore", mountId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("If-Match", "\"v1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("disabled"));

        mockMvc.perform(post("/api/v1/mounts/{mountId}/restore", mountId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("If-Match", "\"v1\""))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/mounts/{mountId}", mountId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("If-Match", "\"v1\""))
                .andExpect(status().isOk());

        MountEntity mount = mountRepository.findById(UUID.fromString(mountId)).orElseThrow();
        mount.setDeletedAt(OffsetDateTime.now().minusDays(31));
        mountRepository.save(mount);

        mockMvc.perform(post("/api/v1/mounts/purge-due")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("If-Match", "\"v1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purgedCount").value(1));

        mockMvc.perform(post("/api/v1/mounts/{mountId}/restore", mountId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("If-Match", "\"v1\""))
                .andExpect(status().isConflict());
    }

    @Test
    void mountUpdateShouldValidateAndEnableShouldCheckAvailability() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String owner = "owner_v_" + suffix;
        register(owner);
        String ownerToken = login(owner, "Passw0rd!");

        String mountId = JsonPath.read(mockMvc.perform(post("/api/v1/mounts")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"origin-%s",
                                  "protocol":"local",
                                  "enabled":true,
                                  "shared_enabled":false,
                                  "root":"./target/test-local/%s/origin-%s"
                                }
                                """.formatted(suffix, owner, suffix)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.mountId");

        mockMvc.perform(put("/api/v1/mounts/{mountId}", mountId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"../bad",
                                  "root":"./target/test-local/%s/bad",
                                  "shared_enabled":false
                                }
                                """.formatted(owner)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/mounts/{mountId}/disable", mountId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"v1\""))
                .andExpect(status().isOk());

        MountEntity mount = mountRepository.findById(UUID.fromString(mountId)).orElseThrow();
        Files.deleteIfExists(Path.of(mount.getPhysicalRoot()));

        mockMvc.perform(post("/api/v1/mounts/{mountId}/enable", mountId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"v1\""))
                .andExpect(status().isConflict());
    }

    @Test
    void mountCreateShouldRejectUnsupportedProtocolAndMissingRoot() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String owner = "owner_s_" + suffix;
        register(owner);
        String ownerToken = login(owner, "Passw0rd!");

        mockMvc.perform(post("/api/v1/mounts")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"sftp-%s",
                                  "protocol":"sftp",
                                  "enabled":true,
                                  "shared_enabled":false,
                                  "root":"./target/test-local/%s/sftp-%s"
                                }
                                """.formatted(suffix, owner, suffix)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/mounts")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"local-%s",
                                  "protocol":"local",
                                  "enabled":true,
                                  "shared_enabled":false,
                                  "root":""
                                }
                                """.formatted(suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("enabled"));
    }

    @Test
    void mountUpdateAndEnableDisableShouldRejectNonOwner() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String owner = "owner_p_" + suffix;
        String other = "other_p_" + suffix;
        register(owner);
        register(other);
        String ownerToken = login(owner, "Passw0rd!");
        String otherToken = login(other, "Passw0rd!");

        String mountId = JsonPath.read(mockMvc.perform(post("/api/v1/mounts")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"perm-%s",
                                  "protocol":"local",
                                  "enabled":true,
                                  "shared_enabled":false,
                                  "root":"./target/test-local/%s/perm-%s"
                                }
                                """.formatted(suffix, owner, suffix)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.mountId");

        mockMvc.perform(put("/api/v1/mounts/{mountId}", mountId)
                        .header("Authorization", "Bearer " + otherToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"perm-updated-%s",
                                  "root":"./target/test-local/%s/perm-updated-%s",
                                  "shared_enabled":true
                                }
                                """.formatted(suffix, owner, suffix)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        mockMvc.perform(post("/api/v1/mounts/{mountId}/disable", mountId)
                        .header("Authorization", "Bearer " + otherToken)
                        .header("If-Match", "\"v1\""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    @Test
    void deleteLocalMountShouldCleanupLocalDirtyData() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String owner = "owner_d_" + suffix;
        register(owner);
        String ownerToken = login(owner, "Passw0rd!");

        String mountId = JsonPath.read(mockMvc.perform(post("/api/v1/mounts")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"cleanup-%s",
                                  "protocol":"local",
                                  "enabled":true,
                                  "shared_enabled":false
                                }
                                """.formatted(suffix)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.mountId");

        MountEntity mount = mountRepository.findById(UUID.fromString(mountId)).orElseThrow();
        Path dirtyFile = Path.of(mount.getPhysicalRoot()).resolve("dirty.txt");
        Files.createDirectories(dirtyFile.getParent());
        Files.writeString(dirtyFile, "dirty-data", StandardCharsets.UTF_8);

        mockMvc.perform(delete("/api/v1/mounts/{mountId}", mountId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"v1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        org.assertj.core.api.Assertions.assertThat(Files.exists(Path.of(mount.getPhysicalRoot()))).isFalse();
    }

    @Test
    void deleteLocalMountShouldSkipCleanupWhenRootOutOfManagedBasePath() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String owner = "owner_o_" + suffix;
        register(owner);
        String ownerToken = login(owner, "Passw0rd!");

        String mountId = JsonPath.read(mockMvc.perform(post("/api/v1/mounts")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"outside-%s",
                                  "protocol":"local",
                                  "enabled":true,
                                  "shared_enabled":false
                                }
                                """.formatted(suffix)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.mountId");

        MountEntity mount = mountRepository.findById(UUID.fromString(mountId)).orElseThrow();
        Path outsideRoot = Path.of(System.getProperty("java.io.tmpdir"), "mpfm-outside-" + suffix);
        Files.createDirectories(outsideRoot);
        Files.writeString(outsideRoot.resolve("legacy.txt"), "legacy-data", StandardCharsets.UTF_8);
        mount.setPhysicalRoot(outsideRoot.toString());
        mountRepository.save(mount);

        mockMvc.perform(delete("/api/v1/mounts/{mountId}", mountId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("If-Match", "\"v1\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        MountEntity updated = mountRepository.findById(UUID.fromString(mountId)).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getState()).isEqualTo("soft_deleted");
        org.assertj.core.api.Assertions.assertThat(Files.exists(outsideRoot.resolve("legacy.txt"))).isTrue();
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"Passw0rd!",
                                  "displayName":"User"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token.accessToken");
    }
}

