package li.adgi.api.booking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingScheduler {

    private final BookingRepository bookingRepo;
    private final BookingService bookingService;

    /**
     * Expires REQUESTED bookings that passed their 48-hour window without a host response.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void expireStaleRequests() {
        List<Booking> expired = bookingRepo.findExpiredRequests(Instant.now());
        if (expired.isEmpty()) return;

        log.info("Expiring {} stale booking request(s)", expired.size());
        for (Booking b : expired) {
            try {
                bookingService.expireRequest(b);
            } catch (Exception e) {
                log.error("Failed to expire booking {}: {}", b.getId(), e.getMessage());
            }
        }
    }

    /**
     * Ends ACTIVE bookings whose scheduled move-out date has passed.
     * Runs daily at midnight.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void autoEndCompletedBookings() {
        List<Booking> due = bookingRepo.findDueToEnd(LocalDate.now());
        if (due.isEmpty()) return;

        log.info("Auto-ending {} booking(s)", due.size());
        for (Booking b : due) {
            try {
                bookingService.endBooking(b);
            } catch (Exception e) {
                log.error("Failed to auto-end booking {}: {}", b.getId(), e.getMessage());
            }
        }
    }
}
