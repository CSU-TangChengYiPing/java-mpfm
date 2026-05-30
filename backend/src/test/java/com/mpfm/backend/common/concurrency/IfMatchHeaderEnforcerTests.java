package com.mpfm.backend.common.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class IfMatchHeaderEnforcerTests {

    private final IfMatchHeaderEnforcer filter = new IfMatchHeaderEnforcer();

    @Test
    void shouldRejectDeleteWithoutIfMatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/files/content");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("VALIDATION_ERROR");
    }

    @Test
    void shouldAllowDeleteWithIfMatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/files/content");
        request.addHeader("If-Match", "\"v1\"");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldAllowUploadInitWithoutIfMatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/files/upload/init");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}

