package com.mpfm.backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mpfm.backend.adapter.api.admin.AdminDebugLogStreamController;
import com.mpfm.backend.adapter.api.config.RestAccessDeniedHandler;
import com.mpfm.backend.adapter.api.config.RestAuthenticationEntryPoint;
import com.mpfm.backend.adapter.api.config.SecurityConfig;
import com.mpfm.backend.adapter.api.exception.GlobalExceptionHandler;
import com.mpfm.backend.application.audit.DebugLogStreamService;
import com.mpfm.backend.application.security.SecurityPolicyService;
import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.common.logging.RequestCorrelationFilter;
import com.mpfm.backend.common.security.JwtTokenService;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(controllers = AdminDebugLogStreamController.class)
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
class AdminDebugLogStreamControllerWebMvcTests {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private SecurityPolicyService securityPolicyService;
    @MockitoBean
    private DebugLogStreamService debugLogStreamService;

    @Test
    void shouldRejectNonRootUser() throws Exception {
        mockMvc.perform(get("/api/v1/debug/logs/stream").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }

    @Test
    void shouldReturnInvalidArgumentWhenParamsInvalid() throws Exception {
        given(debugLogStreamService.subscribe(any(), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid level"));
        mockMvc.perform(get("/api/v1/debug/logs/stream")
                        .with(user("root").roles("ROOT"))
                        .param("level", "TRACE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void shouldOpenSseForRoot() throws Exception {
        given(debugLogStreamService.subscribe(any(), any())).willReturn(new SseEmitter(0L));
        mockMvc.perform(get("/api/v1/debug/logs/stream")
                        .with(user("root").roles("ROOT")))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    void shouldRecordCopyAuditForRoot() throws Exception {
        mockMvc.perform(post("/api/v1/debug/logs/copy-audit")
                        .with(user("root").roles("ROOT"))
                        .contentType("application/json")
                        .content("{\"visibleLines\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.operator").value("root"));
    }
}
