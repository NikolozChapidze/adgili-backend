package li.adgi.api.booking;

import jakarta.persistence.*;
import li.adgi.api.common.BaseEntity;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "listing_id", nullable = false, columnDefinition = "uuid")
    private UUID listingId;

    @Column(name = "host_user_id", nullable = false, columnDefinition = "uuid")
    private UUID hostUserId;

    @Column(name = "renter_user_id", nullable = false, columnDefinition = "uuid")
    private UUID renterUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    // Snapshot of listing terms at request time
    @Column(name = "monthly_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "deposit_amount", precision = 10, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "rental_months", nullable = false)
    private Integer rentalMonths;

    @Column(name = "notice_days_for_cancellation", nullable = false)
    private Integer noticeDaysForCancellation;

    // Timeline
    @Column(name = "requested_move_in_date", nullable = false)
    private LocalDate requestedMoveInDate;

    @Column(name = "actual_move_in_date")
    private LocalDate actualMoveInDate;

    @Column(name = "scheduled_move_out_date")
    private LocalDate scheduledMoveOutDate;

    @Column(name = "actual_move_out_date")
    private LocalDate actualMoveOutDate;

    // Renter context
    @Column(name = "renter_message", columnDefinition = "text")
    private String renterMessage;

    @Column(name = "items_to_store", columnDefinition = "text")
    private String itemsToStore;

    // Host decision
    @Column(name = "host_response_message", columnDefinition = "text")
    private String hostResponseMessage;

    @Column(name = "host_responded_at")
    private Instant hostRespondedAt;

    // Cancellation
    @Column(name = "cancelled_by_user_id", columnDefinition = "uuid")
    private UUID cancelledByUserId;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "request_expires_at", nullable = false)
    private Instant requestExpiresAt;
}
