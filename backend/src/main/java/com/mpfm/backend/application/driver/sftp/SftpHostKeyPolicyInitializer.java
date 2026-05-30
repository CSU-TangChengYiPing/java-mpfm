package com.mpfm.backend.application.driver.sftp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SFTP HostKey 策略初始化器：在应用启动时把配置注入到 SFTP 工具层。
 */
@Component
public class SftpHostKeyPolicyInitializer {
    public SftpHostKeyPolicyInitializer(
            @Value("${mpfm.sftp.hostkey.mode:insecure}") String mode,
            @Value("${mpfm.sftp.hostkey.known-hosts-path:}") String knownHostsPath,
            @Value("${mpfm.sftp.hostkey.pinned-fingerprint:}") String pinnedFingerprint) {
        SftpDriverUtil.configureHostKeyPolicy(mode, knownHostsPath, pinnedFingerprint);
    }
}

