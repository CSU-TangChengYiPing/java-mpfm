package com.mpfm.backend.adapter.api;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HealthControllerTests {

    @TempDir
    Path tempDir;

    @Test
    void downloadDevCertShouldReturnAttachment() throws Exception {
        Path certFile = tempDir.resolve("mpfm-local.cer");
        byte[] expected = "dummy-cert".getBytes();
        Files.write(certFile, expected);
        HealthController controller = new HealthController(certFile.toString());

        ResponseEntity<byte[]> response = controller.downloadDevCert();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment").contains("mpfm-local.cer");
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void downloadDevCertShouldThrowWhenMissing() {
        Path missing = tempDir.resolve("not-exists.cer");
        HealthController controller = new HealthController(missing.toString());

        assertThatThrownBy(controller::downloadDevCert)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}

