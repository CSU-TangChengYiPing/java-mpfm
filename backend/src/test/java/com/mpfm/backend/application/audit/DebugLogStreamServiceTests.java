package com.mpfm.backend.application.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DebugLogStreamServiceTests {

    @Test
    void shouldParseAccessLogLine() {
        DebugLogLineParser parser = new DebugLogLineParser();
        String line = "2026-05-28T18:31:34.804Z INFO  301164 --- [mpfm-backend] [2e5b4feb,2e5b4feb] ACCESS : request method=GET path=/api/v1/files/list status=400 costMs=10 query=virtualPath=. body=";

        DebugLogStreamService.DebugLogEvent event = parser.parse(line);

        assertEquals("2026-05-28T18:31:34.804Z", event.time());
        assertEquals("INFO", event.level());
        assertEquals("ACCESS", event.category());
        assertEquals("/api/v1/files/list", event.path());
        assertEquals(400, event.status());
        assertEquals(10, event.costMs());
        assertEquals("2e5b4feb", event.traceId());
        assertEquals("2e5b4feb", event.requestId());
    }

    @Test
    void shouldThrowInvalidArgumentWhenLevelIllegal() {
        BackendLogReadService readService = mock(BackendLogReadService.class);
        SecurityEventLogger securityEventLogger = mock(SecurityEventLogger.class);
        DebugLogStreamService service = new DebugLogStreamService(
                readService,
                securityEventLogger,
                new DebugLogLineParser(),
                new DebugLogStreamRequestValidator());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.subscribe(
                new DebugLogStreamService.DebugLogStreamRequest("TRACE", null, null, 100, null),
                "root"));
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.getCode());
    }

    @Test
    void shouldThrowSourceUnavailableWhenLogMissing() {
        BackendLogReadService readService = mock(BackendLogReadService.class);
        SecurityEventLogger securityEventLogger = mock(SecurityEventLogger.class);
        DebugLogStreamService service = new DebugLogStreamService(
                readService,
                securityEventLogger,
                new DebugLogLineParser(),
                new DebugLogStreamRequestValidator());
        Path path = Path.of("./logs/mpfm-backend.log").normalize();
        given(readService.resolveLogPath(null)).willReturn(Optional.of(path));
        given(readService.logExists(path)).willReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.subscribe(
                new DebugLogStreamService.DebugLogStreamRequest("INFO", null, null, 100, null),
                "root"));
        assertEquals(ErrorCode.DEBUG_STREAM_SOURCE_UNAVAILABLE, ex.getCode());
    }
}
