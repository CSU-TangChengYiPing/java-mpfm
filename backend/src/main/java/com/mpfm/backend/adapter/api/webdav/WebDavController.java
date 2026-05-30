package com.mpfm.backend.adapter.api.webdav;

import com.mpfm.backend.adapter.api.file.FileQueryController;
import com.mpfm.backend.application.file.FileApplicationService;
import com.mpfm.backend.application.mount.MountApplicationService;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * WebDAV 对外协议入口控制器：统一分发协议方法并复用既有文件应用服务。
 */
@RestController
@RequestMapping("/dav/**")
public class WebDavController {
    private static final String DAV_PREFIX = "/dav";
    private static final String HEADER_IF_MATCH = "If-Match";
    private static final String HEADER_IF_NONE_MATCH = "If-None-Match";
    private static final String HEADER_DESTINATION = "Destination";
    private static final String HEADER_OVERWRITE = "Overwrite";
    private static final String HEADER_RETRY_AFTER = "Retry-After";
    private static final String SHARED_ALIAS_DELIMITER = "---";
    private static final Set<String> NOISE_PROPFIND_NAMES = Set.of(
            "folder.jpg", "folder.gif", "desktop.ini", "thumbs.db", "autorun.inf");
    private static final Set<String> NOISE_TRAVERSAL_SEGMENTS = Set.of(
            "$recycle.bin", "recycler", "system volume information", "config.msi");

    private final FileApplicationService fileApplicationService;
    private final FileQueryController fileQueryController;
    private final MountApplicationService mountApplicationService;
    private final WebDavCacheService webDavCacheService;

    public WebDavController(FileApplicationService fileApplicationService,
                            FileQueryController fileQueryController,
                            MountApplicationService mountApplicationService,
                            WebDavCacheService webDavCacheService) {
        this.fileApplicationService = fileApplicationService;
        this.fileQueryController = fileQueryController;
        this.mountApplicationService = mountApplicationService;
        this.webDavCacheService = webDavCacheService;
    }

    /**
     * WebDAV 方法统一入口：按请求方法分发到对应业务动作。
     */
    @RequestMapping
    public ResponseEntity<?> dispatch(HttpServletRequest request,
                                      Principal principal) {
        String method = request.getMethod();
        if ("PROPFIND".equals(method)) {
            if (isNoiseProbeUri(request.getRequestURI()) || isNoiseTraversalUri(request.getRequestURI())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        }
        String virtualPath = resolveVirtualPath(request);
        if ("PROPFIND".equals(method) && isNoiseProbePath(virtualPath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return switch (method) {
            case "OPTIONS" -> handleOptions();
            case "PROPFIND" -> handlePropfind(request, principal, virtualPath);
            case "HEAD" -> handleHead(principal, virtualPath);
            case "PUT" -> handlePut(request, principal, virtualPath);
            case "MKCOL" -> handleMkcol(request, principal, virtualPath);
            case "DELETE" -> handleDelete(request, principal, virtualPath);
            case "MOVE" -> handleMove(request, principal, virtualPath);
            case "COPY" -> handleCopy(request, principal, virtualPath);
            case "LOCK", "UNLOCK", "PROPPATCH" -> capabilityNotSupported(HttpStatus.METHOD_NOT_ALLOWED,
                    "webdav method not supported in current phase");
            default -> capabilityNotSupported(HttpStatus.NOT_IMPLEMENTED, "unsupported webdav method");
        };
    }

    /**
     * GET 单独声明为 StreamingResponseBody 返回类型，避免被 ResponseEntity<?> 降级到消息转换器分支。
     */
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<StreamingResponseBody> get(HttpServletRequest request, Principal principal) {
        String virtualPath = resolveVirtualPath(request);
        return handleGet(request, principal, virtualPath);
    }

    private ResponseEntity<String> handleOptions() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("DAV", "1,2");
        headers.add("Allow", "OPTIONS, PROPFIND, GET, HEAD, PUT, MKCOL, DELETE, MOVE, COPY");
        headers.add("MS-Author-Via", "DAV");
        return new ResponseEntity<>("", headers, HttpStatus.OK);
    }

    private ResponseEntity<String> handlePropfind(HttpServletRequest request, Principal principal, String virtualPath) {
        String depthHeader = request.getHeader("Depth");
        int depth = parseDepth(depthHeader);
        if (webDavCacheService.shouldRateLimitPropfind(principal.getName(), virtualPath, depth)) {
            String hotCached = webDavCacheService.tryServeHotPropfindCache(principal.getName(), virtualPath, depth, "all");
            if (hotCached != null) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_XML);
                headers.add("DAV", "1,2");
                headers.add("MS-Author-Via", "DAV");
                headers.add("X-MPFM-Propfind-Cache", "hot");
                headers.add("X-MPFM-RateLimit", "1");
                return new ResponseEntity<>(hotCached, headers, HttpStatus.MULTI_STATUS);
            }
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HEADER_RETRY_AFTER, "1")
                    .build();
        }
        PropfindRequest propfindRequest = parsePropfindRequest(request);
        String requestShape = propfindRequest.shape();
        String hotCached = webDavCacheService.tryServeHotPropfindCache(principal.getName(), virtualPath, depth, requestShape);
        if (hotCached != null) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.add("DAV", "1,2");
            headers.add("MS-Author-Via", "DAV");
            headers.add("X-MPFM-Propfind-Cache", "hot");
            return new ResponseEntity<>(hotCached, headers, HttpStatus.MULTI_STATUS);
        }
        String xml = webDavCacheService.getOrLoadPropfindXml(principal.getName(), virtualPath, depth, requestShape, () -> {
            List<FileApplicationService.EntryResult> entries = resolvePropfindEntries(principal.getName(), virtualPath, depth);
            return buildMultiStatusXml(entries, propfindRequest);
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.add("DAV", "1,2");
        headers.add("MS-Author-Via", "DAV");
        return new ResponseEntity<>(xml, headers, HttpStatus.MULTI_STATUS);
    }

    private ResponseEntity<StreamingResponseBody> handleGet(HttpServletRequest request,
                                                            Principal principal,
                                                            String virtualPath) {
        FileApplicationService.EntryResult stat = webDavCacheService.getOrLoadStat(
                principal.getName(),
                virtualPath,
                () -> fileApplicationService.stat(principal.getName(), virtualPath));
        if ("directory".equalsIgnoreCase(stat.type())) {
            throw new BusinessException(ErrorCode.CAPABILITY_NOT_SUPPORTED, "directory get is not supported");
        }
        return fileQueryController.download(virtualPath, request, principal);
    }

    private ResponseEntity<?> handleHead(Principal principal, String virtualPath) {
        FileApplicationService.EntryResult stat = webDavCacheService.getOrLoadStat(
                principal.getName(),
                virtualPath,
                () -> fileApplicationService.stat(principal.getName(), virtualPath));
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(stat.etag());
        headers.set(HttpHeaders.LAST_MODIFIED, toRfc1123(stat.mtime()));
        headers.setContentLength(Math.max(0L, stat.sizeBytes()));
        if ("directory".equalsIgnoreCase(stat.type())) {
            headers.setContentType(MediaType.parseMediaType("httpd/unix-directory"));
            headers.setContentLength(0L);
        } else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        return ResponseEntity.ok().headers(headers).build();
    }

    private ResponseEntity<?> handlePut(HttpServletRequest request,
                                        Principal principal,
                                        String virtualPath) {
        String ifMatch = resolveWritePrecondition(request);
        byte[] content = readRequestBytes(request);
        FileApplicationService.EntryResult result =
                fileApplicationService.writeFileBytes(principal.getName(), virtualPath, content, ifMatch);
        webDavCacheService.evictUser(principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.ETAG, result.etag())
                .build();
    }

    private ResponseEntity<?> handleMkcol(HttpServletRequest request, Principal principal, String virtualPath) {
        fileApplicationService.mkdir(principal.getName(), virtualPath, resolveWritePrecondition(request));
        webDavCacheService.evictUser(principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private ResponseEntity<?> handleDelete(HttpServletRequest request, Principal principal, String virtualPath) {
        fileApplicationService.delete(principal.getName(), virtualPath, resolveWritePrecondition(request));
        webDavCacheService.evictUser(principal.getName());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<?> handleMove(HttpServletRequest request, Principal principal, String sourceVirtualPath) {
        String destinationHeader = request.getHeader(HEADER_DESTINATION);
        if (destinationHeader == null || destinationHeader.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Destination header is required");
        }
        String targetVirtualPath = resolveVirtualPathFromDestination(destinationHeader);
        String ifMatch = resolveWritePrecondition(request);
        ensureOverwritePolicy(principal, targetVirtualPath, request.getHeader(HEADER_OVERWRITE));
        fileApplicationService.move(principal.getName(), sourceVirtualPath, targetVirtualPath, ifMatch);
        webDavCacheService.evictUser(principal.getName());
        String overwrite = request.getHeader(HEADER_OVERWRITE);
        return ResponseEntity.status("F".equalsIgnoreCase(overwrite) ? HttpStatus.CREATED : HttpStatus.NO_CONTENT).build();
    }

    private ResponseEntity<?> handleCopy(HttpServletRequest request, Principal principal, String sourceVirtualPath) {
        String destinationHeader = request.getHeader(HEADER_DESTINATION);
        if (destinationHeader == null || destinationHeader.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Destination header is required");
        }
        String targetVirtualPath = resolveVirtualPathFromDestination(destinationHeader);
        String ifMatch = resolveWritePrecondition(request);
        ensureOverwritePolicy(principal, targetVirtualPath, request.getHeader(HEADER_OVERWRITE));
        fileApplicationService.copy(principal.getName(), sourceVirtualPath, targetVirtualPath, ifMatch);
        webDavCacheService.evictUser(principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private void ensureOverwritePolicy(Principal principal, String targetVirtualPath, String overwriteHeader) {
        if (!"F".equalsIgnoreCase(overwriteHeader)) {
            return;
        }
        try {
            fileApplicationService.stat(principal.getName(), targetVirtualPath);
            throw new BusinessException(ErrorCode.CONFLICT, "destination exists and overwrite is false");
        } catch (BusinessException ex) {
            if (ex.getCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                return;
            }
            throw ex;
        }
    }

    String resolveVirtualPath(HttpServletRequest request) {
        return resolveVirtualPathFromUri(request.getRequestURI());
    }

    private String resolveVirtualPathFromUri(String uri) {
        int index = uri.indexOf(DAV_PREFIX);
        if (index < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid dav path");
        }
        String path = decodePath(uri.substring(index + DAV_PREFIX.length()));
        if (path.isBlank() || "/".equals(path)) {
            return "/";
        }
        String normalized = path.replace('\\', '/').replaceAll("/{2,}", "/");
        if (normalized.contains("..")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid path");
        }
        String withoutPrefix = normalized.startsWith("/") ? normalized.substring(1) : normalized;
        String[] parts = withoutPrefix.split("/");
        String namespace = parts[0];
        if (!"personal".equals(namespace) && !"shared".equals(namespace)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid namespace");
        }
        if (parts.length < 2) {
            return "/" + namespace;
        }
        return "/" + withoutPrefix;
    }

    String resolveVirtualPathFromDestination(String destination) {
        try {
            URI uri = URI.create(destination);
            String rawPath = uri.getPath();
            if (rawPath == null || rawPath.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid destination");
            }
            return resolveVirtualPathFromRaw(rawPath);
        } catch (IllegalArgumentException ex) {
            return resolveVirtualPathFromRaw(destination);
        }
    }

    private String resolveVirtualPathFromRaw(String rawPath) {
        String normalizedRaw = rawPath.startsWith(DAV_PREFIX) ? rawPath : DAV_PREFIX + (rawPath.startsWith("/") ? rawPath : "/" + rawPath);
        return resolveVirtualPathFromUri(normalizedRaw);
    }

    private int parseDepth(String value) {
        if (value == null || value.isBlank()) {
            return 1;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "0" -> 0;
            case "1", "infinity", "1,noroot", "infinity,noroot" -> 1;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid Depth header");
        };
    }

    private List<FileApplicationService.EntryResult> resolvePropfindEntries(String username, String virtualPath, int depth) {
        if ("/".equals(virtualPath)) {
            return resolveDavRootEntries(depth);
        }
        if ("/personal".equals(virtualPath) || "/shared".equals(virtualPath)) {
            return resolveNamespaceEntries(username, virtualPath, depth);
        }
        FileApplicationService.EntryResult self = webDavCacheService.getOrLoadStat(
                username,
                virtualPath,
                () -> fileApplicationService.stat(username, virtualPath));
        List<FileApplicationService.EntryResult> entries = new ArrayList<>();
        entries.add(self);
        if (depth == 1 && "directory".equalsIgnoreCase(self.type())) {
            entries.addAll(loadChildrenWithDegrade(username, virtualPath));
        }
        return entries;
    }

    /**
     * PROPFIND 子项查询降级策略：当目录子项加载失败时仅返回当前目录节点，避免整请求失败。
     */
    private List<FileApplicationService.EntryResult> loadChildrenWithDegrade(String username, String virtualPath) {
        try {
            return webDavCacheService.getOrLoadList(
                    username,
                    virtualPath,
                    () -> fileApplicationService.list(username, virtualPath));
        } catch (BusinessException ex) {
            return List.of();
        }
    }

    private List<FileApplicationService.EntryResult> resolveNamespaceEntries(String username, String virtualPath, int depth) {
        String namespace = virtualPath.substring(1);
        String now = OffsetDateTime.now().toString();
        List<FileApplicationService.EntryResult> entries = new ArrayList<>();
        entries.add(new FileApplicationService.EntryResult(
                virtualPath, namespace, "directory", 0L, now, null, true, true, true, "\"ns-" + namespace + "\"", "ns-" + namespace));
        if (depth == 0) {
            return entries;
        }
        boolean shared = "shared".equals(namespace);
        for (MountApplicationService.MountResult mount : mountApplicationService.listMyMounts(username)) {
            if (!"enabled".equalsIgnoreCase(mount.state())) {
                continue;
            }
            if (shared && !mount.sharedEnabled()) {
                continue;
            }
            if (shared) {
                // 与 Web 门户保持一致：shared 只展示“他人共享给我”的挂载，不展示可管理的自有挂载。
                if (mount.canManage()) {
                    continue;
                }
            }
            String sharedRef = shared
                    ? toSharedAlias(mount.name(), mount.ownerUser())
                    : mount.name();
            String displayName = shared
                    ? mount.name() + "(" + (mount.ownerUser() == null || mount.ownerUser().isBlank() ? "-" : mount.ownerUser()) + ")"
                    : mount.name();
            String childPath = "/" + namespace + "/" + sharedRef;
            entries.add(new FileApplicationService.EntryResult(
                    childPath, displayName, "directory", 0L, now, null, true, true, true,
                    "\"mount-" + mount.mountId() + "\"", "mount-" + mount.mountId()));
        }
        return entries;
    }

    private String toSharedAlias(String mountName, String ownerUser) {
        String safeMountName = (mountName == null || mountName.isBlank()) ? "-" : mountName.trim();
        String safeOwnerUser = (ownerUser == null || ownerUser.isBlank()) ? "-" : ownerUser.trim();
        return safeMountName + SHARED_ALIAS_DELIMITER + safeOwnerUser;
    }

    private List<FileApplicationService.EntryResult> resolveDavRootEntries(int depth) {
        String now = OffsetDateTime.now().toString();
        List<FileApplicationService.EntryResult> entries = new ArrayList<>();
        entries.add(new FileApplicationService.EntryResult(
                "/", "dav", "directory", 0L, now, null, true, true, true, "\"dav-root\"", "dav-root"));
        if (depth == 0) {
            return entries;
        }
        entries.add(new FileApplicationService.EntryResult(
                "/personal", "personal", "directory", 0L, now, null, true, true, true, "\"dav-personal\"", "dav-personal"));
        entries.add(new FileApplicationService.EntryResult(
                "/shared", "shared", "directory", 0L, now, null, true, true, true, "\"dav-shared\"", "dav-shared"));
        return entries;
    }

    private byte[] readRequestBytes(HttpServletRequest request) {
        try {
            byte[] body = request.getInputStream().readAllBytes();
            return body.length == 0 ? new byte[0] : body;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid request body", ex);
        }
    }

    private String resolveWritePrecondition(HttpServletRequest request) {
        String ifMatch = request.getHeader(HEADER_IF_MATCH);
        if (ifMatch != null && !ifMatch.isBlank()) {
            return ifMatch;
        }
        String ifNoneMatch = request.getHeader(HEADER_IF_NONE_MATCH);
        if ("*".equals(ifNoneMatch)) {
            return "*";
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "If-Match or If-None-Match is required");
    }

    private String buildMultiStatusXml(List<FileApplicationService.EntryResult> entries, PropfindRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<d:multistatus xmlns:d=\"DAV:\">");
        for (FileApplicationService.EntryResult entry : entries) {
            sb.append("<d:response>");
            sb.append("<d:href>").append(xmlEscape(toDavHref(entry.path(), "directory".equalsIgnoreCase(entry.type())))).append("</d:href>");
            sb.append("<d:propstat><d:prop>");
            appendPropElement(sb, "displayname", entry.name(), request);
            appendPropElement(sb, "getlastmodified", toRfc1123(entry.mtime()), request);
            appendPropElement(sb, "getetag", entry.etag(), request);
            appendResourceType(sb, "directory".equalsIgnoreCase(entry.type()), request);
            appendPropElement(sb, "getcontentlength",
                    "directory".equalsIgnoreCase(entry.type()) ? "0" : String.valueOf(entry.sizeBytes()), request);
            sb.append("</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>");
            sb.append("</d:response>");
        }
        sb.append("</d:multistatus>");
        return sb.toString();
    }

    private void appendPropElement(StringBuilder sb, String prop, String value, PropfindRequest request) {
        if (!request.shouldInclude(prop)) {
            return;
        }
        if (request.propNameOnly()) {
            sb.append("<d:").append(prop).append("/>");
            return;
        }
        sb.append("<d:").append(prop).append(">").append(xmlEscape(value)).append("</d:").append(prop).append(">");
    }

    private void appendResourceType(StringBuilder sb, boolean directory, PropfindRequest request) {
        if (!request.shouldInclude("resourcetype")) {
            return;
        }
        if (request.propNameOnly()) {
            sb.append("<d:resourcetype/>");
            return;
        }
        if (directory) {
            sb.append("<d:resourcetype><d:collection/></d:resourcetype>");
        } else {
            sb.append("<d:resourcetype/>");
        }
    }

    private PropfindRequest parsePropfindRequest(HttpServletRequest request) {
        try {
            byte[] body = request.getInputStream().readAllBytes();
            if (body.length == 0) {
                return PropfindRequest.ofAll();
            }
            String xml = new String(body, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (xml.contains("<d:propname") || xml.contains("<propname")) {
                return PropfindRequest.ofPropName();
            }
            if (!xml.contains("<d:prop") && !xml.contains("<prop")) {
                return PropfindRequest.ofAll();
            }
            Set<String> requested = new HashSet<>();
            collectProp(xml, "displayname", requested);
            collectProp(xml, "getlastmodified", requested);
            collectProp(xml, "getetag", requested);
            collectProp(xml, "resourcetype", requested);
            collectProp(xml, "getcontentlength", requested);
            return requested.isEmpty() ? PropfindRequest.ofAll() : PropfindRequest.ofSelected(requested);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid PROPFIND body", ex);
        }
    }

    private void collectProp(String xml, String name, Set<String> bucket) {
        if (xml.contains("<d:" + name) || xml.contains("<" + name)) {
            bucket.add(name);
        }
    }

    private String toDavHref(String virtualPath, boolean directory) {
        String path = virtualPath == null ? "/" : virtualPath;
        String normalized = path.startsWith("/") ? path : "/" + path;
        if (normalized.startsWith("/./")) {
            normalized = normalized.substring(2);
        }
        if (directory && !normalized.endsWith("/")) {
            normalized += "/";
        }
        return DAV_PREFIX + normalized;
    }

    private ResponseEntity<ErrorResponse> capabilityNotSupported(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(ErrorCode.CAPABILITY_NOT_SUPPORTED, message, ""));
    }

    private String toRfc1123(String iso) {
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME.format(OffsetDateTime.parse(iso));
        } catch (Exception ex) {
            return DateTimeFormatter.RFC_1123_DATE_TIME.format(OffsetDateTime.now());
        }
    }

    private String decodePath(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String xmlEscape(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Windows 客户端会高频探测这些文件名；对 PROPFIND 直接短路 404，避免触发后续重链路查询。
     */
    private boolean isNoiseProbePath(String virtualPath) {
        if (virtualPath == null || "/".equals(virtualPath)) {
            return false;
        }
        String[] segments = Arrays.stream(virtualPath.split("/"))
                .filter(part -> part != null && !part.isBlank())
                .toArray(String[]::new);
        if (segments.length == 0) {
            return false;
        }
        String fileName = segments[segments.length - 1].toLowerCase(Locale.ROOT);
        return NOISE_PROPFIND_NAMES.contains(fileName);
    }

    /**
     * 噪声探测前置短路：在 URI 原文阶段直接识别，避免进入命名空间校验异常链。
     */
    private boolean isNoiseProbeUri(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return false;
        }
        String decoded = decodePath(requestUri).replace('\\', '/');
        String[] segments = Arrays.stream(decoded.split("/"))
                .filter(part -> part != null && !part.isBlank())
                .toArray(String[]::new);
        if (segments.length == 0) {
            return false;
        }
        String fileName = segments[segments.length - 1].toLowerCase(Locale.ROOT);
        return NOISE_PROPFIND_NAMES.contains(fileName);
    }

    /**
     * 扫描路径短路：对系统探测目录直接返回 404，避免进入协议解析与后端查询链路。
     */
    private boolean isNoiseTraversalUri(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return false;
        }
        String decoded = decodePath(requestUri).replace('\\', '/').toLowerCase(Locale.ROOT);
        String[] segments = Arrays.stream(decoded.split("/"))
                .filter(part -> part != null && !part.isBlank())
                .toArray(String[]::new);
        for (String segment : segments) {
            if (NOISE_TRAVERSAL_SEGMENTS.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    private record PropfindRequest(boolean allProp, boolean propNameOnly, Set<String> selectedProps) {
        static PropfindRequest ofAll() {
            return new PropfindRequest(true, false, Set.of());
        }

        static PropfindRequest ofPropName() {
            return new PropfindRequest(false, true, Set.of());
        }

        static PropfindRequest ofSelected(Set<String> selectedProps) {
            return new PropfindRequest(false, false, Set.copyOf(selectedProps));
        }

        boolean shouldInclude(String prop) {
            if (allProp || propNameOnly) {
                return true;
            }
            return selectedProps.contains(prop);
        }

        String shape() {
            if (allProp) {
                return "all";
            }
            if (propNameOnly) {
                return "propname";
            }
            return "selected:" + String.join(",", selectedProps.stream().sorted().toList());
        }
    }

}
