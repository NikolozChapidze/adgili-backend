package li.adgi.api.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import li.adgi.api.audit.AuditLog;
import li.adgi.api.audit.AuditLogRepository;
import li.adgi.api.audit.AuditService;
import li.adgi.api.common.ApiException;
import li.adgi.api.common.PageResponse;
import li.adgi.api.listing.ListingEnums;
import li.adgi.api.listing.ListingRepository;
import li.adgi.api.security.CurrentUser;
import li.adgi.api.user.*;
import li.adgi.api.verification.IdentityVerification;
import li.adgi.api.verification.IdentityVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin-only endpoints for users, audit, dashboard")
public class AdminController {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final ListingRepository listings;
    private final IdentityVerificationRepository verifications;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;
    private final CurrentUser currentUser;

    @GetMapping("/dashboard")
    @Operation(summary = "[Admin] Marketplace dashboard stats")
    @Transactional(readOnly = true)
    public AdminDtos.DashboardStats dashboard() {
        long total = users.count();
        long active = users.findAll().stream()
            .filter(u -> u.getStatus() == UserStatus.ACTIVE).count();
        long pendingVer = verifications.findByStatus(
            IdentityVerification.Status.PENDING, PageRequest.of(0, 1)).getTotalElements();
        long pendingListings = listings.findByModerationStatus(
            ListingEnums.ModerationStatus.PENDING, PageRequest.of(0, 1)).getTotalElements();
        long liveListings = listings.findAll().stream()
            .filter(l -> l.getListingStatus() == ListingEnums.ListingStatus.LIVE).count();
        return new AdminDtos.DashboardStats(total, active, pendingVer, pendingListings, liveListings, 0);
    }

    @GetMapping("/users")
    @Operation(summary = "[Admin] List users")
    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.AdminUserView> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var p = users.findAll(PageRequest.of(page, Math.min(size, 100),
            Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(p).map(this::toAdminUserView);
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "[Admin] User detail")
    @Transactional(readOnly = true)
    public AdminDtos.AdminUserView getUser(@PathVariable UUID userId) {
        return toAdminUserView(users.findById(userId).orElseThrow(() -> ApiException.notFound("User")));
    }

    @PostMapping("/users/{userId}/suspend")
    @Operation(summary = "[Admin] Suspend a user")
    @Transactional
    public ResponseEntity<Void> suspend(@PathVariable UUID userId,
                                        @RequestBody AdminDtos.SuspendRequest req) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        u.setStatus(UserStatus.SUSPENDED);
        users.save(u);
        audit.logAdmin(currentUser.get().userId(), "USER", u.getId(), "SUSPENDED", null,
            Map.of("reason", req.reason()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/unsuspend")
    @Operation(summary = "[Admin] Unsuspend a user")
    @Transactional
    public ResponseEntity<Void> unsuspend(@PathVariable UUID userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        u.setStatus(u.isVerified() ? UserStatus.ACTIVE : UserStatus.PENDING);
        users.save(u);
        audit.logAdmin(currentUser.get().userId(), "USER", u.getId(), "UNSUSPENDED", null, null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "[Admin] View audit logs, optionally filtered")
    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.AuditLogView> auditLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pr = PageRequest.of(page, Math.min(size, 200),
            Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> result = (entityType != null && entityId != null)
            ? auditLogs.findByEntityTypeAndEntityId(entityType, entityId, pr)
            : auditLogs.findAll(pr);
        return PageResponse.of(result).map(a -> new AdminDtos.AuditLogView(
            a.getId(), a.getActorUserId(), a.getActorType().name(),
            a.getEntityType(), a.getEntityId(), a.getAction(), a.getCreatedAt()));
    }

    private AdminDtos.AdminUserView toAdminUserView(User u) {
        UserProfile p = profiles.findById(u.getId()).orElse(null);
        return new AdminDtos.AdminUserView(
            u.getId(), u.getEmail(), u.getPhone(),
            u.getRole().name(), u.getStatus().name(),
            u.getEmailVerifiedAt() != null, u.getPhoneVerifiedAt() != null,
            p != null ? p.getDisplayName() : null,
            u.getCreatedAt());
    }
}
