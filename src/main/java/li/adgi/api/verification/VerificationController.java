package li.adgi.api.verification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import li.adgi.api.common.PageResponse;
import li.adgi.api.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Verification", description = "Identity verification submission and admin review")
public class VerificationController {

    private final VerificationService service;
    private final IdentityVerificationRepository repo;
    private final CurrentUser currentUser;

    @PostMapping("/api/v1/verifications/identity")
    @Operation(summary = "Submit identity verification (auto-approved in dev)")
    public VerificationDtos.VerificationView submit(
            @Valid @RequestBody VerificationDtos.SubmitRequest req) {
        return VerificationDtos.VerificationView.from(
            service.submit(currentUser.get().userId(), req));
    }

    @GetMapping("/api/v1/verifications/me")
    @Operation(summary = "List my verification submissions")
    public List<VerificationDtos.VerificationView> mine() {
        return service.mine(currentUser.get().userId()).stream()
            .map(VerificationDtos.VerificationView::from).toList();
    }

    // ---------- Admin ----------

    @GetMapping("/api/v1/admin/verifications")
    @Operation(summary = "[Admin] List verifications by status")
    public PageResponse<VerificationDtos.VerificationView> adminList(
            @RequestParam(defaultValue = "PENDING") IdentityVerification.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(repo.findByStatus(status, PageRequest.of(page, Math.min(size, 100))))
            .map(VerificationDtos.VerificationView::from);
    }

    @PostMapping("/api/v1/admin/verifications/{verificationId}/approve")
    @Operation(summary = "[Admin] Approve a verification")
    public VerificationDtos.VerificationView approve(@PathVariable UUID verificationId) {
        return VerificationDtos.VerificationView.from(
            service.adminApprove(verificationId, currentUser.get().userId()));
    }

    @PostMapping("/api/v1/admin/verifications/{verificationId}/reject")
    @Operation(summary = "[Admin] Reject a verification with reason")
    public VerificationDtos.VerificationView reject(
            @PathVariable UUID verificationId,
            @RequestBody(required = false) VerificationDtos.AdminDecisionRequest body) {
        String reason = body == null ? null : body.reason();
        return VerificationDtos.VerificationView.from(
            service.adminReject(verificationId, currentUser.get().userId(), reason));
    }
}
