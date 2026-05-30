package com.mpfm.backend.common.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AccessLogFilterTests {

    @Test
    void shouldOmitMultipartChunkBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/files/upload/chunk");
        request.setContentType("multipart/form-data; boundary=abc");
        assertTrue(AccessLogFilter.shouldOmitBody(request));
    }

    @Test
    void shouldOmitV2BinaryPartBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v2/uploads/parts/ticket-1");
        request.setContentType("application/octet-stream");
        assertTrue(AccessLogFilter.shouldOmitBody(request));
    }

    @Test
    void shouldKeepNormalJsonBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/files/mkdir");
        request.setContentType("application/json");
        assertFalse(AccessLogFilter.shouldOmitBody(request));
    }
}
