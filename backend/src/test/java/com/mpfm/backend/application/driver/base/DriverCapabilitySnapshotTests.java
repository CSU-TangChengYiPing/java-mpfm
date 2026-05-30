package com.mpfm.backend.application.driver.base;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DriverCapabilitySnapshotTests {

    @Test
    void shouldUseSftpProxyProfileFromSnapshot() {
        DriverCapability capability = new DriverCapability(true, true, true, true, true, true, true, false, true, false);

        DriverCapabilitySnapshot snapshot = DriverCapabilitySnapshot.from("sftp", capability);

        assertThat(snapshot.onlyProxy()).isTrue();
        assertThat(snapshot.noLinkUrl()).isTrue();
        assertThat(snapshot.preferProxy()).isFalse();
        assertThat(snapshot.copy()).isFalse();
    }
}

