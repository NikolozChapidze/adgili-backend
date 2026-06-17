package li.adgi.api.booking;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class BookingDtos {

    // ========== Requests ==========

    public record CreateBookingRequest(
        @NotNull @Min(1) @Max(24) Integer rentalMonths,
        @NotNull @Future LocalDate requestedMoveInDate,
        @Size(max = 2000) String message,
        @Size(max = 2000) String itemsToStore
    ) {}

    public record AcceptBookingRequest(
        @Size(max = 2000) String message
    ) {}

    public record DeclineBookingRequest(
        @Size(max = 2000) String reason
    ) {}

    public record CancelBookingRequest(
        @Size(max = 2000) String reason
    ) {}

    public record TerminateBookingRequest(
        @NotBlank @Size(max = 2000) String reason
    ) {}

    // ========== Responses ==========

    public record BookingDetail(
        UUID id,
        UUID listingId,
        String listingTitle,
        String listingSlug,
        String listingCoverPhotoUrl,
        String district,
        String neighborhood,

        UUID hostUserId,
        String hostDisplayName,
        String hostAvatarUrl,

        UUID renterUserId,
        String renterDisplayName,
        String renterAvatarUrl,

        String status,

        BigDecimal monthlyPrice,
        String currency,
        BigDecimal depositAmount,
        Integer rentalMonths,
        Integer noticeDaysForCancellation,

        LocalDate requestedMoveInDate,
        LocalDate actualMoveInDate,
        LocalDate scheduledMoveOutDate,
        LocalDate actualMoveOutDate,

        String renterMessage,
        String itemsToStore,
        String hostResponseMessage,
        Instant hostRespondedAt,
        String cancellationReason,
        Instant cancelledAt,
        Instant requestExpiresAt,

        Instant createdAt,
        Instant updatedAt,

        List<BookingEventView> events
    ) {}

    public record BookingEventView(
        UUID id,
        String eventType,
        UUID actorUserId,
        String actorDisplayName,
        JsonNode payload,
        Instant createdAt
    ) {}
}
