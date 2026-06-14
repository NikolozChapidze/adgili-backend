package li.adgi.api.verification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public class VerificationDtos {

    public record SubmitRequest(
        @NotNull IdentityVerification.Type verificationType,
        @NotBlank String documentFrontUrl,
        String documentBackUrl
    ) {}

    public record VerificationView(
        UUID id,
        UUID userId,
        String verificationType,
        String status,
        String rejectionReason,
        Instant createdAt,
        Instant reviewedAt
    ) {
        public static VerificationView from(IdentityVerification v) {
            return new VerificationView(v.getId(), v.getUserId(),
                v.getVerificationType().name(), v.getStatus().name(),
                v.getRejectionReason(), v.getCreatedAt(), v.getReviewedAt());
        }
    }

    public record AdminDecisionRequest(String reason) {}
}
