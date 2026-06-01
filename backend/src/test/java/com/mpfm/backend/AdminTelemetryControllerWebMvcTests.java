package com.mpfm.backend;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mpfm.backend.adapter.api.admin.AdminTelemetryController;
import com.mpfm.backend.adapter.api.config.RestAccessDeniedHandler;
import com.mpfm.backend.adapter.api.config.RestAuthenticationEntryPoint;
import com.mpfm.backend.adapter.api.config.SecurityConfig;
import com.mpfm.backend.adapter.api.exception.GlobalExceptionHandler;
import com.mpfm.backend.application.monitor.TransferTelemetryService;
import com.mpfm.backend.application.monitor.SystemTelemetryService;
import com.mpfm.backend.application.monitor.UserTransferGovernanceService;
import com.mpfm.backend.application.security.SecurityPolicyService;
import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.common.logging.RequestCorrelationFilter;
import com.mpfm.backend.common.security.WebDavUserCacheService;
import com.mpfm.backend.common.security.JwtTokenService;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import java.util.List;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminTelemetryController.class)
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
class AdminTelemetryControllerWebMvcTests {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private SecurityPolicyService securityPolicyService;
    @MockitoBean
    private WebDavUserCacheService webDavUserCacheService;
    @MockitoBean
    private TransferTelemetryService transferTelemetryService;
    @MockitoBean
    private SystemTelemetryService systemTelemetryService;
    @MockitoBean
    private UserTransferGovernanceService userTransferGovernanceService;

    @Test
    void shouldRejectNonRootUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/telemetry/users/transfer").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    @Test
    void shouldReturnUserTransferStatsForRoot() throws Exception {
        given(transferTelemetryService.forAllUsers()).willReturn(List.of(
                new TransferTelemetryService.TransferSnapshot("alice", 1024, 2048, 1, 1, 2)));

        mockMvc.perform(get("/api/v1/admin/telemetry/users/transfer").with(user("root").roles("ROOT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].uploadBps").value(1024))
                .andExpect(jsonPath("$[0].downloadBps").value(2048));
    }

    @Test
    void shouldReturnSystemOverviewForRoot() throws Exception {
        given(systemTelemetryService.current()).willReturn(new SystemTelemetryService.SystemSnapshot(
                Instant.parse("2026-05-24T00:00:00Z"),
                0.5D,
                0.25D,
                1024L,
                512L,
                256L,
                1024L,
                2048L,
                1024L,
                512L,
                256L,
                "Windows",
                "11",
                "amd64",
                8,
                "Intel",
                1.0D,
                5.0D,
                15.0D,
                1234L,
                5678L,
                111L,
                222L,
                91011L,
                121314L,
                12345L));
        mockMvc.perform(get("/api/v1/admin/telemetry/system/overview").with(user("root").roles("ROOT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpuLoad").value(0.5))
                .andExpect(jsonPath("$.osName").value("Windows"));
    }

    @Test
    void shouldReturnUserTransferHistoryForRoot() throws Exception {
        given(transferTelemetryService.userTimeline("alice", 5)).willReturn(List.of(
                new TransferTelemetryService.TransferTimelinePoint(OffsetDateTime.parse("2026-05-25T01:00:00Z").toString(), 1024, 2048)));
        mockMvc.perform(get("/api/v1/admin/telemetry/users/alice/history?minutes=5").with(user("root").roles("ROOT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uploadBps").value(1024))
                .andExpect(jsonPath("$[0].downloadBps").value(2048));
    }

    @Test
    void shouldApplyGovernanceActionForRoot() throws Exception {
        given(userTransferGovernanceService.pauseUpload("alice", true, "root"))
                .willReturn(new UserTransferGovernanceService.GovernanceState("alice", true, false));
        given(userTransferGovernanceService.stateOf("alice"))
                .willReturn(new UserTransferGovernanceService.GovernanceState("alice", true, false));
        mockMvc.perform(post("/api/v1/admin/telemetry/users/alice/governance")
                        .contentType("application/json")
                        .content("{\"action\":\"pause_upload\"}")
                        .with(user("root").roles("ROOT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.uploadPaused").value(true));
    }

    @Test
    void shouldRejectUnsupportedGovernanceAction() throws Exception {
        mockMvc.perform(post("/api/v1/admin/telemetry/users/alice/governance")
                        .contentType("application/json")
                        .content("{\"action\":\"oops\"}")
                        .with(user("root").roles("ROOT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectGovernanceActionForNonRoot() throws Exception {
        mockMvc.perform(post("/api/v1/admin/telemetry/users/alice/governance")
                        .contentType("application/json")
                        .content("{\"action\":\"pause_upload\"}")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }
}
