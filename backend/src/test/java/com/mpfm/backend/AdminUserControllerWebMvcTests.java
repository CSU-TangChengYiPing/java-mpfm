package com.mpfm.backend;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mpfm.backend.adapter.api.admin.AdminUserController;
import com.mpfm.backend.adapter.api.config.RestAccessDeniedHandler;
import com.mpfm.backend.adapter.api.config.RestAuthenticationEntryPoint;
import com.mpfm.backend.adapter.api.config.SecurityConfig;
import com.mpfm.backend.adapter.api.exception.GlobalExceptionHandler;
import com.mpfm.backend.application.security.SecurityPolicyService;
import com.mpfm.backend.application.user.UserManagementService;
import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.common.logging.RequestCorrelationFilter;
import com.mpfm.backend.common.security.JwtTokenService;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminUserController.class)
@Import({
        SecurityConfig.class,
        RequestCorrelationFilter.class,
        SecurityEventLogger.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "security.jwt.issuer=test",
        "security.jwt.access-token-expire-seconds=600",
        "security.jwt.refresh-token-expire-seconds=3600",
        "security.jwt.signing-key=0123456789abcdef0123456789abcdef"
})
class AdminUserControllerWebMvcTests {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private SecurityPolicyService securityPolicyService;
    @MockitoBean
    private UserManagementService userManagementService;

    @Test
    void shouldReturnCustomLimitAndGovernanceStateInList() throws Exception {
        UUID userId = UUID.randomUUID();
        given(userManagementService.search("root", null, null, null))
                .willReturn(List.of(new UserManagementService.UserSummary(
                        userId, "alice", "Alice", null, "USER", "ACTIVE", "default",
                        8 * 1024 * 1024L, 6 * 1024 * 1024L, true, true, false
                )));

        mockMvc.perform(get("/api/v1/admin/users").with(user("root").roles("ROOT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].customUploadBps").value(8 * 1024 * 1024L))
                .andExpect(jsonPath("$.items[0].customDownloadBps").value(6 * 1024 * 1024L))
                .andExpect(jsonPath("$.items[0].uploadPaused").value(true))
                .andExpect(jsonPath("$.items[0].downloadPaused").value(false));
    }

    @Test
    void shouldAcceptCustomLimitAndGovernanceStateInUpdate() throws Exception {
        UUID userId = UUID.randomUUID();
        given(userManagementService.adminUpdateUser(
                org.mockito.ArgumentMatchers.eq("root"),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("Alice"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("default"),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(false)))
                .willReturn(new UserManagementService.UserSummary(
                        userId, "alice", "Alice", null, "USER", "ACTIVE", "default", 10L, 20L, true, true, false
                ));

        mockMvc.perform(put("/api/v1/admin/users/{userId}", userId)
                        .with(user("root").roles("ROOT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName":"Alice",
                                  "role":"user",
                                  "status":"active",
                                  "qosProfile":"default",
                                  "customUploadBps":10,
                                  "customDownloadBps":20,
                                  "qosCustomEnabled":true,
                                  "uploadPaused":true,
                                  "downloadPaused":false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customUploadBps").value(10))
                .andExpect(jsonPath("$.customDownloadBps").value(20))
                .andExpect(jsonPath("$.qosCustomEnabled").value(true))
                .andExpect(jsonPath("$.uploadPaused").value(true))
                .andExpect(jsonPath("$.downloadPaused").value(false));
    }
}
