package com.mpfm.backend.application.driver.webdav;

import com.mpfm.backend.application.driver.base.DriverContext;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * WebDAV 驱动工具：负责鉴权、HTTP 请求与 PROPFIND 解析。
 */
public final class WebdavDriverUtil {
    private static final String PROPFIND_BODY = "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:getetag/></d:prop></d:propfind>";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private WebdavDriverUtil() {
    }

    public static DavConnection open(DriverContext context) {
        try {
            URI root = URI.create(context.mount().getPhysicalRoot());
            if (root.getHost() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid webdav mount uri");
            }
            String userInfo = root.getUserInfo();
            String authHeader = null;
            if (userInfo != null && userInfo.contains(":")) {
                String[] pair = userInfo.split(":", 2);
                String username = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String password = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                authHeader = "Basic " + token;
            }
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            return new DavConnection(client, root, authHeader);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "webdav connection init failed", ex);
        }
    }

    public static String normalizePath(String path) {
        if (path == null || path.isBlank() || ".".equals(path.trim())) {
            return ".";
        }
        String normalized = path.replace('\\', '/').trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static String join(String parent, String child) {
        String p = normalizePath(parent);
        if (".".equals(p)) {
            p = "/";
        }
        String c = child == null ? "" : child.replace('\\', '/');
        if (c.startsWith("/")) {
            c = c.substring(1);
        }
        if (p.endsWith("/")) {
            return p + c;
        }
        return p + "/" + c;
    }

    public static URI resolve(DavConnection connection, String targetPath) {
        String normalized = normalizePath(targetPath);
        if (".".equals(normalized)) {
            normalized = "/";
        }
        StringBuilder encoded = new StringBuilder();
        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if (segment.isBlank()) {
                continue;
            }
            encoded.append('/').append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        String suffix = encoded.length() == 0 ? "/" : encoded.toString();
        String basePath = connection.rootUri().getPath();
        String normalizedBase = (basePath == null || basePath.isBlank()) ? "/" : basePath;
        if (!normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase + "/";
        }
        String relative = "/".equals(suffix) ? "" : suffix.substring(1);
        String mergedPath = normalizedBase + relative;
        URI root = connection.rootUri();
        try {
            return new URI(
                    root.getScheme(),
                    root.getUserInfo(),
                    root.getHost(),
                    root.getPort(),
                    mergedPath,
                    null,
                    null);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "webdav resolve uri failed", ex);
        }
    }

    public static HttpResponse<byte[]> send(DavConnection connection, String method, String path,
                                            String depth, byte[] body, List<Header> extraHeaders) {
        try {
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(connection, path))
                    .timeout(REQUEST_TIMEOUT)
                    .method(method, publisher)
                    .header("Accept", "application/xml,text/plain,*/*");
            if (connection.authorization() != null) {
                builder.header("Authorization", connection.authorization());
            }
            if (depth != null) {
                builder.header("Depth", depth);
            }
            if (body != null) {
                builder.header("Content-Type", "application/xml; charset=utf-8");
            }
            for (Header header : extraHeaders) {
                builder.header(header.name(), header.value());
            }
            HttpResponse<byte[]> response = connection.client().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status >= 200 && status < 300 || status == 207) {
                return response;
            }
            if (status == 404) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "webdav path not found");
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "webdav request failed, status=" + status);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "webdav request failed", ex);
        }
    }

    public static List<DavItem> propFindChildren(DavConnection connection, String dirPath) {
        URI current = resolve(connection, dirPath);
        HttpResponse<byte[]> response = send(connection, "PROPFIND", dirPath, "1",
                PROPFIND_BODY.getBytes(StandardCharsets.UTF_8), List.of());
        return parseItems(response.body(), current.getPath(), true);
    }

    public static DavItem propFindSelf(DavConnection connection, String path) {
        URI current = resolve(connection, path);
        HttpResponse<byte[]> response = send(connection, "PROPFIND", path, "0",
                PROPFIND_BODY.getBytes(StandardCharsets.UTF_8), List.of());
        List<DavItem> items = parseItems(response.body(), current.getPath(), false);
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "webdav path not found");
        }
        return items.get(0);
    }

    private static List<DavItem> parseItems(byte[] xml, String currentPath, boolean excludeSelf) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            NodeList responses = document.getElementsByTagName("d:response");
            List<DavItem> items = new ArrayList<>();
            String normalizedCurrent = normalizeHrefPath(currentPath);
            for (int i = 0; i < responses.getLength(); i++) {
                Element response = (Element) responses.item(i);
                String href = textByTag(response, "d:href");
                String decoded = URLDecoder.decode(href, StandardCharsets.UTF_8);
                String normalizedHref = normalizeHrefPath(decoded);
                if (excludeSelf && normalizedHref.equals(normalizedCurrent)) {
                    continue;
                }
                if (excludeSelf && !isDirectOrNestedChild(normalizedHref, normalizedCurrent)) {
                    continue;
                }
                String name = leafName(normalizedHref);
                if (excludeSelf && name.isBlank()) {
                    continue;
                }
                boolean directory = response.getElementsByTagName("d:collection").getLength() > 0;
                long size = parseLong(textByTag(response, "d:getcontentlength"));
                String mtime = parseMtime(textByTag(response, "d:getlastmodified"));
                String etag = textByTag(response, "d:getetag");
                if (etag == null || etag.isBlank()) {
                    etag = Long.toHexString(size) + "-" + Integer.toHexString(normalizedHref.hashCode());
                }
                items.add(new DavItem(normalizedHref.isBlank() ? "/" : normalizedHref, name, directory, size, mtime, etag));
            }
            return items;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "webdav parse propfind failed", ex);
        }
    }

    private static String normalizeHrefPath(String hrefLike) {
        if (hrefLike == null || hrefLike.isBlank()) {
            return "/";
        }
        String path = hrefLike.trim();
        try {
            URI uri = URI.create(path);
            if (uri.getScheme() != null && uri.getPath() != null) {
                path = uri.getPath();
            }
        } catch (Exception ignored) {
            // href 可能是非标准相对路径，保持原值继续走字符串归一化
        }
        path = path.replace('\\', '/');
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static boolean isDirectOrNestedChild(String candidate, String parent) {
        if (candidate.equals(parent)) {
            return false;
        }
        String parentPrefix = parent.endsWith("/") ? parent : parent + "/";
        return candidate.startsWith(parentPrefix);
    }

    private static String leafName(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "";
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String textByTag(Element node, String tagName) {
        NodeList list = node.getElementsByTagName(tagName);
        if (list.getLength() == 0 || list.item(0) == null) {
            return "";
        }
        return list.item(0).getTextContent();
    }

    private static long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? 0L : Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String parseMtime(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.now().toString();
        }
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toString();
        } catch (Exception ex) {
            return OffsetDateTime.now().toString();
        }
    }

    public record DavConnection(HttpClient client, URI rootUri, String authorization) {
    }

    public record Header(String name, String value) {
    }

    public record DavItem(String path, String name, boolean directory, long sizeBytes, String mtime, String etag) {
    }
}
