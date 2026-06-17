package li.adgi.api.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import li.adgi.api.common.PageResponse;
import li.adgi.api.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Bookings (Renter)", description = "Renter-facing booking endpoints")
public class BookingController {

    private final BookingService service;
    private final CurrentUser currentUser;

    @PostMapping("/api/v1/listings/{listingId}/book")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request to book a listing")
    public BookingDtos.BookingDetail create(
            @PathVariable UUID listingId,
            @Valid @RequestBody BookingDtos.CreateBookingRequest req) {
        return service.create(currentUser.get().userId(), listingId, req);
    }

    @GetMapping("/api/v1/renter/bookings")
    @Operation(summary = "List my bookings as renter")
    public PageResponse<BookingDtos.BookingDetail> list(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.listForRenter(currentUser.get().userId(), status,
            PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.of(result);
    }

    @GetMapping("/api/v1/renter/bookings/{bookingId}")
    @Operation(summary = "Get booking detail as renter")
    public BookingDtos.BookingDetail get(@PathVariable UUID bookingId) {
        return service.getForRenter(currentUser.get().userId(), bookingId);
    }

    @PostMapping("/api/v1/renter/bookings/{bookingId}/cancel")
    @Operation(summary = "Cancel a REQUESTED or ACCEPTED booking")
    public BookingDtos.BookingDetail cancel(
            @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) BookingDtos.CancelBookingRequest req) {
        return service.cancelByRenter(currentUser.get().userId(), bookingId, req);
    }

    @PostMapping("/api/v1/renter/bookings/{bookingId}/confirm-move-in")
    @Operation(summary = "Confirm move-in, activating the booking")
    public BookingDtos.BookingDetail confirmMoveIn(@PathVariable UUID bookingId) {
        return service.confirmMoveIn(currentUser.get().userId(), bookingId);
    }
}
