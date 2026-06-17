package li.adgi.api.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import li.adgi.api.common.PageResponse;
import li.adgi.api.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Bookings (Host)", description = "Host-facing booking management endpoints")
public class HostBookingController {

    private final BookingService service;
    private final CurrentUser currentUser;

    @GetMapping("/api/v1/host/bookings")
    @Operation(summary = "List bookings for my listings")
    public PageResponse<BookingDtos.BookingDetail> list(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) UUID listingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.listForHost(currentUser.get().userId(), status, listingId,
            PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.of(result);
    }

    @GetMapping("/api/v1/host/bookings/{bookingId}")
    @Operation(summary = "Get booking detail as host")
    public BookingDtos.BookingDetail get(@PathVariable UUID bookingId) {
        return service.getForHost(currentUser.get().userId(), bookingId);
    }

    @PostMapping("/api/v1/host/bookings/{bookingId}/accept")
    @Operation(summary = "Accept a REQUESTED booking")
    public BookingDtos.BookingDetail accept(
            @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) BookingDtos.AcceptBookingRequest req) {
        return service.accept(currentUser.get().userId(), bookingId, req);
    }

    @PostMapping("/api/v1/host/bookings/{bookingId}/decline")
    @Operation(summary = "Decline a REQUESTED booking")
    public BookingDtos.BookingDetail decline(
            @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) BookingDtos.DeclineBookingRequest req) {
        return service.decline(currentUser.get().userId(), bookingId, req);
    }

    @PostMapping("/api/v1/host/bookings/{bookingId}/terminate")
    @Operation(summary = "Terminate an ACTIVE booking")
    public BookingDtos.BookingDetail terminate(
            @PathVariable UUID bookingId,
            @Valid @RequestBody BookingDtos.TerminateBookingRequest req) {
        return service.terminate(currentUser.get().userId(), bookingId, req);
    }
}
