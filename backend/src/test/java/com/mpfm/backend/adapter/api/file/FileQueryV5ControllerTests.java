package com.mpfm.backend.adapter.api.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class FileQueryV5ControllerTests {

    @Mock
    private FileQueryController fileQueryController;

    @Mock
    private HttpServletRequest request;

    @Test
    void contentShouldDelegateToDownloadFlow() {
        FileQueryV5Controller controller = new FileQueryV5Controller(fileQueryController);
        Principal principal = () -> "alice";
        ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> expected =
                ResponseEntity.ok(outputStream -> outputStream.write(new byte[] {1, 2, 3}));
        when(fileQueryController.download("/personal/mount-a/demo.txt", request, principal))
                .thenAnswer(invocation -> expected);

        ResponseEntity<?> actual = controller.content("/personal/mount-a/demo.txt", request, principal);

        assertThat(actual).isSameAs(expected);
    }
}
