package li.adgi.api.booking;

import li.adgi.api.audit.AuditService;
import li.adgi.api.common.ApiException;
import li.adgi.api.listing.Listing;
import li.adgi.api.listing.ListingEnums;
import li.adgi.api.listing.ListingPhoto;
import li.adgi.api.listing.ListingPhotoRepository;
import li.adgi.api.listing.ListingRepository;
import li.adgi.api.notification.NotificationService;
import li.adgi.api.user.User;
import li.adgi.api.user.UserProfile;
import li.adgi.api.user.UserProfileRepository;
import li.adgi.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookings;
    private final BookingEventRepository bookingEvents;
    private final ListingRepository listings;
    private final ListingPhotoRepository photos;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final NotificationService notifications;
    private final AuditService audit;

    // -----------------------------------------------------------------------
    // Renter: create booking request
    // -----------------------------------------------------------------------

    @Transactional
    public BookingDtos.BookingDetail create(UUID renterId, UUID listingId,
                                            BookingDtos.CreateBookingRequest req) {
        Listing listing = listings.findById(listingId)
            .orElseThrow(() -> ApiException.notFound("Listing"));

        if (listing.getListingStatus() != ListingEnums.ListingStatus.LIVE) {
            throw ApiException.notFound("Listing");
        }

        User renter = users.findById(renterId)
            .orElseThrow(() -> ApiException.notFound("User"));
        if (!renter.isVerified()) {
            throw ApiException.forbidden("Must verify email and phone before booking");
        }

        if (listing.getHostUserId().equals(renterId)) {
            throw ApiException.badRequest("SELF_BOOKING", "Cannot book your own listing");
        }

        if (Boolean.TRUE.equals(listing.getCurrentlyRented())) {
            throw ApiException.conflict("LISTING_OCCUPIED", "This listing is currently rented");
        }

        boolean hasActiveBooking = bookings.existsByListingIdAndRenterUserIdAndStatusIn(
            listingId, renterId,
            List.of(BookingStatus.REQUESTED, BookingStatus.ACCEPTED, BookingStatus.ACTIVE));
        if (hasActiveBooking) {
            throw ApiException.conflict("DUPLICATE_BOOKING",
                "You already have an active booking for this listing");
        }

        Booking booking = Booking.builder()
            .listingId(listingId)
            .hostUserId(listing.getHostUserId())
            .renterUserId(renterId)
            .status(BookingStatus.REQUESTED)
            .monthlyPrice(listing.getMonthlyPrice())
            .currency(listing.getCurrency())
            .depositAmount(listing.getDepositAmount())
            .rentalMonths(req.rentalMonths())
            .noticeDaysForCancellation(listing.getNoticeDaysForCancellation())
            .requestedMoveInDate(req.requestedMoveInDate())
            .scheduledMoveOutDate(req.requestedMoveInDate().plusMonths(req.rentalMonths()))
            .renterMessage(req.message())
            .itemsToStore(req.itemsToStore())
            .requestExpiresAt(Instant.now().plus(48, ChronoUnit.HOURS))
            .build();

        booking = bookings.save(booking);
        addEvent(booking.getId(), renterId, BookingEventType.REQUESTED);

        String renterName = displayName(renterId);
        notifications.sendInApp(listing.getHostUserId(), BookingNotificationTemplates.BOOKING_REQUESTED,
            "New booking request",
            Map.of("bookingId", booking.getId().toString(),
                "listingId", listingId.toString(),
                "listingTitle", listing.getTitle(),
                "renterDisplayName", renterName));

        notifications.sendInApp(renterId, BookingNotificationTemplates.BOOKING_SUBMITTED,
            "Booking request submitted",
            Map.of("bookingId", booking.getId().toString(),
                "listingId", listingId.toString(),
                "listingTitle", listing.getTitle()));

        audit.log(renterId, "BOOKING", booking.getId(), "REQUESTED", null,
            Map.of("listingId", listingId.toString(), "rentalMonths", req.rentalMonths()));

        return toDetail(booking);
    }

    // -----------------------------------------------------------------------
    // Renter: cancel own request (REQUESTED or ACCEPTED)
    // -----------------------------------------------------------------------

    @Transactional
    public BookingDtos.BookingDetail cancelByRenter(UUID renterId, UUID bookingId,
                                                     BookingDtos.CancelBookingRequest req) {
        Booking booking = requireBooking(bookingId);
        requireRenter(booking, renterId);

        if (booking.getStatus() != BookingStatus.REQUESTED
                && booking.getStatus() != BookingStatus.ACCEPTED) {
            throw ApiException.conflict("BAD_STATE",
                "Only REQUESTED or ACCEPTED bookings can be cancelled this way");
        }

        Instant now = Instant.now();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledByUserId(renterId);
        booking.setCancellationReason(req == null ? null : req.reason());
        booking.setCancelledAt(now);
        bookings.save(booking);

        addEvent(bookingId, renterId, BookingEventType.CANCELLED);

        Listing listing = listingOrNull(booking.getListingId());
        String listingTitle = listing != null ? listing.getTitle() : "";
        notifications.sendInApp(booking.getHostUserId(),
            BookingNotificationTemplates.BOOKING_CANCELLED_BY_RENTER,
            "Booking cancelled by renter",
            Map.of("bookingId", bookingId.toString(),
                "listingId", booking.getListingId().toString(),
                "listingTitle", listingTitle));

        audit.log(renterId, "BOOKING", bookingId, "CANCELLED_BY_RENTER", null, null);
        return toDetail(booking);
    }

    // -----------------------------------------------------------------------
    // Renter: confirm move-in (ACCEPTED → ACTIVE)
    // -----------------------------------------------------------------------

    @Transactional
    public BookingDtos.BookingDetail confirmMoveIn(UUID renterId, UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        requireRenter(booking, renterId);

        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            throw ApiException.conflict("BAD_STATE", "Booking must be ACCEPTED to confirm move-in");
        }

        booking.setStatus(BookingStatus.ACTIVE);
        booking.setActualMoveInDate(LocalDate.now());
        bookings.save(booking);

        setCurrentlyRented(booking.getListingId(), true);
        addEvent(bookingId, renterId, BookingEventType.MOVED_IN);

        Listing listing = listingOrNull(booking.getListingId());
        String listingTitle = listing != null ? listing.getTitle() : "";
        notifications.sendInApp(booking.getHostUserId(),
            BookingNotificationTemplates.BOOKING_MOVED_IN,
            "Renter has moved in",
            Map.of("bookingId", bookingId.toString(),
                "listingId", booking.getListingId().toString(),
                "listingTitle", listingTitle,
                "renterDisplayName", displayName(renterId)));

        audit.log(renterId, "BOOKING", bookingId, "MOVED_IN", null, null);
        return toDetail(booking);
    }

    // -----------------------------------------------------------------------
    // Renter: list bookings
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<BookingDtos.BookingDetail> listForRenter(UUID renterId,
                                                          BookingStatus status, Pageable pageable) {
        Page<Booking> page = status == null
            ? bookings.findByRenterUserIdOrderByCreatedAtDesc(renterId, pageable)
            : bookings.findByRenterUserIdAndStatusOrderByCreatedAtDesc(renterId, status, pageable);
        return page.map(this::toDetail);
    }

    @Transactional(readOnly = true)
    public BookingDtos.BookingDetail getForRenter(UUID renterId, UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        requireRenter(booking, renterId);
        return toDetail(booking);
    }

    // -----------------------------------------------------------------------
    // Host: accept (REQUESTED → ACCEPTED)
    // -----------------------------------------------------------------------

    @Transactional
    public BookingDtos.BookingDetail accept(UUID hostId, UUID bookingId,
                                             BookingDtos.AcceptBookingRequest req) {
        Booking booking = requireBooking(bookingId);
        requireHost(booking, hostId);

        if (booking.getStatus() != BookingStatus.REQUESTED) {
            throw ApiException.conflict("BAD_STATE", "Only REQUESTED bookings can be accepted");
        }

        Instant now = Instant.now();
        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setHostResponseMessage(req == null ? null : req.message());
        booking.setHostRespondedAt(now);
        bookings.save(booking);

        addEvent(bookingId, hostId, BookingEventType.ACCEPTED);

        Listing listing = listingOrNull(booking.getListingId());
        String listingTitle = listing != null ? listing.getTitle() : "";
        notifications.sendInApp(booking.getRenterUserId(),
            BookingNotificationTemplates.BOOKING_ACCEPTED,
            "Your booking was accepted",
            Map.of("bookingId", bookingId.toString(),
                "listingId", booking.getListingId().toString(),
                "listingTitle", listingTitle,
                "hostMessage", req != null && req.message() != null ? req.message() : ""));

        audit.log(hostId, "BOOKING", bookingId, "ACCEPTED", null, null);
        return toDetail(booking);
    }

    // -----------------------------------------------------------------------
    // Host: decline (REQUESTED → DECLINED)
    // -----------------------------------------------------------------------

    @Transactional
    public BookingDtos.BookingDetail decline(UUID hostId, UUID bookingId,
                                              BookingDtos.DeclineBookingRequest req) {
        Booking booking = requireBooking(bookingId);
        requireHost(booking, hostId);

        if (booking.getStatus() != BookingStatus.REQUESTED) {
            throw ApiException.conflict("BAD_STATE", "Only REQUESTED bookings can be declined");
        }

        Instant now = Instant.now();
        booking.setStatus(BookingStatus.DECLINED);
        booking.setHostResponseMessage(req == null ? null : req.reason());
        booking.setHostRespondedAt(now);
        bookings.save(booking);

        addEvent(bookingId, hostId, BookingEventType.DECLINED);

        Listing listing = listingOrNull(booking.getListingId());
        String listingTitle = listing != null ? listing.getTitle() : "";
        notifications.sendInApp(booking.getRenterUserId(),
            BookingNotificationTemplates.BOOKING_DECLINED,
            "Your booking was declined",
            Map.of("bookingId", bookingId.toString(),
                "listingId", booking.getListingId().toString(),
                "listingTitle", listingTitle,
                "hostMessage", req != null && req.reason() != null ? req.reason() : ""));

        audit.log(hostId, "BOOKING", bookingId, "DECLINED", null, null);
        return toDetail(booking);
    }

    // -----------------------------------------------------------------------
    // Host: terminate (ACTIVE → TERMINATED)
    // -----------------------------------------------------------------------

    @Transactional
    public BookingDtos.BookingDetail terminate(UUID hostId, UUID bookingId,
                                                BookingDtos.TerminateBookingRequest req) {
        Booking booking = requireBooking(bookingId);
        requireHost(booking, hostId);

        if (booking.getStatus() != BookingStatus.ACTIVE) {
            throw ApiException.conflict("BAD_STATE", "Only ACTIVE bookings can be terminated");
        }

        Instant now = Instant.now();
        booking.setStatus(BookingStatus.TERMINATED);
        booking.setActualMoveOutDate(LocalDate.now());
        booking.setCancellationReason(req.reason());
        booking.setCancelledAt(now);
        booking.setCancelledByUserId(hostId);
        bookings.save(booking);

        setCurrentlyRented(booking.getListingId(), false);
        addEvent(bookingId, hostId, BookingEventType.TERMINATED);

        Listing listing = listingOrNull(booking.getListingId());
        String listingTitle = listing != null ? listing.getTitle() : "";
        notifications.sendInApp(booking.getRenterUserId(),
            BookingNotificationTemplates.BOOKING_TERMINATED,
            "Your booking was terminated",
            Map.of("bookingId", bookingId.toString(),
                "listingId", booking.getListingId().toString(),
                "listingTitle", listingTitle,
                "reason", req.reason()));

        audit.log(hostId, "BOOKING", bookingId, "TERMINATED", null,
            Map.of("reason", req.reason()));
        return toDetail(booking);
    }

    // -----------------------------------------------------------------------
    // Host: list bookings
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<BookingDtos.BookingDetail> listForHost(UUID hostId, BookingStatus status,
                                                        UUID listingId, Pageable pageable) {
        Page<Booking> page;
        if (listingId != null && status != null) {
            page = bookings.findByHostUserIdAndListingIdAndStatusOrderByCreatedAtDesc(
                hostId, listingId, status, pageable);
        } else if (listingId != null) {
            page = bookings.findByHostUserIdAndListingIdOrderByCreatedAtDesc(hostId, listingId, pageable);
        } else if (status != null) {
            page = bookings.findByHostUserIdAndStatusOrderByCreatedAtDesc(hostId, status, pageable);
        } else {
            page = bookings.findByHostUserIdOrderByCreatedAtDesc(hostId, pageable);
        }
        return page.map(this::toDetail);
    }

    @Transactional(readOnly = true)
    public BookingDtos.BookingDetail getForHost(UUID hostId, UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        requireHost(booking, hostId);
        return toDetail(booking);
    }

    // -----------------------------------------------------------------------
    // Scheduler callbacks (called from BookingScheduler, already transactional)
    // -----------------------------------------------------------------------

    @Transactional
    public void expireRequest(Booking booking) {
        booking.setStatus(BookingStatus.EXPIRED);
        bookings.save(booking);
        addEvent(booking.getId(), null, BookingEventType.EXPIRED);

        Listing listing = listingOrNull(booking.getListingId());
        String listingTitle = listing != null ? listing.getTitle() : "";
        Map<String, Object> payload = Map.of(
            "bookingId", booking.getId().toString(),
            "listingId", booking.getListingId().toString(),
            "listingTitle", listingTitle);

        notifications.sendInApp(booking.getRenterUserId(),
            BookingNotificationTemplates.BOOKING_EXPIRED, "Booking request expired", payload);
        notifications.sendInApp(booking.getHostUserId(),
            BookingNotificationTemplates.BOOKING_EXPIRED_FOR_HOST, "Booking request expired", payload);

        audit.log(null, "BOOKING", booking.getId(), "EXPIRED", null, null);
    }

    @Transactional
    public void endBooking(Booking booking) {
        booking.setStatus(BookingStatus.ENDED);
        booking.setActualMoveOutDate(LocalDate.now());
        bookings.save(booking);

        setCurrentlyRented(booking.getListingId(), false);
        addEvent(booking.getId(), null, BookingEventType.ENDED);

        Listing listing = listingOrNull(booking.getListingId());
        String listingTitle = listing != null ? listing.getTitle() : "";
        Map<String, Object> payload = Map.of(
            "bookingId", booking.getId().toString(),
            "listingId", booking.getListingId().toString(),
            "listingTitle", listingTitle);

        notifications.sendInApp(booking.getRenterUserId(),
            BookingNotificationTemplates.BOOKING_ENDED, "Your rental period has ended", payload);
        notifications.sendInApp(booking.getHostUserId(),
            BookingNotificationTemplates.BOOKING_ENDED_FOR_HOST, "Rental period ended", payload);

        audit.log(null, "BOOKING", booking.getId(), "ENDED", null, null);
    }

    // -----------------------------------------------------------------------
    // DTO assembly
    // -----------------------------------------------------------------------

    public BookingDtos.BookingDetail toDetail(Booking b) {
        Listing listing = listingOrNull(b.getListingId());
        String listingTitle = listing != null ? listing.getTitle() : null;
        String listingSlug = listing != null ? listing.getSlug() : null;
        String district = listing != null ? listing.getDistrict() : null;
        String neighborhood = listing != null ? listing.getNeighborhood() : null;
        String coverPhotoUrl = listing != null ? coverPhoto(b.getListingId()) : null;

        UserProfile hostProfile = profiles.findById(b.getHostUserId()).orElse(null);
        UserProfile renterProfile = profiles.findById(b.getRenterUserId()).orElse(null);

        List<BookingDtos.BookingEventView> events = bookingEvents
            .findByBookingIdOrderByCreatedAtAsc(b.getId())
            .stream()
            .map(e -> {
                String actorName = e.getActorUserId() != null
                    ? displayName(e.getActorUserId()) : null;
                return new BookingDtos.BookingEventView(
                    e.getId(), e.getEventType().name(), e.getActorUserId(),
                    actorName, e.getPayload(), e.getCreatedAt());
            })
            .toList();

        return new BookingDtos.BookingDetail(
            b.getId(), b.getListingId(), listingTitle, listingSlug, coverPhotoUrl, district, neighborhood,
            b.getHostUserId(),
            hostProfile != null ? hostProfile.getDisplayName() : null,
            hostProfile != null ? hostProfile.getProfilePhotoUrl() : null,
            b.getRenterUserId(),
            renterProfile != null ? renterProfile.getDisplayName() : null,
            renterProfile != null ? renterProfile.getProfilePhotoUrl() : null,
            b.getStatus().name(),
            b.getMonthlyPrice(), b.getCurrency(), b.getDepositAmount(),
            b.getRentalMonths(), b.getNoticeDaysForCancellation(),
            b.getRequestedMoveInDate(), b.getActualMoveInDate(),
            b.getScheduledMoveOutDate(), b.getActualMoveOutDate(),
            b.getRenterMessage(), b.getItemsToStore(),
            b.getHostResponseMessage(), b.getHostRespondedAt(),
            b.getCancellationReason(), b.getCancelledAt(),
            b.getRequestExpiresAt(),
            b.getCreatedAt(), b.getUpdatedAt(),
            events
        );
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Booking requireBooking(UUID bookingId) {
        return bookings.findById(bookingId)
            .orElseThrow(() -> ApiException.notFound("Booking"));
    }

    private void requireHost(Booking booking, UUID hostId) {
        if (!booking.getHostUserId().equals(hostId)) {
            throw ApiException.forbidden("You are not the host of this booking");
        }
    }

    private void requireRenter(Booking booking, UUID renterId) {
        if (!booking.getRenterUserId().equals(renterId)) {
            throw ApiException.forbidden("You are not the renter of this booking");
        }
    }

    private void setCurrentlyRented(UUID listingId, boolean rented) {
        listings.findById(listingId).ifPresent(l -> {
            l.setCurrentlyRented(rented);
            listings.save(l);
        });
    }

    private void addEvent(UUID bookingId, UUID actorId, BookingEventType type) {
        bookingEvents.save(BookingEvent.builder()
            .bookingId(bookingId)
            .actorUserId(actorId)
            .eventType(type)
            .build());
    }

    private Listing listingOrNull(UUID listingId) {
        return listings.findById(listingId).orElse(null);
    }

    private String coverPhoto(UUID listingId) {
        return photos.findByListingIdOrderBySortOrderAsc(listingId).stream()
            .filter(p -> Boolean.TRUE.equals(p.getIsCover()))
            .findFirst()
            .map(ListingPhoto::getFileUrl)
            .orElseGet(() -> photos.findByListingIdOrderBySortOrderAsc(listingId)
                .stream().findFirst().map(ListingPhoto::getFileUrl).orElse(null));
    }

    private String displayName(UUID userId) {
        UserProfile p = profiles.findById(userId).orElse(null);
        return p != null ? p.getDisplayName() : "User";
    }
}
