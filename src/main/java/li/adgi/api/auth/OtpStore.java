package li.adgi.api.auth;

import li.adgi.api.config.FeatureFlags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP store. Uses Redis when available, otherwise an in-memory map.
 * In dev (fixed-otp configured), always returns the fixed code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtpStore {

    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RNG = new SecureRandom();

    private final FeatureFlags flags;
    private final Optional<StringRedisTemplate> redis;

    @Value("${adgili.dev.fixed-otp:000000}")
    private String fixedOtp;

    // In-memory fallback
    private final Map<String, OtpRecord> memory = new ConcurrentHashMap<>();

    private record OtpRecord(String code, Instant expiresAt) {}

    public String issue(String key) {
        String code = flags.isSmsStub() ? fixedOtp : generateCode();
        Instant exp = Instant.now().plus(OTP_TTL);
        if (redis.isPresent()) {
            try {
                redis.get().opsForValue().set("otp:" + key, code, OTP_TTL);
            } catch (Exception ex) {
                log.warn("Redis unavailable, using in-memory OTP: {}", ex.getMessage());
                memory.put(key, new OtpRecord(code, exp));
            }
        } else {
            memory.put(key, new OtpRecord(code, exp));
        }
        return code;
    }

    public boolean verify(String key, String code) {
        if (code == null) return false;
        String stored = read(key);
        if (stored == null) return false;
        boolean ok = stored.equals(code.trim());
        if (ok) invalidate(key);
        return ok;
    }

    public void invalidate(String key) {
        if (redis.isPresent()) {
            try { redis.get().delete("otp:" + key); } catch (Exception ignored) {}
        }
        memory.remove(key);
    }

    private String read(String key) {
        if (redis.isPresent()) {
            try {
                String v = redis.get().opsForValue().get("otp:" + key);
                if (v != null) return v;
            } catch (Exception ignored) {}
        }
        OtpRecord r = memory.get(key);
        if (r == null) return null;
        if (r.expiresAt.isBefore(Instant.now())) {
            memory.remove(key);
            return null;
        }
        return r.code;
    }

    private String generateCode() {
        return String.format("%06d", RNG.nextInt(1_000_000));
    }
}
