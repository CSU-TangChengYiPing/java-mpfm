package com.mpfm.backend.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

class AvatarStorageServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldStoreAvatarAndLoadResource() throws Exception {
        AvatarStorageService service = buildService(300L);
        String dataUrl = toJpegDataUrl("avatar-content");

        String url = service.store("user-1", dataUrl);
        assertThat(url).isEqualTo("/api/v1/users/avatar/user-1");

        Resource resource = service.load("user-1");
        assertThat(resource.exists()).isTrue();
        try (InputStream input = resource.getInputStream()) {
            byte[] bytes = input.readAllBytes();
            assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("avatar-content");
        }
    }

    @Test
    void shouldRejectInvalidDataUrl() {
        AvatarStorageService service = buildService(300L);

        assertThatThrownBy(() -> service.store("user-1", "not-a-data-url"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void shouldSignAndVerifyUrl() {
        AvatarStorageService service = buildService(300L);
        service.store("user-1", toJpegDataUrl("avatar-content"));

        String signedUrl = service.signAvatarUrl("user-1", "/api/v1/users/avatar/user-1");
        assertThat(signedUrl).contains("exp=").contains("sig=");
        String query = signedUrl.substring(signedUrl.indexOf('?') + 1);
        String[] parts = query.split("&");
        String exp = parts[0].substring("exp=".length());
        String sig = parts[1].substring("sig=".length());

        service.verifyAvatarAccess("user-1", exp, sig);
    }

    @Test
    void shouldRejectExpiredSignedUrl() {
        AvatarStorageService service = buildService(300L);
        service.store("user-1", toJpegDataUrl("avatar-content"));

        String signedUrl = service.signAvatarUrl("user-1", "/api/v1/users/avatar/user-1");
        String query = signedUrl.substring(signedUrl.indexOf('?') + 1);
        String[] parts = query.split("&");
        String exp = String.valueOf(System.currentTimeMillis() / 1000 - 1);
        String sig = parts[1].substring("sig=".length());

        assertThatThrownBy(() -> service.verifyAvatarAccess("user-1", exp, sig))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.PERMISSION_DENIED));
    }

    private AvatarStorageService buildService(long expireSeconds) {
        AvatarStorageProperties properties = new AvatarStorageProperties(
                tempDir.toString(),
                "0123456789abcdef0123456789abcdef",
                expireSeconds
        );
        return new AvatarStorageService(properties);
    }

    private String toJpegDataUrl(String content) {
        return "data:image/jpeg;base64," + Base64.getEncoder()
                .encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }
}
