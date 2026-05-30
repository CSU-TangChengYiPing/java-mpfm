package com.mpfm.backend.application.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mpfm.backend.application.security.QosPolicyService;
import com.mpfm.backend.application.security.TransferBandwidthLimiter;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransferChunkEngineTests {

    @TempDir
    Path tempDir;

    @Test
    void uploadPartShouldBeIdempotent() throws Exception {
        UploadChunkEngine engine = new UploadChunkEngine(newLimiter(), new TransferIntegrityVerifier());
        Path payload = tempDir.resolve("payload.bin");
        byte[] part = "abc".getBytes();
        TransferChunkService.UploadSession session = new TransferChunkService.UploadSession(
                UUID.randomUUID().toString(), "u1", "/personal/m1", "a.txt",
                3L, 3L, 1, UUID.randomUUID().toString(), "RUNNING",
                Instant.now().toString(), Instant.now().toString(), payload.toString(), new ArrayList<>(), "", "runtime", "");

        TransferChunkService.UploadSession first = engine.writePart("u1", session, 1, part, sha256(part));
        TransferChunkService.UploadSession second = engine.writePart("u1", first, 1, part, sha256(part));

        assertThat(first.completedParts()).containsExactly(1);
        assertThat(second.completedParts()).containsExactly(1);
        assertThat(Files.readAllBytes(payload)).isEqualTo(part);
    }

    @Test
    void uploadPartShouldRejectChecksumMismatch() {
        UploadChunkEngine engine = new UploadChunkEngine(newLimiter(), new TransferIntegrityVerifier());
        TransferChunkService.UploadSession session = new TransferChunkService.UploadSession(
                UUID.randomUUID().toString(), "u1", "/personal/m1", "a.txt",
                3L, 3L, 1, UUID.randomUUID().toString(), "RUNNING",
                Instant.now().toString(), Instant.now().toString(), tempDir.resolve("x.bin").toString(), List.of(), "", "runtime", "");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> engine.writePart("u1", session, 1, "abc".getBytes(), "deadbeef"));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    private TransferBandwidthLimiter newLimiter() {
        QosPolicyService qosPolicyService = mock(QosPolicyService.class);
        when(qosPolicyService.effectivePolicy("u1"))
                .thenReturn(new QosPolicyService.QosPolicy("p1", "P1", 1024 * 1024L, 1024 * 1024L, 1, 1, true, "root"));
        return new TransferBandwidthLimiter(qosPolicyService);
    }

    private String sha256(byte[] payload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(payload));
    }
}
