package li.adgi.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import li.adgi.api.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication, verification, password reset")
@SecurityRequirements // public by default
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    @PostMapping("/register")
    @Operation(summary = "Register a new user (sends email token + phone OTP)")
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange refresh token for a new access token")
    public AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout (stateless JWT; client should discard tokens)")
    public ResponseEntity<Void> logout() {
        // Stateless JWT: client discards tokens. Could blacklist refresh in Redis here.
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset link (always returns 204)")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody AuthDtos.ForgotPasswordRequest req) {
        authService.forgotPassword(req.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Complete password reset with token")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody AuthDtos.ResetPasswordRequest req) {
        authService.resetPassword(req.token(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email address using token from email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody AuthDtos.VerifyEmailRequest req) {
        authService.verifyEmail(req.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/send-phone-otp")
    @Operation(summary = "Resend phone OTP (requires auth)",
        security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> sendPhoneOtp(@Valid @RequestBody AuthDtos.SendPhoneOtpRequest req) {
        authService.sendPhoneOtp(currentUser.get().userId(), req.phone());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-phone-otp")
    @Operation(summary = "Verify phone number with OTP (requires auth)",
        security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> verifyPhoneOtp(
            @Valid @RequestBody AuthDtos.VerifyPhoneOtpRequest req) {
        authService.verifyPhoneOtp(currentUser.get().userId(), req.phone(), req.code());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Current authenticated user summary",
        security = @SecurityRequirement(name = "bearerAuth"))
    public Map<String, Object> me() {
        var p = currentUser.get();
        return Map.of("userId", p.userId(), "role", p.role());
    }
}
