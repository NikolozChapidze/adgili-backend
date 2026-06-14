package li.adgi.api.admin;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class AdminDtos {

    public record AdminUserView(
        UUID id,
        String email,
        String phone,
        String role,
        String status,
        boolean emailVerified,
        boolean phoneVerified,
        String displayName,
        Instant createdAt
    ) {}

    public record SuspendRequest(@NotBlank String reason) {}

    public record AuditLogView(
        UUID id,
        UUID actorUserId,
        String actorType,
        String entityType,
        UUID entityId,
        String action,
        Instant createdAt
    ) {}

    public record DashboardStats(
        long totalUsers,
        long activeUsers,
        long pendingVerifications,
        long pendingListings,
        long liveListings,
        long pendingBookingRequests
    ) {}
}
