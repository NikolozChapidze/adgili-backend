package li.adgi.api.auth;

import io.jsonwebtoken.Claims;
import li.adgi.api.common.ApiException;
import li.adgi.api.config.JwtProperties;
import li.adgi.api.notification.NotificationService;
import li.adgi.api.security.JwtService;
import li.adgi.api.user.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String SCOPE_EMAIL = "email-verify";
    private static final String SCOPE_RESET = "password-reset";

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final HostProfileRepository hostProfiles;
    private final RenterProfileRepository renterProfiles;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final JwtProperties jwtProps;
    private final OtpStore otpStore;
    private final TokenStore tokenStore;
    private final NotificationService notifications;

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req) {
        if (!req.acceptTerms() || !req.acceptPrivacy()) {
            throw ApiException.badRequest("TERMS_REQUIRED",
                "Must accept terms and privacy policy");
        }
        if (users.existsByEmail(req.email())) {
            throw ApiException.conflict("EMAIL_EXISTS", "Email already registered");
        }
        if (users.existsByPhone(req.phone())) {
            throw ApiException.conflict("PHONE_EXISTS", "Phone already registered");
        }

        Instant now = Instant.now();
        User user = User.builder()
            .email(req.email().toLowerCase().trim())
            .phone(req.phone().trim())
            .passwordHash(encoder.encode(req.password()))
            .role(UserRole.USER)
            .status(UserStatus.PENDING)
            .termsAcceptedAt(now)
            .privacyAcceptedAt(now)
            .build();
        user = users.save(user);

        UserProfile profile = UserProfile.builder()
            .userId(user.getId())
            .firstName(req.firstName().trim())
            .lastName(req.lastName().trim())
            .displayName(req.firstName().trim() + " " + req.lastName().trim())
            .preferredLanguage("ka")
            .city("Tbilisi")
            .build();
        profiles.save(profile);

        // Always create both profile shells so a user can act as host OR renter
        hostProfiles.save(HostProfile.builder()
            .userId(user.getId())
            .hostStatus(HostProfile.HostStatus.PENDING)
            .averageRating(BigDecimal.ZERO)
            .ratingsCount(0)
            .payoutEnabled(false)
            .build());

        renterProfiles.save(RenterProfile.builder()
            .userId(user.getId())
            .averageRating(BigDecimal.ZERO)
            .ratingsCount(0)
            .build());

        // Issue email verification token + notify
        String emailToken = tokenStore.issue(SCOPE_EMAIL, user.getId().toString());
        notifications.sendEmail(user.getId(), "email.verify", "Verify your adgi.li email",
            Map.of("token", emailToken, "firstName", req.firstName()));

        // Issue phone OTP
        String otp = otpStore.issue("phone:" + user.getPhone());
        notifications.sendSms(user.getId(), "phone.verify",
            Map.of("code", otp, "phone", user.getPhone()));

        log.info("Registered user {} ({})", user.getId(), user.getEmail());
        return buildAuthResponse(user, profile);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
        User user = users.findByEmail(req.email().toLowerCase().trim())
            .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));

        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw ApiException.forbidden("Account suspended");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw ApiException.forbidden("Account deleted");
        }

        UserProfile profile = profiles.findById(user.getId()).orElse(null);
        return buildAuthResponse(user, profile);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse refresh(String refreshToken) {
        Claims claims = jwt.parse(refreshToken);
        if (!JwtService.TYPE_REFRESH.equals(jwt.extractType(claims))) {
            throw ApiException.unauthorized("Not a refresh token");
        }
        UUID userId = jwt.extractUserId(claims);
        User user = users.findById(userId)
            .orElseThrow(() -> ApiException.unauthorized("User not found"));
        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.PENDING) {
            throw ApiException.forbidden("Account not active");
        }
        UserProfile profile = profiles.findById(user.getId()).orElse(null);
        return buildAuthResponse(user, profile);
    }

    @Transactional
    public void verifyEmail(String token) {
        String userIdStr = tokenStore.consume(SCOPE_EMAIL, token)
            .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "Invalid or expired token"));
        User user = users.findById(UUID.fromString(userIdStr))
            .orElseThrow(() -> ApiException.notFound("User"));
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
            activateIfFullyVerified(user);
            users.save(user);
        }
    }

    @Transactional
    public void sendPhoneOtp(UUID userId, String phone) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!user.getPhone().equals(phone)) {
            throw ApiException.badRequest("PHONE_MISMATCH", "Phone does not match account");
        }
        String otp = otpStore.issue("phone:" + phone);
        notifications.sendSms(user.getId(), "phone.verify",
            Map.of("code", otp, "phone", phone));
    }

    @Transactional
    public void verifyPhoneOtp(UUID userId, String phone, String code) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!user.getPhone().equals(phone)) {
            throw ApiException.badRequest("PHONE_MISMATCH", "Phone does not match account");
        }
        if (!otpStore.verify("phone:" + phone, code)) {
            throw ApiException.badRequest("INVALID_OTP", "Invalid or expired verification code");
        }
        user.setPhoneVerifiedAt(Instant.now());
        activateIfFullyVerified(user);
        users.save(user);
    }

    @Transactional
    public void forgotPassword(String email) {
        users.findByEmail(email.toLowerCase().trim()).ifPresent(user -> {
            String token = tokenStore.issue(SCOPE_RESET, user.getId().toString());
            notifications.sendEmail(user.getId(), "password.reset",
                "Reset your adgi.li password", Map.of("token", token));
        });
        // Return 200 either way — don't leak account existence
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        String userIdStr = tokenStore.consume(SCOPE_RESET, token)
            .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "Invalid or expired token"));
        User user = users.findById(UUID.fromString(userIdStr))
            .orElseThrow(() -> ApiException.notFound("User"));
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
    }

    private void activateIfFullyVerified(User user) {
        if (user.getStatus() == UserStatus.PENDING
            && user.getEmailVerifiedAt() != null
            && user.getPhoneVerifiedAt() != null) {
            user.setStatus(UserStatus.ACTIVE);
        }
    }

    private AuthDtos.AuthResponse buildAuthResponse(User user, UserProfile profile) {
        String access = jwt.generateAccessToken(user.getId(), user.getRole().name());
        String refresh = jwt.generateRefreshToken(user.getId(), user.getRole().name());
        AuthDtos.TokenPair tokens = new AuthDtos.TokenPair(
            access, refresh, jwtProps.getAccessTokenTtlMinutes() * 60L);

        AuthDtos.UserSummary summary = new AuthDtos.UserSummary(
            user.getId(), user.getEmail(), user.getPhone(),
            user.getRole().name(), user.getStatus().name(),
            profile != null ? profile.getFirstName() : null,
            profile != null ? profile.getLastName() : null,
            user.getEmailVerifiedAt() != null,
            user.getPhoneVerifiedAt() != null
        );
        return new AuthDtos.AuthResponse(tokens, summary);
    }
}
