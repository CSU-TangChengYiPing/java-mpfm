package com.mpfm.backend.application.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mpfm.backend.application.file.NamespaceResolver;
import com.mpfm.backend.application.task.AsyncTaskService;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class UploadSessionSupportTests {

    @TempDir
    Path tempDir;

    @Test
    void writeStreamToTargetFile_shouldWriteOnceAndMoveAtomically() throws Exception {
        UploadSessionSupport support = new UploadSessionSupport(
                mock(AsyncTaskService.class),
                mockResolver(tempDir),
                new ObjectMapper());
        Path target = support.resolveUploadTargetFile("a123", "/personal/m1/docs", "a.bin");
        byte[] content = "hello-world".getBytes();

        UploadSessionSupport.WriteResult written = support.writeStreamToTargetFile(target, new ByteArrayInputStream(content), content.length);

        assertEquals(target, written.targetFile());
        assertEquals(content.length, written.writtenBytes());
        assertEquals(content.length, Files.size(target));
        assertFalse(Files.exists(target.resolveSibling("a.bin.uploading")));
    }

    @Test
    void writeStreamToTargetFile_shouldFailWhenSizeMismatch() {
        UploadSessionSupport support = new UploadSessionSupport(
                mock(AsyncTaskService.class),
                mockResolver(tempDir),
                new ObjectMapper());
        Path target = support.resolveUploadTargetFile("a123", "/personal/m1/docs", "b.bin");
        byte[] content = "hello".getBytes();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> support.writeStreamToTargetFile(target, new ByteArrayInputStream(content), content.length + 1L));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getCode());
        assertFalse(Files.exists(target));
        assertFalse(Files.exists(target.resolveSibling("b.bin.uploading")));
    }

    private NamespaceResolver mockResolver(Path rootPath) {
        NamespaceResolver resolver = mock(NamespaceResolver.class);
        MountEntity mount = new MountEntity();
        mount.setPhysicalRoot(rootPath.toString());
        NamespaceResolver.ResolveResult result = new NamespaceResolver.ResolveResult(
                mount, "docs", "/personal/m1/docs", false, true);
        when(resolver.resolve("a123", "/personal/m1/docs", true, true)).thenReturn(result);
        return resolver;
    }
}
