package li.adgi.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import li.adgi.api.common.ApiException;
import li.adgi.api.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties props;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, String role) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.getAccessTokenTtlMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
            .issuer(props.getIssuer())
            .subject(userId.toString())
            .claims(Map.of(CLAIM_ROLE, role, CLAIM_TYPE, TYPE_ACCESS))
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(key())
            .compact();
    }

    public String generateRefreshToken(UUID userId, String role) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.getRefreshTokenTtlDays(), ChronoUnit.DAYS);
        return Jwts.builder()
            .issuer(props.getIssuer())
            .subject(userId.toString())
            .claims(Map.of(CLAIM_ROLE, role, CLAIM_TYPE, TYPE_REFRESH))
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(key())
            .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                .verifyWith(key())
                .requireIssuer(props.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (Exception e) {
            throw ApiException.unauthorized("Invalid or expired token");
        }
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public String extractRole(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

    public String extractType(Claims claims) {
        return claims.get(CLAIM_TYPE, String.class);
    }
}
