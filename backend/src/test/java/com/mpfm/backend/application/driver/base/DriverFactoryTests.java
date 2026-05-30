package com.mpfm.backend.application.driver.base;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriverFactoryTests {

    @Test
    void shouldResolveDriverByProtocolIgnoreCase() {
        DriverFactory factory = new DriverFactory(List.of(new DummyDriver("local"), new DummyDriver("webdav")));

        StorageDriver resolved = factory.resolve("LOCAL");

        assertThat(resolved.protocol()).isEqualTo("local");
    }

    @Test
    void shouldThrowWhenProtocolUnsupported() {
        DriverFactory factory = new DriverFactory(List.of(new DummyDriver("local")));

        assertThatThrownBy(() -> factory.resolve("ftp"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CAPABILITY_NOT_SUPPORTED));
    }

    private record DummyDriver(String protocol) implements StorageDriver {
        @Override
        public DriverCapability capability() {
            return DriverCapability.full();
        }

        @Override
        public void init(DriverContext context) {
        }

        @Override
        public List<DriverObject> list(DriverContext context, String dirPath) {
            return List.of();
        }

        @Override
        public DriverObject get(DriverContext context, String path) {
            return null;
        }

        @Override
        public DriverLink link(DriverContext context, String filePath) {
            return null;
        }

        @Override
        public void makeDir(DriverContext context, String parentDirPath, String dirName) {
        }

        @Override
        public void move(DriverContext context, String srcPath, String dstDirPath) {
        }

        @Override
        public void rename(DriverContext context, String srcPath, String newName) {
        }

        @Override
        public void copy(DriverContext context, String srcPath, String dstDirPath) {
        }

        @Override
        public void remove(DriverContext context, String path) {
        }

        @Override
        public DriverObject put(DriverContext context, DriverPutRequest request) {
            return null;
        }
    }
}
