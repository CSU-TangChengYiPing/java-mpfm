package com.mpfm.backend.application.driver.sftp;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SftpDriverUtilTests {

    @Test
    void normalizePathShouldMapBlankToDotAndTrimTailSlash() {
        assertThat(SftpDriverUtil.normalizePath("")).isEqualTo(".");
        assertThat(SftpDriverUtil.normalizePath(" /a/b/ ")).isEqualTo("/a/b");
    }

    @Test
    void joinShouldCreateAbsoluteStylePathWhenParentIsDot() {
        assertThat(SftpDriverUtil.join(".", "file.txt")).isEqualTo("/file.txt");
        assertThat(SftpDriverUtil.join("/base", "child.txt")).isEqualTo("/base/child.txt");
    }

    @Test
    void closeQuietlyShouldCloseAllResources() throws Exception {
        SshClient client = Mockito.mock(SshClient.class);
        ClientSession session = Mockito.mock(ClientSession.class);
        SftpClient sftpClient = Mockito.mock(SftpClient.class);
        SftpDriverUtil.SftpConnection connection = new SftpDriverUtil.SftpConnection(client, session, sftpClient, "/");

        SftpDriverUtil.closeQuietly(connection);

        verify(sftpClient, times(1)).close();
        verify(session, times(1)).close();
        verify(client, times(1)).close();
    }
}
