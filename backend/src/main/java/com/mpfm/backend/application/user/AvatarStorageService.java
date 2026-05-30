package com.mpfm.backend.application.user;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

@Service
public class AvatarStorageService {

    private static final Pattern DATA_URL_PATTERN = Pattern.compile("^data:([a-zA-Z0-9.+/-]+);base64,(.+)$", Pattern.DOTALL);
    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");
    private static final String JPG_EXT = "jpg";
    private final AvatarStorageProperties properties;

    public AvatarStorageService(AvatarStorageProperties properties) {
        this.properties = properties;
    }

    public String store(String userId, String avatarDataUrl) {
        Matcher matcher = DATA_URL_PATTERN.matcher(avatarDataUrl == null ? "" : avatarDataUrl.trim());
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "avatar must be data url");
        }
        String mimeType = matcher.group(1).toLowerCase(Locale.ROOT);
        String ext = toExt(mimeType);
        byte[] content;
        try {
            content = Base64.getDecoder().decode(matcher.group(2));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "avatar base64 invalid", ex);
        }
        Path dir = Paths.get(properties.basePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            deleteOldAvatars(dir, userId);
            Path file = dir.resolve(userId + "." + ext).normalize();
            Files.write(file, content);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "avatar store failed", ex);
        }
        return "/api/v1/users/avatar/" + userId;
    }

    public Resource load(String userId) {
        Path dir = Paths.get(properties.basePath()).toAbsolutePath().normalize();
        String[] exts = new String[] {"jpg", "jpeg", "png", "webp"};
        for (String ext : exts) {
            Path file = dir.resolve(userId + "." + ext).normalize();
            if (Files.exists(file)) {
                try {
                    return new UrlResource(file.toUri());
                } catch (IOException ex) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "avatar read failed", ex);
                }
            }
        }
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "avatar not found");
    }

    public String signAvatarUrl(String userId, String rawAvatarUrl) {
        if (rawAvatarUrl == null || rawAvatarUrl.isBlank()) {
            return "";
        }
        long exp = System.currentTimeMillis() / 1000 + Math.max(30L, properties.signedUrlExpireSeconds());
        String sig = sign(userId, exp);
        return rawAvatarUrl + "?exp=" + exp + "&sig=" + sig;
    }

    public void verifyAvatarAccess(String userId, String exp, String sig) {
        if (exp == null || exp.isBlank() || sig == null || sig.isBlank() || !HEX_64.matcher(sig).matches()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "avatar signature required");
        }
        long expSeconds;
        try {
            expSeconds = Long.parseLong(exp);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "avatar exp invalid", ex);
        }
        long now = System.currentTimeMillis() / 1000;
        if (expSeconds < now) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "avatar url expired");
        }
        String expected = sign(userId, expSeconds);
        if (!expected.equals(sig)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "avatar signature invalid");
        }
    }

    public String probeContentType(Resource resource) {
        try {
            Path path = resource.getFile().toPath();
            String contentType = Files.probeContentType(path);
            return contentType == null ? "application/octet-stream" : contentType;
        } catch (IOException ex) {
            return "application/octet-stream";
        }
    }

    private void deleteOldAvatars(Path dir, String userId) throws IOException {
        try (var stream = Files.list(dir)) {
            stream.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith(userId + ".");
            }).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private String toExt(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/jpeg", "image/jpg" -> JPG_EXT;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "avatar mime unsupported");
        };
    }

    private String sign(String userId, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.signingKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((userId + ":" + exp).getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "avatar sign failed", ex);
        }
    }

    private String toHex(byte[] source) {
        StringBuilder sb = new StringBuilder(source.length * 2);
        for (byte b : source) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
