package com.mpfm.backend.common.security;

import com.mpfm.backend.application.user.UserStatus;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * WebDAV 认证用户缓存服务：优先使用 Redis，未配置时回退本地短缓存，降低高频探测查库压力。
 */
@Component
public class WebDavUserCacheService {
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final String PREFIX = "mpfm:webdav:user:";

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, LocalCacheEntry> localCache = new ConcurrentHashMap<>();

    public WebDavUserCacheService(UserRepository userRepository, @Nullable StringRedisTemplate redisTemplate) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    public Optional<CachedUser> findByUsername(String username) {
        Optional<CachedUser> fromRedis = readRedis(username);
        if (fromRedis.isPresent()) {
            return fromRedis;
        }
        Optional<CachedUser> fromLocal = readLocal(username);
        if (fromLocal.isPresent()) {
            return fromLocal;
        }
        Optional<CachedUser> loaded = userRepository.findByUsername(username).map(WebDavUserCacheService::toCachedUser);
        loaded.ifPresent(user -> {
            writeLocal(username, user);
            writeRedis(username, user);
        });
        return loaded;
    }

    private Optional<CachedUser> readRedis(String username) {
        if (redisTemplate == null) {
            return Optional.empty();
        }
        try {
            String raw = redisTemplate.opsForValue().get(PREFIX + username);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 4) {
                return Optional.empty();
            }
            return Optional.of(new CachedUser(parts[0], parts[1], parts[2], parts[3]));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void writeRedis(String username, CachedUser user) {
        if (redisTemplate == null) {
            return;
        }
        try {
            String raw = String.join("|", user.username(), user.passwordHash(), user.role(), user.status());
            redisTemplate.opsForValue().set(PREFIX + username, raw, TTL);
        } catch (Exception ignored) {
        }
    }

    private Optional<CachedUser> readLocal(String username) {
        LocalCacheEntry entry = localCache.get(username);
        if (entry == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > entry.expireAtMs) {
            localCache.remove(username);
            return Optional.empty();
        }
        return Optional.of(entry.user);
    }

    private void writeLocal(String username, CachedUser user) {
        localCache.put(username, new LocalCacheEntry(user, System.currentTimeMillis() + TTL.toMillis()));
    }

    private static CachedUser toCachedUser(UserEntity user) {
        return new CachedUser(
                user.getUsername(),
                user.getPasswordHash(),
                user.getPlatformRole().name(),
                user.getStatus() == null ? UserStatus.ACTIVE.name() : user.getStatus().name());
    }

    public record CachedUser(String username, String passwordHash, String role, String status) {
        public boolean disabled() {
            return UserStatus.DISABLED.name().equalsIgnoreCase(status);
        }
    }

    private record LocalCacheEntry(CachedUser user, long expireAtMs) {
    }
}
