package com.mpfm.backend.application.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mpfm.backend.application.driver.base.DriverFactory;
import com.mpfm.backend.application.driver.sftp.SftpDriverUtil;
import com.mpfm.backend.application.share.v5.ShareAuthorizationV5Service;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class FileQueryServiceTests {

    @Test
    void readFileShouldSupportSftpMount() throws Exception {
        FileAccessService accessService = org.mockito.Mockito.mock(FileAccessService.class);
        ShareAuthorizationV5Service shareAuthorizationV5Service = org.mockito.Mockito.mock(ShareAuthorizationV5Service.class);
        FileEntryMapper fileEntryMapper = org.mockito.Mockito.mock(FileEntryMapper.class);
        DriverFactory driverFactory = org.mockito.Mockito.mock(DriverFactory.class);
        FileQueryService queryService = new FileQueryService(accessService, shareAuthorizationV5Service, fileEntryMapper, driverFactory);

        MountEntity mount = new MountEntity();
        mount.setId(UUID.randomUUID());
        mount.setType("sftp");
        mount.setPhysicalRoot("sftp://alice:pwd@127.0.0.1:22/remote");
        FileAccessService.AccessContext context = new FileAccessService.AccessContext(
                mount, Path.of("demo.txt"), false, true, "/personal/mount-a/demo.txt");
        when(accessService.requireAccess("alice", mount.getId(), "demo.txt", false, true)).thenReturn(context);

        SftpClient sftpClient = org.mockito.Mockito.mock(SftpClient.class);
        SftpClient.Attributes attrs = org.mockito.Mockito.mock(SftpClient.Attributes.class);
        SftpClient.CloseableHandle handle = org.mockito.Mockito.mock(SftpClient.CloseableHandle.class);
        when(attrs.isDirectory()).thenReturn(false);
        when(attrs.getSize()).thenReturn(5L);
        when(sftpClient.stat("/remote/demo.txt")).thenReturn(attrs);
        when(sftpClient.open("/remote/demo.txt", SftpClient.OpenMode.Read)).thenReturn(handle);
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        when(sftpClient.read(
                org.mockito.ArgumentMatchers.eq(handle),
                org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(5)))
                .thenAnswer(invocation -> {
                    byte[] buf = invocation.getArgument(2);
                    System.arraycopy(payload, 0, buf, 0, payload.length);
                    return payload.length;
                });
        SftpDriverUtil.SftpConnection connection = new SftpDriverUtil.SftpConnection(
                org.mockito.Mockito.mock(SshClient.class),
                org.mockito.Mockito.mock(ClientSession.class),
                sftpClient,
                "/remote");

        try (MockedStatic<SftpDriverUtil> mocked = org.mockito.Mockito.mockStatic(SftpDriverUtil.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> SftpDriverUtil.open(org.mockito.ArgumentMatchers.any())).thenReturn(connection);
            mocked.when(() -> SftpDriverUtil.closeQuietly(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> null);
            String text = queryService.readFile("alice", mount.getId(), "demo.txt");
            assertThat(text).isEqualTo("hello");
        }
    }
}
