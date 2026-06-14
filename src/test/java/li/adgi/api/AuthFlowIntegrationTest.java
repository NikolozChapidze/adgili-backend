package li.adgi.api;

import com.fasterxml.jackson.databind.JsonNode;
import li.adgi.api.auth.AuthDtos;
import li.adgi.api.notification.Notification;
import li.adgi.api.notification.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class AuthFlowIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired NotificationRepository notifications;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanup() {
        // Truncate user-facing tables in FK-safe order before each test
        jdbc.execute("TRUNCATE TABLE " +
            "audit_logs, notifications, identity_verifications, " +
            "host_profiles, renter_profiles, user_profiles, users " +
            "RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("Register → phone OTP verify → email token verify → /users/me shows verified")
    void fullRegistrationAndVerificationFlow() throws Exception {
        // ---------- 1. Register ----------
        var registerReq = new AuthDtos.RegisterRequest(
            "test+newuser@example.com", "+995555123456", "Password123!",
            "Test", "User", true, true);
        ResponseEntity<AuthDtos.AuthResponse> regResp = rest.postForEntity(
            "/api/v1/auth/register", registerReq, AuthDtos.AuthResponse.class);

        assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(regResp.getBody()).isNotNull();
        AuthDtos.AuthResponse auth = regResp.getBody();
        assertThat(auth.tokens().accessToken()).isNotBlank();
        assertThat(auth.tokens().refreshToken()).isNotBlank();
        assertThat(auth.user().email()).isEqualTo("test+newuser@example.com");
        assertThat(auth.user().status()).isEqualTo("PENDING");
        assertThat(auth.user().emailVerified()).isFalse();
        assertThat(auth.user().phoneVerified()).isFalse();

        UUID userId = auth.user().id();
        String accessToken = auth.tokens().accessToken();

        // ---------- 2. Registration produced email + SMS notifications ----------
        List<Notification> userNotifs = notifications.findAll().stream()
            .filter(n -> n.getUserId().equals(userId)).toList();
        assertThat(userNotifs).hasSizeGreaterThanOrEqualTo(2);

        Notification emailNotif = userNotifs.stream()
            .filter(n -> n.getChannel() == Notification.Channel.EMAIL)
            .findFirst().orElseThrow();
        String emailToken = emailNotif.getPayload().get("token").asText();
        assertThat(emailToken).isNotBlank();

        // In dev with fixed OTP, we know the code is 000000
        String otpCode = "000000";

        // ---------- 3. Verify email ----------
        ResponseEntity<Void> emailVerifyResp = rest.postForEntity(
            "/api/v1/auth/verify-email",
            new AuthDtos.VerifyEmailRequest(emailToken), Void.class);
        assertThat(emailVerifyResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // ---------- 4. Verify phone OTP (needs auth) ----------
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        var verifyPhoneReq = new AuthDtos.VerifyPhoneOtpRequest("+995555123456", otpCode);
        HttpEntity<AuthDtos.VerifyPhoneOtpRequest> phoneEntity = new HttpEntity<>(verifyPhoneReq, authHeaders);
        ResponseEntity<Void> phoneResp = rest.exchange(
            "/api/v1/auth/verify-phone-otp", HttpMethod.POST, phoneEntity, Void.class);
        assertThat(phoneResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // ---------- 5. GET /users/me — should be ACTIVE & fully verified ----------
        HttpEntity<Void> authEntity = new HttpEntity<>(authHeaders);
        ResponseEntity<JsonNode> meResp = rest.exchange(
            "/api/v1/users/me", HttpMethod.GET, authEntity, JsonNode.class);
        assertThat(meResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode me = meResp.getBody();
        assertThat(me).isNotNull();
        assertThat(me.get("emailVerified").asBoolean()).isTrue();
        assertThat(me.get("phoneVerified").asBoolean()).isTrue();
        assertThat(me.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(me.get("profile").get("firstName").asText()).isEqualTo("Test");
    }

    @Test
    @DisplayName("Login with correct creds returns tokens; wrong creds returns 401")
    void loginHappyAndSadPath() {
        // Register a user first
        rest.postForEntity("/api/v1/auth/register", new AuthDtos.RegisterRequest(
            "login-test@example.com", "+995555999888", "CorrectPassword!",
            "Login", "Tester", true, true), AuthDtos.AuthResponse.class);

        // Happy path
        ResponseEntity<AuthDtos.AuthResponse> ok = rest.postForEntity(
            "/api/v1/auth/login",
            new AuthDtos.LoginRequest("login-test@example.com", "CorrectPassword!"),
            AuthDtos.AuthResponse.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().tokens().accessToken()).isNotBlank();

        // Wrong password
        ResponseEntity<Map> bad = rest.postForEntity(
            "/api/v1/auth/login",
            new AuthDtos.LoginRequest("login-test@example.com", "WrongPassword!"),
            Map.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Protected endpoints reject missing or invalid bearer tokens with 401")
    void protectedEndpointsRequireAuth() {
        ResponseEntity<Map> noToken = rest.getForEntity("/api/v1/users/me", Map.class);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("not-a-real-jwt");
        ResponseEntity<Map> badToken = rest.exchange(
            "/api/v1/users/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(badToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Duplicate email registration returns 409")
    void duplicateEmailConflict() {
        var req = new AuthDtos.RegisterRequest(
            "dupe@example.com", "+995555111222", "Password123!",
            "First", "Try", true, true);
        ResponseEntity<AuthDtos.AuthResponse> first = rest.postForEntity(
            "/api/v1/auth/register", req, AuthDtos.AuthResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Same email, different phone
        var dupReq = new AuthDtos.RegisterRequest(
            "dupe@example.com", "+995555333444", "OtherPass123!",
            "Second", "Try", true, true);
        ResponseEntity<Map> second = rest.postForEntity(
            "/api/v1/auth/register", dupReq, Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code")).isEqualTo("EMAIL_EXISTS");
    }
}
