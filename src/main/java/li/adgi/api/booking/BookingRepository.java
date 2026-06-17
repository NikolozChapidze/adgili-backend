package li.adgi.api.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Page<Booking> findByRenterUserIdOrderByCreatedAtDesc(UUID renterUserId, Pageable pageable);

    Page<Booking> findByRenterUserIdAndStatusOrderByCreatedAtDesc(
            UUID renterUserId, BookingStatus status, Pageable pageable);

    Page<Booking> findByHostUserIdOrderByCreatedAtDesc(UUID hostUserId, Pageable pageable);

    Page<Booking> findByHostUserIdAndStatusOrderByCreatedAtDesc(
            UUID hostUserId, BookingStatus status, Pageable pageable);

    Page<Booking> findByHostUserIdAndListingIdOrderByCreatedAtDesc(
            UUID hostUserId, UUID listingId, Pageable pageable);

    Page<Booking> findByHostUserIdAndListingIdAndStatusOrderByCreatedAtDesc(
            UUID hostUserId, UUID listingId, BookingStatus status, Pageable pageable);

    boolean existsByListingIdAndRenterUserIdAndStatusIn(
            UUID listingId, UUID renterUserId, Collection<BookingStatus> statuses);

    @Query("SELECT b FROM Booking b WHERE b.status = 'REQUESTED' AND b.requestExpiresAt < :now")
    List<Booking> findExpiredRequests(@Param("now") Instant now);

    @Query("SELECT b FROM Booking b WHERE b.status = 'ACTIVE' AND b.scheduledMoveOutDate <= :today")
    List<Booking> findDueToEnd(@Param("today") LocalDate today);
}
