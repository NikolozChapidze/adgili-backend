package li.adgi.api.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opaque-token store for email verification and password reset.
 * Maps token -> target identifier (email or userId).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenStore {

    private static final Duration TTL = Duration.ofHours(24);
    private static final SecureRandom RNG = new SecureRandom();

    private final Optional<StringRedisTemplate> redis;
    private final Map<String, Record> memory = new ConcurrentHashMap<>();

    private record Record(String value, Instant expiresAt) {}

    public String issue(String scope, String value) {
        String token = newToken();
        String key = keyOf(scope, token);
        if (redis.isPresent()) {
            try { redis.get().opsForValue().set(key, value, TTL); return token; }
            catch (Exception e) { log.warn("Redis unavailable, using memory: {}", e.getMessage()); }
        }
        memory.put(key, new Record(value, Instant.now().plus(TTL)));
        return token;
    }

    public Optional<String> consume(String scope, String token) {
        String key = keyOf(scope, token);
        if (redis.isPresent()) {
            try {
                String v = redis.get().opsForValue().getAndDelete(key);
                if (v != null) return Optional.of(v);
            } catch (Exception ignored) {}
        }
        Record r = memory.remove(key);
        if (r == null) return Optional.empty();
        if (r.expiresAt.isBefore(Instant.now())) return Optional.empty();
        return Optional.of(r.value);
    }

    private String newToken() {
        byte[] buf = new byte[24];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String keyOf(String scope, String token) {
        return "tok:" + scope + ":" + token;
    }
}
