package com.mpfm.backend.application.driver.sftp;

import com.mpfm.backend.application.driver.base.DriverContext;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import io.micrometer.core.instrument.Metrics;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SFTP 驱动工具：负责连接建立、路径归一与异常归一化。
 */
public final class SftpDriverUtil {
    private static final Logger log = LoggerFactory.getLogger(SftpDriverUtil.class);
    private static final java.util.concurrent.ConcurrentHashMap<String, AtomicLong> INSECURE_WARN_TS = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile String hostKeyMode = "insecure";
    private static volatile String knownHostsPath = "";
    private static volatile String pinnedFingerprint = "";
    private static volatile Set<String> knownFingerprints = Set.of();
    private static final ThreadLocal<String> LAST_HOSTKEY_FAILURE = new ThreadLocal<>();
    private static final long INSECURE_WARN_WINDOW_MS = TimeUnit.MINUTES.toMillis(5);
    private static final ConcurrentHashMap<String, CachedConnection> CONNECTION_CACHE = new ConcurrentHashMap<>();

    private SftpDriverUtil() {
    }

    public static void configureHostKeyPolicy(String mode, String knownHosts, String pinned) {
        hostKeyMode = mode == null || mode.isBlank() ? "insecure" : mode.trim().toLowerCase(Locale.ROOT);
        knownHostsPath = knownHosts == null ? "" : knownHosts.trim();
        pinnedFingerprint = pinned == null ? "" : pinned.trim();
        knownFingerprints = loadKnownFingerprints(knownHostsPath);
    }

    public static String normalizePath(String path) {
        if (path == null || path.isBlank() || ".".equals(path.trim())) {
            return ".";
        }
        String normalized = path.replace('\\', '/').trim();
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "." : normalized;
    }

    public static String join(String parent, String child) {
        String p = normalizePath(parent);
        String c = child == null ? "" : child.replace('\\', '/');
        if (p.equals(".") || p.equals("/")) {
            return c.startsWith("/") ? c : "/" + c;
        }
        return (p.endsWith("/") ? p : p + "/") + (c.startsWith("/") ? c.substring(1) : c);
    }

    public static SftpConnection open(DriverContext context) {
        String cacheKey = cacheKey(context);
        AtomicBoolean reused = new AtomicBoolean(false);
        AtomicBoolean reconnectTriggered = new AtomicBoolean(false);
        CachedConnection cached = CONNECTION_CACHE.compute(cacheKey, (key, existing) -> {
            if (isConnectionAlive(existing)) {
                reused.set(true);
                return existing;
            }
            if (existing != null) {
                reconnectTriggered.set(true);
            }
            closeConnection(existing == null ? null : existing.connection());
            return new CachedConnection(connect(context), new AtomicLong(System.currentTimeMillis()));
        });
        if (cached != null) {
            cached.lastUsedAt().set(System.currentTimeMillis());
            Metrics.counter("mpfm.sftp.connection.borrow.count",
                    "mountId", context.mount().getId().toString(),
                    "reused", String.valueOf(reused.get()),
                    "reconnectTriggered", String.valueOf(reconnectTriggered.get()))
                    .increment();
            log.info("sftp-connection-borrow mountId={} reused={} reconnectTriggered={}",
                    context.mount().getId(),
                    reused.get(),
                    reconnectTriggered.get());
            return cached.connection();
        }
        return connect(context);
    }

    private static SftpConnection connect(DriverContext context) {
        try {
            LAST_HOSTKEY_FAILURE.remove();
            ParsedSftpUri parsed = parseSftpMountUri(context.mount().getPhysicalRoot());
            String host = parsed.host();
            int port = parsed.port();
            String username = URLDecoder.decode(parsed.username(), StandardCharsets.UTF_8);
            String password = URLDecoder.decode(parsed.password(), StandardCharsets.UTF_8);
            String basePath = normalizePath(parsed.path());

            SshClient client = SshClient.setUpDefaultClient();
            client.setServerKeyVerifier((clientSession, remoteAddress, serverKey) -> verifyHostKey(serverKey));
            client.start();
            ClientSession session = client.connect(username, host, port).verify(10_000L).getSession();
            session.addPasswordIdentity(password);
            session.auth().verify(10_000L);
            SftpClient sftpClient = SftpClientFactory.instance().createSftpClient(session);
            Metrics.counter("mpfm.sftp.connection.create.count").increment();
            return new SftpConnection(client, session, sftpClient, basePath);
        } catch (BusinessException ex) {
            Metrics.counter("mpfm.sftp.connection.fail.count", "code", ex.getCode().name()).increment();
            throw ex;
        } catch (Exception ex) {
            String hostKeyFailure = LAST_HOSTKEY_FAILURE.get();
            if (hostKeyFailure != null && !hostKeyFailure.isBlank()) {
                Metrics.counter("mpfm.sftp.connection.fail.count", "code", ErrorCode.PERMISSION_DENIED.name()).increment();
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, hostKeyFailure, ex);
            }
            Metrics.counter("mpfm.sftp.connection.fail.count", "code", ErrorCode.INTERNAL_ERROR.name()).increment();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "sftp connection failed", ex);
        } finally {
            LAST_HOSTKEY_FAILURE.remove();
        }
    }

    private static boolean verifyHostKey(PublicKey serverKey) {
        String fingerprint = KeyUtils.getFingerPrint(serverKey);
        if ("insecure".equals(hostKeyMode)) {
            warnInsecureModeOnce(fingerprint);
            return true;
        }
        if ("pinned".equals(hostKeyMode)) {
            boolean ok = !pinnedFingerprint.isBlank() && pinnedFingerprint.equalsIgnoreCase(fingerprint);
            if (!ok) {
                LAST_HOSTKEY_FAILURE.set("sftp host key verify failed (mode=pinned, expected="
                        + pinnedFingerprint + ", actual=" + fingerprint + ")");
            }
            return ok;
        }
        if ("known_hosts".equals(hostKeyMode)) {
            boolean ok = knownFingerprints.contains(fingerprint.toLowerCase(Locale.ROOT));
            if (!ok) {
                LAST_HOSTKEY_FAILURE.set("sftp host key verify failed (mode=known_hosts, actual=" + fingerprint + ")");
            }
            return ok;
        }
        LAST_HOSTKEY_FAILURE.set("sftp host key verify failed (unsupported mode=" + hostKeyMode + ")");
        return false;
    }

    private static void warnInsecureModeOnce(String fingerprint) {
        long now = System.currentTimeMillis();
        AtomicLong holder = INSECURE_WARN_TS.computeIfAbsent(fingerprint, key -> new AtomicLong(0L));
        long last = holder.get();
        if (now - last < INSECURE_WARN_WINDOW_MS) {
            return;
        }
        if (holder.compareAndSet(last, now) && log.isWarnEnabled()) {
            log.warn("SFTP host key verify mode=insecure, server fingerprint={} (仅开发环境建议使用)", fingerprint);
        }
    }

    private static Set<String> loadKnownFingerprints(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return Set.of();
        }
        try {
            Set<String> set = new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);
            for (String line : Files.readAllLines(Path.of(filePath))) {
                String value = line == null ? "" : line.trim();
                if (value.isBlank() || value.startsWith("#")) {
                    continue;
                }
                set.add(value.toLowerCase(Locale.ROOT));
            }
            return Set.copyOf(set);
        } catch (Exception ex) {
            return Set.of();
        }
    }

    public static void invalidate(DriverContext context) {
        String cacheKey = cacheKey(context);
        CachedConnection removed = CONNECTION_CACHE.remove(cacheKey);
        closeConnection(removed == null ? null : removed.connection());
    }

    private static ParsedSftpUri parseSftpMountUri(String raw) {
        try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            String userInfo = uri.getUserInfo();
            if (host != null && userInfo != null && userInfo.contains(":")) {
                String[] pair = userInfo.split(":", 2);
                int port = uri.getPort() > 0 ? uri.getPort() : 22;
                String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
                return new ParsedSftpUri(host, port, pair[0], pair[1], path);
            }
        } catch (Exception ignore) {
            // 回退到手工解析，兼容历史未编码账号密码。
        }
        String value = raw == null ? "" : raw.trim();
        if (!value.startsWith("sftp://")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid sftp mount uri");
        }
        String remainder = value.substring("sftp://".length());
        int atIndex = remainder.lastIndexOf('@');
        if (atIndex <= 0 || atIndex >= remainder.length() - 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid sftp mount uri");
        }
        String credential = remainder.substring(0, atIndex);
        String hostAndPath = remainder.substring(atIndex + 1);
        int slashIndex = hostAndPath.indexOf('/');
        String hostPort = slashIndex >= 0 ? hostAndPath.substring(0, slashIndex) : hostAndPath;
        String path = slashIndex >= 0 ? hostAndPath.substring(slashIndex) : "/";
        int colonIndex = credential.indexOf(':');
        if (colonIndex <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid sftp mount uri");
        }
        String username = credential.substring(0, colonIndex);
        String password = credential.substring(colonIndex + 1);
        String host;
        int port = 22;
        int hostColon = hostPort.lastIndexOf(':');
        if (hostColon > 0 && hostColon < hostPort.length() - 1) {
            host = hostPort.substring(0, hostColon);
            try {
                port = Integer.parseInt(hostPort.substring(hostColon + 1));
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid sftp mount uri", ex);
            }
        } else {
            host = hostPort;
        }
        if (host.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid sftp mount uri");
        }
        return new ParsedSftpUri(host, port, username, password, path);
    }

    public static void closeQuietly(SftpConnection connection) {
        // 单连接复用模式下，调用方只做“释放语义”占位，不在请求结束时关闭连接。
    }

    private static void closeConnection(SftpConnection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.sftpClient().close();
        } catch (Exception ignored) {
            // no-op
        }
        try {
            connection.session().close();
        } catch (Exception ignored) {
            // no-op
        }
        try {
            connection.client().close();
        } catch (Exception ignored) {
            // no-op
        }
    }

    public record SftpConnection(SshClient client, ClientSession session, SftpClient sftpClient, String basePath) {
    }

    private static String cacheKey(DriverContext context) {
        return context.mount().getId().toString();
    }

    private static boolean isConnectionAlive(CachedConnection cached) {
        if (cached == null || cached.connection() == null) {
            return false;
        }
        SftpConnection connection = cached.connection();
        try {
            return connection.client().isStarted()
                    && connection.session().isOpen()
                    && connection.sftpClient().isOpen();
        } catch (Exception ex) {
            return false;
        }
    }

    private record CachedConnection(SftpConnection connection, AtomicLong lastUsedAt) {
    }

    private record ParsedSftpUri(String host, int port, String username, String password, String path) { }
}
