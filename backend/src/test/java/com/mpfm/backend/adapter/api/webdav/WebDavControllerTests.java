package com.mpfm.backend.adapter.api.webdav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

import com.mpfm.backend.adapter.api.file.FileQueryController;
import com.mpfm.backend.application.file.FileApplicationService;
import com.mpfm.backend.application.mount.MountApplicationService;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.security.Principal;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class WebDavControllerTests {

    @Mock
    private FileApplicationService fileApplicationService;
    @Mock
    private FileQueryController fileQueryController;
    @Mock
    private MountApplicationService mountApplicationService;
    @Mock
    private WebDavCacheService webDavCacheService;

    @Test
    void propfindShouldReturnMultiStatusXml() {
        mockCachePassThrough();
        WebDavController controller = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";
        MockHttpServletRequest request = new MockHttpServletRequest("PROPFIND", "/dav/personal/mount-a/docs");
        request.addHeader("Depth", "1");
        when(fileApplicationService.stat("alice", "/personal/mount-a/docs"))
                .thenReturn(new FileApplicationService.EntryResult(
                        "/personal/mount-a/docs", "docs", "directory", 0L, "2026-05-29T00:00:00Z",
                        null, true, true, true, "\"v1\"", "v1"));
        when(fileApplicationService.list("alice", "/personal/mount-a/docs"))
                .thenReturn(List.of(new FileApplicationService.EntryResult(
                        "/personal/mount-a/docs/a.txt", "a.txt", "file", 12L, "2026-05-29T00:00:01Z",
                        null, true, true, true, "\"v2\"", "v2")));

        ResponseEntity<?> response = controller.dispatch(request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
        assertThat(String.valueOf(response.getBody())).contains("multistatus").contains("a.txt");
        assertThat(String.valueOf(response.getBody())).contains("/dav/personal/mount-a/docs/");
    }

    @Test
    void propfindDavRootShouldContainPersonalAndShared() {
        mockCachePassThrough();
        WebDavController controller = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";
        MockHttpServletRequest request = new MockHttpServletRequest("PROPFIND", "/dav");
        request.addHeader("Depth", "1");

        ResponseEntity<?> response = controller.dispatch(request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
        String xml = String.valueOf(response.getBody());
        assertThat(xml).contains("/dav/").contains("/dav/personal/").contains("/dav/shared/");
    }

    @Test
    void propfindPropnameShouldReturnNameOnlyNodes() {
        mockCachePassThrough();
        WebDavController controller = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";
        MockHttpServletRequest request = new MockHttpServletRequest("PROPFIND", "/dav/personal/mount-a/docs");
        request.setContent("""
                <?xml version="1.0"?>
                <d:propfind xmlns:d="DAV:"><d:propname/></d:propfind>
                """.getBytes());
        when(fileApplicationService.stat("alice", "/personal/mount-a/docs"))
                .thenReturn(new FileApplicationService.EntryResult(
                        "/personal/mount-a/docs", "docs", "directory", 0L, "2026-05-29T00:00:00Z",
                        null, true, true, true, "\"v1\"", "v1"));
        when(fileApplicationService.list("alice", "/personal/mount-a/docs")).thenReturn(List.of());

        ResponseEntity<?> response = controller.dispatch(request, principal);
        String xml = String.valueOf(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
        assertThat(xml).contains("<d:displayname/>").contains("<d:getetag/>");
        assertThat(xml).doesNotContain("<d:displayname>docs</d:displayname>");
    }

    @Test
    void propfindSelectedPropsShouldOnlyContainRequestedProperties() {
        mockCachePassThrough();
        WebDavController controller = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";
        MockHttpServletRequest request = new MockHttpServletRequest("PROPFIND", "/dav/personal/mount-a/docs");
        request.setContent("""
                <?xml version="1.0"?>
                <d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:getetag/></d:prop></d:propfind>
                """.getBytes());
        when(fileApplicationService.stat("alice", "/personal/mount-a/docs"))
                .thenReturn(new FileApplicationService.EntryResult(
                        "/personal/mount-a/docs", "docs", "directory", 0L, "2026-05-29T00:00:00Z",
                        null, true, true, true, "\"v1\"", "v1"));
        when(fileApplicationService.list("alice", "/personal/mount-a/docs")).thenReturn(List.of());

        ResponseEntity<?> response = controller.dispatch(request, principal);
        String xml = String.valueOf(response.getBody());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
        assertThat(xml).contains("<d:displayname>").contains("<d:getetag>");
        assertThat(xml).doesNotContain("<d:getcontentlength>");
    }

    @Test
    void putShouldRequirePreconditionHeader() {
        WebDavController controller = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/dav/personal/mount-a/a.txt");

        assertThatThrownBy(() -> controller.dispatch(request, principal))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void moveShouldResolveDestinationAndDelegate() {
        WebDavController controller = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";
        MockHttpServletRequest request = new MockHttpServletRequest("MOVE", "/dav/personal/mount-a/a.txt");
        request.addHeader("Destination", "/dav/personal/mount-a/archive/a.txt");
        request.addHeader("If-Match", "\"v1\"");

        ResponseEntity<?> response = controller.dispatch(request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(fileApplicationService).move("alice", "/personal/mount-a/a.txt", "/personal/mount-a/archive/a.txt", "\"v1\"");
    }

    @Test
    void lockShouldReturnCapabilityNotSupported() {
        WebDavController controller = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";
        MockHttpServletRequest request = new MockHttpServletRequest("LOCK", "/dav/personal/mount-a/a.txt");

        ResponseEntity<?> response = controller.dispatch(request, principal);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(String.valueOf(response.getBody())).contains("CAPABILITY_NOT_SUPPORTED");
    }

    @Test
    void unsupportedMethodShouldReturnNotImplemented() {
        WebDavController controller = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";
        MockHttpServletRequest request = new MockHttpServletRequest("TRACE", "/dav/personal/mount-a/a.txt");

        ResponseEntity<?> response = controller.dispatch(request, principal);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(String.valueOf(response.getBody())).contains("CAPABILITY_NOT_SUPPORTED");
    }

    @Test
    void moveOverwriteFalseShouldConflictWhenDestinationExists() {
        WebDavController controller = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";
        MockHttpServletRequest request = new MockHttpServletRequest("MOVE", "/dav/personal/mount-a/a.txt");
        request.addHeader("Destination", "/dav/personal/mount-a/archive/a.txt");
        request.addHeader("If-Match", "\"v1\"");
        request.addHeader("Overwrite", "F");
        when(fileApplicationService.stat("alice", "/personal/mount-a/archive/a.txt"))
                .thenReturn(new FileApplicationService.EntryResult(
                        "/personal/mount-a/archive/a.txt", "a.txt", "file", 12L, "2026-05-29T00:00:01Z",
                        null, true, true, true, "\"v2\"", "v2"));

        assertThatThrownBy(() -> controller.dispatch(request, principal))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void propfindNoiseTraversalPathShouldShortCircuit404() {
        WebDavController controller = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";
        MockHttpServletRequest request = new MockHttpServletRequest("PROPFIND", "/dav/personal/mount-a/%24Recycle.Bin");

        ResponseEntity<?> response = controller.dispatch(request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(fileApplicationService, never()).stat(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void propfindShouldReturnTooManyRequestsWhenRateLimited() {
        WebDavController controller = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";
        MockHttpServletRequest request = new MockHttpServletRequest("PROPFIND", "/dav/personal/mount-a/docs");
        request.addHeader("Depth", "1");
        when(webDavCacheService.shouldRateLimitPropfind("alice", "/personal/mount-a/docs", 1)).thenReturn(true);

        ResponseEntity<?> response = controller.dispatch(request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(fileApplicationService, never()).stat(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void propfindShouldDegradeWhenChildrenQueryFails() {
        mockCachePassThrough();
        WebDavController controller = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";
        MockHttpServletRequest request = new MockHttpServletRequest("PROPFIND", "/dav/personal/mount-a/docs");
        request.addHeader("Depth", "1");
        when(fileApplicationService.stat("alice", "/personal/mount-a/docs"))
                .thenReturn(new FileApplicationService.EntryResult(
                        "/personal/mount-a/docs", "docs", "directory", 0L, "2026-05-29T00:00:00Z",
                        null, true, true, true, "\"v1\"", "v1"));
        when(fileApplicationService.list("alice", "/personal/mount-a/docs"))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "probe child failed"));

        ResponseEntity<?> response = controller.dispatch(request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
        String xml = String.valueOf(response.getBody());
        assertThat(xml).contains("/dav/personal/mount-a/docs/");
        assertThat(xml).doesNotContain("/dav/personal/mount-a/docs/a.txt");
    }

    private void mockCachePassThrough() {
        lenient().when(webDavCacheService.getOrLoadStat(anyString(), anyString(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
        lenient().when(webDavCacheService.getOrLoadList(anyString(), anyString(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
        lenient().when(webDavCacheService.getOrLoadPropfindXml(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());
        lenient().when(webDavCacheService.shouldRateLimitPropfind(anyString(), anyString(), anyInt())).thenReturn(false);
        lenient().when(webDavCacheService.tryServeHotPropfindCache(anyString(), anyString(), anyInt(), anyString())).thenReturn(null);
    }
}


