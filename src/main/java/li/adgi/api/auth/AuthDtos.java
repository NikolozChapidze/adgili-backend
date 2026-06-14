package li.adgi.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is required")
        String email,

        @Pattern(regexp = "\\+?[0-9]{8,20}", message = "Invalid phone number")
        @NotBlank(message = "Phone is required")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
        String password,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        boolean acceptTerms,
        boolean acceptPrivacy
    ) {}

    public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {}

    public record VerifyEmailRequest(@NotBlank String token) {}

    public record SendPhoneOtpRequest(@NotBlank String phone) {}

    public record VerifyPhoneOtpRequest(
        @NotBlank String phone,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code
    ) {}

    public record TokenPair(String accessToken, String refreshToken, long accessExpiresInSeconds) {}

    public record AuthResponse(
        TokenPair tokens,
        UserSummary user
    ) {}

    public record UserSummary(
        java.util.UUID id,
        String email,
        String phone,
        String role,
        String status,
        String firstName,
        String lastName,
        boolean emailVerified,
        boolean phoneVerified
    ) {}
}
