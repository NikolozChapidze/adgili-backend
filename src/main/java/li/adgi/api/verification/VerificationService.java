package li.adgi.api.verification;

import li.adgi.api.audit.AuditService;
import li.adgi.api.common.ApiException;
import li.adgi.api.config.FeatureFlags;
import li.adgi.api.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private final IdentityVerificationRepository repo;
    private final FeatureFlags flags;
    private final NotificationService notifications;
    private final AuditService audit;

    @Transactional
    public IdentityVerification submit(UUID userId, VerificationDtos.SubmitRequest req) {
        if (repo.existsByUserIdAndStatus(userId, IdentityVerification.Status.PENDING)) {
            throw ApiException.conflict("PENDING_EXISTS",
                "You already have a pending verification");
        }

        IdentityVerification v = IdentityVerification.builder()
            .userId(userId)
            .verificationType(req.verificationType())
            .status(IdentityVerification.Status.PENDING)
            .documentFrontUrl(req.documentFrontUrl())
            .documentBackUrl(req.documentBackUrl())
            .build();
        v = repo.save(v);

        audit.log(userId, "IDENTITY_VERIFICATION", v.getId(), "SUBMITTED", null, Map.of(
            "type", v.getVerificationType().name()
        ));

        if (flags.isIdentityAutoApprove()) {
            autoApproveLater(v.getId());
        }
        return v;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoApproveLater(UUID verificationId) {
        try {
            Thread.sleep(2000); // simulate provider latency
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        repo.findById(verificationId).ifPresent(v -> {
            if (v.getStatus() != IdentityVerification.Status.PENDING) return;
            v.setStatus(IdentityVerification.Status.APPROVED);
            v.setReviewedAt(Instant.now());
            v.setProviderReference("stub-autoapprove");
            repo.save(v);
            notifications.sendInApp(v.getUserId(), "verification.approved",
                "Identity verified", Map.of("verificationId", v.getId().toString()));
            audit.log(null, "IDENTITY_VERIFICATION", v.getId(), "AUTO_APPROVED", null, null);
        });
    }

    @Transactional(readOnly = true)
    public List<IdentityVerification> mine(UUID userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public IdentityVerification adminApprove(UUID verificationId, UUID adminId) {
        IdentityVerification v = repo.findById(verificationId)
            .orElseThrow(() -> ApiException.notFound("Verification"));
        if (v.getStatus() != IdentityVerification.Status.PENDING) {
            throw ApiException.conflict("NOT_PENDING", "Verification is not pending");
        }
        v.setStatus(IdentityVerification.Status.APPROVED);
        v.setReviewedAt(Instant.now());
        v.setReviewedBy(adminId);
        repo.save(v);
        notifications.sendInApp(v.getUserId(), "verification.approved",
            "Identity verified", Map.of("verificationId", v.getId().toString()));
        audit.log(adminId, "IDENTITY_VERIFICATION", v.getId(), "APPROVED", null, null);
        return v;
    }

    @Transactional
    public IdentityVerification adminReject(UUID verificationId, UUID adminId, String reason) {
        IdentityVerification v = repo.findById(verificationId)
            .orElseThrow(() -> ApiException.notFound("Verification"));
        if (v.getStatus() != IdentityVerification.Status.PENDING) {
            throw ApiException.conflict("NOT_PENDING", "Verification is not pending");
        }
        v.setStatus(IdentityVerification.Status.REJECTED);
        v.setReviewedAt(Instant.now());
        v.setReviewedBy(adminId);
        v.setRejectionReason(reason);
        repo.save(v);
        notifications.sendInApp(v.getUserId(), "verification.rejected",
            "Identity verification rejected",
            Map.of("verificationId", v.getId().toString(), "reason", reason == null ? "" : reason));
        audit.log(adminId, "IDENTITY_VERIFICATION", v.getId(), "REJECTED", null,
            Map.of("reason", reason));
        return v;
    }
}
