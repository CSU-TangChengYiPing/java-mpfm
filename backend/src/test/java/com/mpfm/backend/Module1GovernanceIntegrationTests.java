package com.mpfm.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class Module1GovernanceIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void userProfileAndCredentialLifecycleShouldWork() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "u_" + suffix;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"Passw0rd!",
                                  "displayName":"User %s"
                                }
                                """.formatted(username, suffix)))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"Passw0rd!"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();

        String access = JsonPath.read(login.getResponse().getContentAsString(), "$.token.accessToken");

        MvcResult captcha = mockMvc.perform(post("/api/v1/auth/captcha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scene":"change_credential"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String captchaId = JsonPath.read(captcha.getResponse().getContentAsString(), "$.captchaId");

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .header("Authorization", "Bearer " + access)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName":"New Name",
                                  "email":"u@example.com",
                                  "phone":"13800000000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Name"));

        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .header("Authorization", "Bearer " + access)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "language":"en-US",
                                  "fileViewMode":"grid"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en-US"));

        mockMvc.perform(post("/api/v1/users/me/change-credential")
                        .header("Authorization", "Bearer " + access)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "oldCredential":"Passw0rd!",
                                  "newCredential":"N3wPassw0rd!",
                                  "captchaId":"%s",
                                  "captchaAnswer":"123456"
                                }
                                """.formatted(captchaId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"N3wPassw0rd!"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk());
    }

    @Test
    void rootShouldManageUsersAndAdminCannotCreateAdmin() throws Exception {
        MvcResult rootLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"root",
                                  "password":"Root@123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String rootToken = JsonPath.read(rootLogin.getResponse().getContentAsString(), "$.token.accessToken");

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        MvcResult createAdmin = mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + rootToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"admin_%s",
                                  "password":"Adm1nPass!",
                                  "displayName":"Admin %s",
                                  "role":"ADMIN",
                                  "qosProfile":"admin-standard"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.qosProfile").value("admin-standard"))
                .andReturn();

        String adminUserId = JsonPath.read(createAdmin.getResponse().getContentAsString(), "$.userId");

        MvcResult adminLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"admin_%s",
                                  "password":"Adm1nPass!"
                                }
                                """.formatted(suffix)))
                .andExpect(status().isOk())
                .andReturn();
        String adminToken = JsonPath.read(adminLogin.getResponse().getContentAsString(), "$.token.accessToken");

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"admin2_%s",
                                  "password":"Adm1nPass!",
                                  "displayName":"Admin2 %s",
                                  "role":"ADMIN"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/users/%s/disable".formatted(adminUserId))
                        .header("Authorization", "Bearer " + rootToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void refreshLogoutAndSessionRevokeShouldWork() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "r_" + suffix;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"Passw0rd!",
                                  "displayName":"User %s"
                                }
                                """.formatted(username, suffix)))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"Passw0rd!"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();

        String loginJson = login.getResponse().getContentAsString();
        String access = JsonPath.read(loginJson, "$.token.accessToken");
        String refresh = JsonPath.read(loginJson, "$.token.refreshToken");
        String sessionId = JsonPath.read(loginJson, "$.token.sessionId");

        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].sessionId", hasItem(sessionId)));

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken":"%s",
                                  "sessionId":"%s"
                                }
                                """.formatted(refresh, sessionId)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshedJson = refreshed.getResponse().getContentAsString();
        String refreshedAccess = JsonPath.read(refreshedJson, "$.token.accessToken");
        String refreshedRefresh = JsonPath.read(refreshedJson, "$.token.refreshToken");
        String refreshedSessionId = JsonPath.read(refreshedJson, "$.token.sessionId");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken":"%s",
                                  "sessionId":"%s"
                                }
                                """.formatted(refresh, sessionId)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken":"%s",
                                  "sessionId":"%s"
                                }
                                """.formatted(refreshedRefresh, refreshedSessionId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + refreshedAccess))
                .andExpect(status().isOk());
    }

    @Test
    void captchaShouldBeRequiredAndOneTimeWhenRiskTriggered() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "c_" + suffix;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"Passw0rd!",
                                  "displayName":"User %s"
                                }
                                """.formatted(username, suffix)))
                .andExpect(status().isOk());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username":"%s",
                                      "password":"WrongPass1!"
                                    }
                                    """.formatted(username)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"WrongPass1!"
                                }
                                """.formatted(username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CAPTCHA_REQUIRED"));

        MvcResult captcha = mockMvc.perform(post("/api/v1/auth/captcha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scene":"login"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String captchaId = JsonPath.read(captcha.getResponse().getContentAsString(), "$.captchaId");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"Passw0rd!",
                                  "captchaId":"%s",
                                  "captchaAnswer":"123456"
                                }
                                """.formatted(username, captchaId)))
                .andExpect(status().isOk());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username":"%s",
                                      "password":"WrongPass1!"
                                    }
                                    """.formatted(username)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"Passw0rd!",
                                  "captchaId":"%s",
                                  "captchaAnswer":"123456"
                                }
                                """.formatted(username, captchaId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CAPTCHA_INVALID"));
    }

    @Test
    void meShouldNotExposeMountOrSharedEntryAndAuditShouldQueryableByAdmin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "m_" + suffix;
        registerUser(username, "Passw0rd!");
        String access = loginUser(username, "Passw0rd!");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qosProfile").value("default"))
                .andExpect(jsonPath("$.mounts").doesNotExist())
                .andExpect(jsonPath("$.shared").doesNotExist());

        MvcResult rootLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"root",
                                  "password":"Root@123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String rootToken = JsonPath.read(rootLogin.getResponse().getContentAsString(), "$.token.accessToken");

        mockMvc.perform(get("/api/v1/admin/audit/events")
                        .header("Authorization", "Bearer " + rootToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("auth_login")))
                .andExpect(jsonPath("$[*].action", hasItem("auth_register")));
    }

    @Test
    void rootShouldNotBeCreatableByAdminApi() throws Exception {
        MvcResult rootLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"root",
                                  "password":"Root@123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String rootToken = JsonPath.read(rootLogin.getResponse().getContentAsString(), "$.token.accessToken");

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + rootToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"root_shadow",
                                  "password":"Root@123456",
                                  "displayName":"Root Shadow",
                                  "role":"ROOT"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OWNER_IMMUTABLE"));
    }

    @Test
    void rootShouldNotBeDisabledByUpdateApi() throws Exception {
        MvcResult rootLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"root",
                                  "password":"Root@123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String rootToken = JsonPath.read(rootLogin.getResponse().getContentAsString(), "$.token.accessToken");

        MvcResult me = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + rootToken))
                .andExpect(status().isOk())
                .andReturn();
        String rootUserId = JsonPath.read(me.getResponse().getContentAsString(), "$.userId");

        mockMvc.perform(put("/api/v1/admin/users/{userId}", rootUserId)
                        .header("Authorization", "Bearer " + rootToken)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName":"Root Admin",
                                  "email":"root@example.com",
                                  "phone":"13800000000",
                                  "role":"ROOT",
                                  "status":"DISABLED"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OWNER_IMMUTABLE"));
    }

    private void registerUser(String username, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"%s",
                                  "displayName":"User"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk());
    }

    private String loginUser(String username, String password) throws Exception {
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



