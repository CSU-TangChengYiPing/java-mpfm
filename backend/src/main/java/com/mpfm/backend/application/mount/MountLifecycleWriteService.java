package com.mpfm.backend.application.mount;

import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.application.share.v5.ShareAuthorizationV5Service;
import com.mpfm.backend.application.driver.base.DriverContext;
import com.mpfm.backend.application.driver.base.DriverFactory;
import com.mpfm.backend.application.driver.sftp.SftpDriverUtil;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.repository.MountRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 挂载写入协作器，负责状态迁移与本地目录治理。 */
@Component
class MountLifecycleWriteService {
    private static final Logger log = LoggerFactory.getLogger(MountLifecycleWriteService.class);
    private static final String LOCAL_TYPE = "local";
    private static final String SFTP_TYPE = "sftp";
    private static final String WEBDAV_TYPE = "webdav";
    private static final String STATE_ENABLED = "enabled";
    private static final String STATE_DISABLED = "disabled";
    private static final String STATE_SOFT_DELETED = "soft_deleted";
    private static final String STATE_PURGED = "purged";

    private final MountRepository mountRepository;
    private final MountLifecycleReadService readService;
    private final SecurityEventLogger securityEventLogger;
    private final Path localRootBase;
    private final DriverFactory driverFactory;
    private final ShareAuthorizationV5Service shareAuthorizationV5Service;

    MountLifecycleWriteService(MountRepository mountRepository,
                               MountLifecycleReadService readService,
                               SecurityEventLogger securityEventLogger,
                               DriverFactory driverFactory,
                               ShareAuthorizationV5Service shareAuthorizationV5Service,
                               @Value("${mpfm.local.base-path:./data/local}") String localBasePath) {
        this.mountRepository = mountRepository;
        this.readService = readService;
        this.securityEventLogger = securityEventLogger;
        this.driverFactory = driverFactory;
        this.shareAuthorizationV5Service = shareAuthorizationV5Service;
        this.localRootBase = Path.of(localBasePath).toAbsolutePath().normalize();
    }

    MountApplicationService.MountResult createMount(String username, String name, String protocol, boolean enabled, boolean sharedEnabled,
                                                    String host, Integer port, String mountUsername, String password, String remoteRoot, String localRoot) {
        UserEntity user = readService.loadUser(username);
        validateMountName(name);
        String mountName = name.trim();
        String mountProtocol = normalizeProtocol(protocol);
        mountRepository.findByNameAndStateNot(mountName, STATE_SOFT_DELETED)
                .ifPresent(item -> { throw new BusinessException(ErrorCode.CONFLICT, "mount name already exists"); });
        String physicalRoot = buildPhysicalRoot(mountProtocol, username, mountName, host, port, mountUsername, password, remoteRoot, localRoot);
        MountEntity mount = new MountEntity();
        mount.setId(UUID.randomUUID());
        mount.setOwnerId(user.getId());
        mount.setType(mountProtocol);
        mount.setName(mountName);
        mount.setVirtualPath("./personal/" + mountName);
        mount.setPhysicalRoot(physicalRoot);
        mount.setState(enabled ? STATE_ENABLED : STATE_DISABLED);
        mount.setSharedEnabled(sharedEnabled);
        mount.setCapacityBytes(null);
        mount.setCreatedAt(OffsetDateTime.now());
        mount.setUpdatedAt(OffsetDateTime.now());
        assertMountAvailable(user.getUsername(), mount);
        mountRepository.save(mount);
        shareAuthorizationV5Service.ensureSystemTemplatesForMount(mount.getId());
        securityEventLogger.managementOperation(new SecurityEventLogger.ManagementAuditEvent("mount_create", mount.getId().toString(), "success", null));
        return readService.toResult(mount, user);
    }

    MountApplicationService.ConnectionCheckResult testConnection(String username, String protocol,
                                                                 String host, Integer port, String mountUsername, String password, String remoteRoot, String localRoot) {
        String normalizedProtocol = normalizeProtocol(protocol);
        String physicalRoot = buildPhysicalRoot(normalizedProtocol, username, "connection-check", host, port, mountUsername, password, remoteRoot, localRoot);
        MountEntity probe = new MountEntity();
        probe.setId(UUID.randomUUID());
        probe.setType(normalizedProtocol);
        probe.setPhysicalRoot(physicalRoot);
        assertMountAvailable(username, probe);
        return new MountApplicationService.ConnectionCheckResult(normalizedProtocol, "available", "connection ok");
    }

    MountApplicationService.MountResult updateMount(String username, UUID mountId, String name, boolean sharedEnabled,
                                                    String host, Integer port, String mountUsername, String password, String remoteRoot) {
        MountEntity mount = readService.requireOwnedOrAdminMount(username, mountId);
        ensureEditable(mount, "mount deleted", "mount purged");
        validateMountName(name);
        String mountName = name.trim();
        mountRepository.findByNameAndStateNot(mountName, STATE_SOFT_DELETED)
                .filter(item -> !item.getId().equals(mountId))
                .ifPresent(item -> { throw new BusinessException(ErrorCode.CONFLICT, "mount name already exists"); });
        mount.setName(mountName);
        mount.setVirtualPath("./personal/" + mountName);
        mount.setSharedEnabled(sharedEnabled);
        if (!LOCAL_TYPE.equalsIgnoreCase(mount.getType())) {
            mount.setPhysicalRoot(mergeRemotePhysicalRoot(mount, host, port, mountUsername, password, remoteRoot));
            invalidateSftpConnection(username, mount);
            if (STATE_ENABLED.equalsIgnoreCase(mount.getState())) {
                assertMountAvailable(username, mount);
            }
        }
        mount.setUpdatedAt(OffsetDateTime.now());
        mountRepository.save(mount);
        return readService.toResult(mount, readService.loadUser(username));
    }

    MountApplicationService.MountResult enable(String username, UUID mountId) {
        MountEntity mount = readService.requireOwnedOrAdminMount(username, mountId);
        ensureEditable(mount, "mount cannot be enabled", "mount cannot be enabled");
        if (STATE_ENABLED.equals(mount.getState())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "mount already enabled");
        }
        assertMountAvailable(username, mount);
        mount.setState(STATE_ENABLED);
        mount.setUpdatedAt(OffsetDateTime.now());
        mountRepository.save(mount);
        return readService.toResult(mount, readService.loadUser(username));
    }

    private String normalizeProtocol(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "protocol required");
        }
        String value = protocol.trim().toLowerCase(Locale.ROOT);
        if (!LOCAL_TYPE.equals(value) && !SFTP_TYPE.equals(value) && !WEBDAV_TYPE.equals(value)) {
            throw new BusinessException(ErrorCode.CAPABILITY_NOT_SUPPORTED, "unsupported mount protocol");
        }
        return value;
    }

    private String buildPhysicalRoot(String protocol, String owner, String mountName, String host, Integer port, String mountUsername,
                                     String password, String remoteRoot, String localRoot) {
        if (LOCAL_TYPE.equals(protocol)) {
            Path physicalRoot;
            if (localRoot != null && !localRoot.isBlank()) {
                physicalRoot = Path.of(localRoot.trim()).toAbsolutePath().normalize();
            } else {
                physicalRoot = localRootBase.resolve(sanitize(owner)).resolve(sanitize(mountName)).normalize();
            }
            try {
                Files.createDirectories(physicalRoot);
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to create mount root", ex);
            }
            return physicalRoot.toString();
        }
        if (host == null || host.isBlank() || mountUsername == null || mountUsername.isBlank()
                || password == null || password.isBlank() || remoteRoot == null || remoteRoot.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "remote mount config required");
        }
        RemoteEndpoint endpoint = normalizeRemoteEndpoint(protocol, host, port, remoteRoot);
        int resolvedPort = endpoint.port();
        String normalizedRoot = endpoint.rootPath();
        String schema = endpoint.scheme();
        String encodedUser = URLEncoder.encode(mountUsername, StandardCharsets.UTF_8);
        String encodedPassword = URLEncoder.encode(password, StandardCharsets.UTF_8);
        return schema + "://" + encodedUser + ":" + encodedPassword + "@" + endpoint.host() + ":" + resolvedPort + normalizedRoot;
    }

    private int defaultPortOf(String protocol) {
        if (SFTP_TYPE.equals(protocol)) {
            return 22;
        }
        return 443;
    }

    private String mergeRemotePhysicalRoot(MountEntity mount, String host, Integer port, String mountUsername, String password, String remoteRoot) {
        try {
            URI original = URI.create(mount.getPhysicalRoot());
            String protocol = normalizeProtocol(mount.getType());
            RemoteEndpoint endpoint = normalizeRemoteEndpoint(
                    protocol,
                    host == null || host.isBlank() ? original.getHost() : host,
                    port == null ? (original.getPort() > 0 ? original.getPort() : null) : port,
                    remoteRoot == null || remoteRoot.isBlank() ? (original.getPath() == null || original.getPath().isBlank() ? "/" : original.getPath()) : remoteRoot
            );
            String mergedHost = endpoint.host();
            int mergedPort = endpoint.port();
            String existingUser = extractUserPart(original, true);
            String existingPass = extractUserPart(original, false);
            String mergedUser = mountUsername == null || mountUsername.isBlank() ? existingUser : mountUsername.trim();
            String mergedPass = password == null || password.isBlank() ? existingPass : password;
            String mergedRoot = endpoint.rootPath();
            if (mergedHost == null || mergedHost.isBlank() || mergedUser == null || mergedUser.isBlank()
                    || mergedPass == null || mergedPass.isBlank() || mergedRoot.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "remote mount config required");
            }
            String schema = endpoint.scheme();
            String encodedUser = URLEncoder.encode(mergedUser, StandardCharsets.UTF_8);
            String encodedPassword = URLEncoder.encode(mergedPass, StandardCharsets.UTF_8);
            return schema + "://" + encodedUser + ":" + encodedPassword + "@" + mergedHost + ":" + mergedPort + mergedRoot;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid remote mount uri", ex);
        }
    }

    private RemoteEndpoint normalizeRemoteEndpoint(String protocol, String host, Integer port, String remoteRoot) {
        String trimmedHost = host == null ? "" : host.trim();
        String explicitScheme = null;
        String normalizedHost = trimmedHost;
        String rootFromHost = null;
        Integer portFromHost = null;
        if (trimmedHost.startsWith("http://") || trimmedHost.startsWith("https://") || trimmedHost.startsWith("sftp://")) {
            URI parsed = URI.create(trimmedHost);
            if (parsed.getHost() == null || parsed.getHost().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid remote host");
            }
            explicitScheme = parsed.getScheme();
            normalizedHost = parsed.getHost();
            if (parsed.getPort() > 0) {
                portFromHost = parsed.getPort();
            }
            if (parsed.getPath() != null && !parsed.getPath().isBlank() && !"/".equals(parsed.getPath())) {
                rootFromHost = parsed.getPath();
            }
        }
        String normalizedRoot = remoteRoot == null || remoteRoot.isBlank()
                ? (rootFromHost == null ? "/" : rootFromHost)
                : (remoteRoot.startsWith("/") ? remoteRoot : "/" + remoteRoot);
        int normalizedPort = port != null ? port : (portFromHost != null ? portFromHost : defaultPortOf(protocol));
        String normalizedScheme;
        if (SFTP_TYPE.equals(protocol)) {
            normalizedScheme = "sftp";
        } else {
            normalizedScheme = explicitScheme != null ? explicitScheme.toLowerCase(Locale.ROOT) : "https";
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                normalizedScheme = "https";
            }
        }
        return new RemoteEndpoint(normalizedScheme, normalizedHost, normalizedPort, normalizedRoot);
    }

    private record RemoteEndpoint(String scheme, String host, int port, String rootPath) {
    }

    private String extractUserPart(URI uri, boolean usernamePart) {
        String userInfo = uri.getUserInfo();
        if (userInfo == null || !userInfo.contains(":")) {
            return "";
        }
        String[] pair = userInfo.split(":", 2);
        return usernamePart ? pair[0] : pair[1];
    }

    private void assertMountAvailable(String username, MountEntity mount) {
        if (LOCAL_TYPE.equalsIgnoreCase(mount.getType())) {
            Path root = Path.of(mount.getPhysicalRoot());
            if (!Files.exists(root) || !Files.isDirectory(root) || !Files.isReadable(root)) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "mount root unavailable");
            }
            return;
        }
        var driver = driverFactory.resolve(mount.getType());
        DriverContext context = new DriverContext(username, mount);
        driver.init(context);
        driver.list(context, ".");
    }

    MountApplicationService.MountResult disable(String username, UUID mountId) {
        MountEntity mount = readService.requireOwnedOrAdminMount(username, mountId);
        ensureEditable(mount, "mount cannot be disabled", "mount cannot be disabled");
        if (STATE_DISABLED.equals(mount.getState())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "mount already disabled");
        }
        mount.setState(STATE_DISABLED);
        mount.setUpdatedAt(OffsetDateTime.now());
        mountRepository.save(mount);
        invalidateSftpConnection(username, mount);
        return readService.toResult(mount, readService.loadUser(username));
    }

    MountApplicationService.MountResult softDelete(String username, UUID mountId) {
        MountEntity mount = readService.requireOwnedOrAdminMount(username, mountId);
        if (STATE_SOFT_DELETED.equals(mount.getState()) || STATE_PURGED.equals(mount.getState())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "mount already deleted");
        }
        cleanupLocalDirtyData(mount);
        mount.setState(STATE_SOFT_DELETED);
        mount.setDeletedAt(OffsetDateTime.now());
        mount.setUpdatedAt(OffsetDateTime.now());
        mountRepository.save(mount);
        invalidateSftpConnection(username, mount);
        return readService.toResult(mount, readService.loadUser(username));
    }

    MountApplicationService.MountResult restore(String username, UUID mountId) {
        MountEntity mount = readService.requireOwnedOrAdminMount(username, mountId);
        if (!STATE_SOFT_DELETED.equals(mount.getState())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "mount is not soft deleted");
        }
        mount.setState(STATE_DISABLED);
        mount.setDeletedAt(null);
        mount.setUpdatedAt(OffsetDateTime.now());
        mountRepository.save(mount);
        return readService.toResult(mount, readService.loadUser(username));
    }

    private void ensureEditable(MountEntity mount, String deletedMessage, String purgedMessage) {
        if (STATE_SOFT_DELETED.equals(mount.getState())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, deletedMessage);
        }
        if (STATE_PURGED.equals(mount.getState())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, purgedMessage);
        }
    }

    private String sanitize(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-_]", "-");
    }

    private void validateMountName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "mount name required");
        }
        String normalized = name.trim();
        if (normalized.contains("..") || normalized.contains("/") || normalized.contains("\\")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid mount name");
        }
    }

    private void cleanupLocalDirtyData(MountEntity mount) {
        if (!LOCAL_TYPE.equalsIgnoreCase(mount.getType())) {
            return;
        }
        Path root = Path.of(mount.getPhysicalRoot()).toAbsolutePath().normalize();
        if (!root.startsWith(localRootBase)) {
            if (log.isWarnEnabled()) {
                log.warn("skip local cleanup: root out of managed base path, mountId={}, root={}, base={}",
                        mount.getId(), root, localRootBase);
            }
            return;
        }
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ex) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to cleanup local mount data", ex);
                }
            });
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to cleanup local mount data", ex);
        }
    }

    private void invalidateSftpConnection(String username, MountEntity mount) {
        if (!SFTP_TYPE.equalsIgnoreCase(mount.getType())) {
            return;
        }
        SftpDriverUtil.invalidate(new DriverContext(username, mount));
    }
}
