package com.mpfm.backend.adapter.api.webdav;

import com.mpfm.backend.application.file.FileApplicationService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * WebDAV 读路径缓存：提供 stat/list 短 TTL 缓存、PROPFIND 响应缓存和同键 singleflight 合并。
 */
@Component
public class WebDavCacheService {
    private static final long STAT_TTL_MILLIS = 1500L;
    private static final long LIST_TTL_MILLIS = 1500L;
    private static final long PROPFIND_TTL_MILLIS = 1200L;
    private static final long HOT_PROPFIND_WINDOW_MILLIS = 2000L;
    private static final int HOT_PROPFIND_THRESHOLD = 12;
    private static final long HOT_PROPFIND_FALLBACK_MAX_AGE_MILLIS = 10000L;
    private static final long RATE_LIMIT_WINDOW_MILLIS = 2000L;
    private static final int RATE_LIMIT_THRESHOLD = 32;

    private final Map<String, TimedValue<FileApplicationService.EntryResult>> statCache = new ConcurrentHashMap<>();
    private final Map<String, TimedValue<List<FileApplicationService.EntryResult>>> listCache = new ConcurrentHashMap<>();
    private final Map<String, TimedValue<String>> propfindXmlCache = new ConcurrentHashMap<>();
    private final Map<String, TimedValue<String>> recentPropfindXmlCache = new ConcurrentHashMap<>();
    private final Map<String, HotPathState> hotPathState = new ConcurrentHashMap<>();
    private final Map<String, HotPathState> rateLimitState = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Object>> inflight = new ConcurrentHashMap<>();

    public FileApplicationService.EntryResult getOrLoadStat(String username,
                                                            String virtualPath,
                                                            Supplier<FileApplicationService.EntryResult> loader) {
        String key = "stat|" + basePathKey(username, virtualPath);
        return getOrLoadTimed(statCache, key, STAT_TTL_MILLIS, loader);
    }

    public List<FileApplicationService.EntryResult> getOrLoadList(String username,
                                                                  String virtualPath,
                                                                  Supplier<List<FileApplicationService.EntryResult>> loader) {
        String key = "list|" + basePathKey(username, virtualPath);
        return getOrLoadTimed(listCache, key, LIST_TTL_MILLIS, () -> List.copyOf(loader.get()));
    }

    public String getOrLoadPropfindXml(String username,
                                       String virtualPath,
                                       int depth,
                                       String requestShape,
                                       Supplier<String> loader) {
        String key = "propfind|" + basePathKey(username, virtualPath) + "|d=" + depth + "|shape=" + requestShape;
        String value = getOrLoadTimed(propfindXmlCache, key, PROPFIND_TTL_MILLIS, loader);
        recentPropfindXmlCache.put(key, new TimedValue<>(value, System.currentTimeMillis() + HOT_PROPFIND_FALLBACK_MAX_AGE_MILLIS));
        return value;
    }

    /**
     * 写操作后按用户清理 WebDAV 读缓存，优先保证一致性。
     */
    public void evictUser(String username) {
        String marker = "|" + username + "|";
        statCache.keySet().removeIf(key -> key.contains(marker));
        listCache.keySet().removeIf(key -> key.contains(marker));
        propfindXmlCache.keySet().removeIf(key -> key.contains(marker));
        recentPropfindXmlCache.keySet().removeIf(key -> key.contains(marker));
        hotPathState.keySet().removeIf(key -> key.contains(marker));
        rateLimitState.keySet().removeIf(key -> key.contains(marker));
    }

    /**
     * 高频遍历保护：同一路径短窗口内过热时，优先返回最近缓存，避免重复触发后端深链路。
     */
    public String tryServeHotPropfindCache(String username,
                                           String virtualPath,
                                           int depth,
                                           String requestShape) {
        String key = "propfind|" + basePathKey(username, virtualPath) + "|d=" + depth + "|shape=" + requestShape;
        long now = System.currentTimeMillis();
        HotPathState state = hotPathState.computeIfAbsent(key, unused -> new HotPathState(now, new AtomicInteger(0)));
        if (now - state.windowStartMillis() > HOT_PROPFIND_WINDOW_MILLIS) {
            state.windowStartMillis = now;
            state.hitCount().set(0);
        }
        int hits = state.hitCount().incrementAndGet();
        if (hits < HOT_PROPFIND_THRESHOLD) {
            return null;
        }
        TimedValue<String> recent = recentPropfindXmlCache.get(key);
        if (recent == null || recent.expireAtMillis() < now) {
            return null;
        }
        return recent.value();
    }

    /**
     * 高频扫描抑制：同路径在短窗口内命中过高时返回 true，由上层走最小响应或速率控制。
     */
    public boolean shouldRateLimitPropfind(String username, String virtualPath, int depth) {
        String key = "ratelimit|" + basePathKey(username, virtualPath) + "|d=" + depth;
        long now = System.currentTimeMillis();
        HotPathState state = rateLimitState.computeIfAbsent(key, unused -> new HotPathState(now, new AtomicInteger(0)));
        if (now - state.windowStartMillis() > RATE_LIMIT_WINDOW_MILLIS) {
            state.windowStartMillis = now;
            state.hitCount().set(0);
        }
        int hits = state.hitCount().incrementAndGet();
        return hits > RATE_LIMIT_THRESHOLD;
    }

    private String basePathKey(String username, String virtualPath) {
        String normalizedPath = virtualPath == null ? "/" : virtualPath;
        String[] parts = normalizedPath.split("/", 4);
        String namespace = parts.length > 1 ? parts[1] : "";
        String mount = parts.length > 2 ? parts[2] : "";
        return username + "|" + namespace + "|" + mount + "|" + normalizedPath;
    }

    private <T> T getOrLoadTimed(Map<String, TimedValue<T>> cache,
                                 String cacheKey,
                                 long ttlMillis,
                                 Supplier<T> loader) {
        TimedValue<T> cached = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expireAtMillis() > now) {
            return cached.value();
        }
        String inflightKey = "load|" + cacheKey;
        CompletableFuture<Object> future = inflight.computeIfAbsent(inflightKey, key -> new CompletableFuture<>());
        if (!future.isDone()) {
            boolean runner = false;
            synchronized (future) {
                if (!future.isDone()) {
                    runner = true;
                }
            }
            if (runner) {
                try {
                    T loaded = Objects.requireNonNull(loader.get(), "cache loader result cannot be null");
                    cache.put(cacheKey, new TimedValue<>(loaded, now + ttlMillis));
                    future.complete(loaded);
                } catch (Throwable ex) {
                    future.completeExceptionally(ex);
                } finally {
                    inflight.remove(inflightKey, future);
                }
            }
        }
        try {
            // 这里的强转仅用于回收 singleflight 的泛型结果；key 已按调用点隔离，不会混型。
            @SuppressWarnings("unchecked")
            T value = (T) future.join();
            return value;
        } catch (RuntimeException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }

    private record TimedValue<T>(T value, long expireAtMillis) {
    }

    private static final class HotPathState {
        private volatile long windowStartMillis;
        private final AtomicInteger hitCount;

        private HotPathState(long windowStartMillis, AtomicInteger hitCount) {
            this.windowStartMillis = windowStartMillis;
            this.hitCount = hitCount;
        }

        long windowStartMillis() {
            return windowStartMillis;
        }

        AtomicInteger hitCount() {
            return hitCount;
        }
    }
}
