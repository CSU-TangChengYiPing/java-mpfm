package com.mpfm.backend.application.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferSessionStoreTests {

    @Test
    void shouldPersistAndLoadUploadSession() {
        TransferSessionStore store = new TransferSessionStore(new ObjectMapper());
        TransferChunkService.UploadSession session = new TransferChunkService.UploadSession(
                UUID.randomUUID().toString(), "u1", "/personal/m1", "a.bin",
                10L, 4L, 3, UUID.randomUUID().toString(), "RUNNING",
                Instant.now().toString(), Instant.now().toString(), "D:/tmp/payload.bin", List.of(1), "", "runtime", "");

        store.saveUpload(session);
        TransferChunkService.UploadSession loaded = store.loadUpload(session.uploadId());

        assertThat(loaded.uploadId()).isEqualTo(session.uploadId());
        assertThat(loaded.completedParts()).containsExactly(1);
    }
}
