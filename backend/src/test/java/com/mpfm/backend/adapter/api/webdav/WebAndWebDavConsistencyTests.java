package com.mpfm.backend.adapter.api.webdav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mpfm.backend.adapter.api.file.FileApiModels;
import com.mpfm.backend.adapter.api.file.FileCommandController;
import com.mpfm.backend.adapter.api.file.FileQueryController;
import com.mpfm.backend.application.file.FileApplicationService;
import com.mpfm.backend.application.mount.MountApplicationService;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class WebAndWebDavConsistencyTests {

    @Mock
    private FileApplicationService fileApplicationService;
    @Mock
    private FileQueryController fileQueryController;
    @Mock
    private MountApplicationService mountApplicationService;
    @Mock
    private WebDavCacheService webDavCacheService;

    @Test
    void mkdirShouldDelegateSameArgumentsInWebAndWebDav() {
        FileCommandController webController = new FileCommandController(fileApplicationService);
        WebDavController davController = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";

        webController.mkdir(new FileApiModels.PathOperationRequest("/personal/mount-a/new-dir"), "\"v1\"", principal);

        MockHttpServletRequest davRequest = new MockHttpServletRequest("MKCOL", "/dav/personal/mount-a/new-dir");
        davRequest.addHeader("If-Match", "\"v1\"");
        davController.dispatch(davRequest, principal);

        verify(fileApplicationService, times(2)).mkdir("alice", "/personal/mount-a/new-dir", "\"v1\"");
    }

    @Test
    void deletePermissionDeniedShouldBeConsistent() {
        FileCommandController webController = new FileCommandController(fileApplicationService);
        WebDavController davController = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";
        doThrow(new BusinessException(ErrorCode.PERMISSION_DENIED, "path not writable"))
                .when(fileApplicationService).delete("alice", "/personal/mount-a/a.txt", "\"v1\"");

        assertThatThrownBy(() -> webController.delete("/personal/mount-a/a.txt", "\"v1\"", principal))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.PERMISSION_DENIED));

        MockHttpServletRequest davRequest = new MockHttpServletRequest("DELETE", "/dav/personal/mount-a/a.txt");
        davRequest.addHeader("If-Match", "\"v1\"");
        assertThatThrownBy(() -> davController.dispatch(davRequest, principal))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.PERMISSION_DENIED));
    }

    @Test
    void moveShouldResolveToSameDomainCall() {
        FileCommandController webController = new FileCommandController(fileApplicationService);
        WebDavController davController = new WebDavController(fileApplicationService, fileQueryController, mountApplicationService, webDavCacheService);
        Principal principal = () -> "alice";
        when(fileApplicationService.move("alice", "/personal/mount-a/a.txt", "/personal/mount-a/archive/a.txt", "\"v2\""))
                .thenReturn(new FileApplicationService.EntryResult(
                        "/personal/mount-a/archive/a.txt", "a.txt", "file", 1L, "2026-05-29T00:00:00Z",
                        null, true, true, true, "\"v2\"", "v2"));

        webController.move(new FileApiModels.MoveRequest(
                "/personal/mount-a/a.txt", "/personal/mount-a/archive/a.txt"), "\"v2\"", principal);

        when(fileApplicationService.stat("alice", "/personal/mount-a/archive/a.txt"))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "path not found"));
        MockHttpServletRequest davRequest = new MockHttpServletRequest("MOVE", "/dav/personal/mount-a/a.txt");
        davRequest.addHeader("Destination", "/dav/personal/mount-a/archive/a.txt");
        davRequest.addHeader("If-Match", "\"v2\"");
        davRequest.addHeader("Overwrite", "F");
        davController.dispatch(davRequest, principal);

        verify(fileApplicationService, times(2)).move(
                "alice",
                "/personal/mount-a/a.txt",
                "/personal/mount-a/archive/a.txt",
                "\"v2\"");
    }
}


