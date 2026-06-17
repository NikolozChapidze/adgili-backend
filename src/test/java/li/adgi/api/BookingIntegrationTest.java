package li.adgi.api;

import com.fasterxml.jackson.databind.JsonNode;
import li.adgi.api.auth.AuthDtos;
import li.adgi.api.listing.ListingDtos;
import li.adgi.api.listing.ListingEnums;
import li.adgi.api.notification.Notification;
import li.adgi.api.notification.NotificationRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.web.client.ResponseErrorHandler;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BookingIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationRepository notificationRepo;

    private String hostToken;
    private String renterToken;
    private UUID listingId;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(
            new org.springframework.http.client.JdkClientHttpRequestFactory());
        rest.getRestTemplate().setErrorHandler(new ResponseErrorHandler() {
            @Override public boolean hasError(ClientHttpResponse r) { return false; }
            @Override public void handleError(ClientHttpResponse r) {}
        });

        // Wipe all data in FK-safe order
        jdbc.execute("TRUNCATE TABLE booking_events, bookings, " +
            "listing_features, listing_rules, listing_photos, listings, " +
            "audit_logs, notifications, identity_verifications, " +
            "host_profiles, renter_profiles, user_profiles, users RESTART IDENTITY CASCADE");

        // Create and verify host user
        var hostAuth = registerAndVerify("host@booking.test", "+995599000001", "Giorgi", "Host");
        hostToken = hostAuth.tokens().accessToken();

        var renterAuth = registerAndVerify("renter@booking.test", "+995599000002", "Mariam", "Renter");
        renterToken = renterAuth.tokens().accessToken();

        // Create a listing via API then force it to LIVE via JDBC
        listingId = createLiveListing(hostToken);
    }

    // -----------------------------------------------------------------------
    // Test 1: Renter creates booking → host accepts → ACCEPTED
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Renter books → host accepts → status ACCEPTED, host event recorded")
    void renterBooksAndHostAccepts() {
        UUID bookingId = createBookingRequest(renterToken, listingId);

        // Host accepts
        var resp = post("/api/v1/host/bookings/" + bookingId + "/accept",
            Map.of("message", "Welcome! Looking forward to it."), hostToken, JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(resp.getBody().get("hostResponseMessage").asText())
            .isEqualTo("Welcome! Looking forward to it.");

        // Renter can see it via their endpoint
        var renterView = get("/api/v1/renter/bookings/" + bookingId, renterToken, JsonNode.class);
        assertThat(renterView.getBody().get("status").asText()).isEqualTo("ACCEPTED");

        // Events should include REQUESTED + ACCEPTED
        var events = renterView.getBody().get("events");
        assertThat(events.size()).isEqualTo(2);
        assertThat(events.get(0).get("eventType").asText()).isEqualTo("REQUESTED");
        assertThat(events.get(1).get("eventType").asText()).isEqualTo("ACCEPTED");
    }

    // -----------------------------------------------------------------------
    // Test 2: Renter confirms move-in → ACTIVE, listing.currently_rented = true
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Renter confirms move-in → ACTIVE, listing marked currently_rented")
    void confirmMoveInActivatesBookingAndMarksList() {
        UUID bookingId = createBookingRequest(renterToken, listingId);
        post("/api/v1/host/bookings/" + bookingId + "/accept", null, hostToken, JsonNode.class);

        var resp = post("/api/v1/renter/bookings/" + bookingId + "/confirm-move-in",
            null, renterToken, JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status").asText()).isEqualTo("ACTIVE");
        assertThat(resp.getBody().get("actualMoveInDate").asText()).isNotBlank();

        Boolean rented = jdbc.queryForObject(
            "SELECT currently_rented FROM listings WHERE id = ?::uuid",
            Boolean.class, listingId.toString());
        assertThat(rented).isTrue();
    }

    // -----------------------------------------------------------------------
    // Test 3: Host declines → DECLINED, listing.currently_rented unchanged (false)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Host declines → DECLINED, listing.currently_rented stays false")
    void hostDeclines() {
        UUID bookingId = createBookingRequest(renterToken, listingId);

        var resp = post("/api/v1/host/bookings/" + bookingId + "/decline",
            Map.of("reason", "Space no longer available"), hostToken, JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status").asText()).isEqualTo("DECLINED");

        Boolean rented = jdbc.queryForObject(
            "SELECT currently_rented FROM listings WHERE id = ?::uuid",
            Boolean.class, listingId.toString());
        assertThat(rented).isFalse();
    }

    // -----------------------------------------------------------------------
    // Test 4: Renter cancels REQUESTED booking → CANCELLED
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Renter cancels REQUESTED booking → CANCELLED")
    void renterCancelsRequest() {
        UUID bookingId = createBookingRequest(renterToken, listingId);

        var resp = post("/api/v1/renter/bookings/" + bookingId + "/cancel",
            Map.of("reason", "Changed my mind"), renterToken, JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status").asText()).isEqualTo("CANCELLED");
        assertThat(resp.getBody().get("cancellationReason").asText()).isEqualTo("Changed my mind");
    }

    // -----------------------------------------------------------------------
    // Test 5: Renter tries to book their own listing → 400
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Renter cannot book their own listing → 400 SELF_BOOKING")
    void selfBookingRejected() {
        // Host tries to book the listing they own
        var resp = post("/api/v1/listings/" + listingId + "/book",
            Map.of("rentalMonths", 3, "requestedMoveInDate", LocalDate.now().plusDays(10).toString()),
            hostToken, JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("code").asText()).isEqualTo("SELF_BOOKING");
    }

    // -----------------------------------------------------------------------
    // Test 6: Renter tries to book an occupied listing → 409
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Renter cannot book a currently_rented listing → 409 LISTING_OCCUPIED")
    void occupiedListingRejected() {
        // Force listing to currently_rented = true
        jdbc.update("UPDATE listings SET currently_rented = TRUE WHERE id = ?::uuid",
            listingId.toString());

        var resp = post("/api/v1/listings/" + listingId + "/book",
            Map.of("rentalMonths", 2, "requestedMoveInDate", LocalDate.now().plusDays(5).toString()),
            renterToken, JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().get("code").asText()).isEqualTo("LISTING_OCCUPIED");
    }

    // -----------------------------------------------------------------------
    // Test 7: Renter tries to book a non-LIVE listing → 404
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Booking a non-LIVE listing returns 404")
    void nonLiveListingReturns404() {
        jdbc.update("UPDATE listings SET listing_status = 'PAUSED' WHERE id = ?::uuid",
            listingId.toString());

        var resp = post("/api/v1/listings/" + listingId + "/book",
            Map.of("rentalMonths", 2, "requestedMoveInDate", LocalDate.now().plusDays(5).toString()),
            renterToken, JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -----------------------------------------------------------------------
    // Test 8: Unverified renter tries to book → 403
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Unverified renter cannot book → 403")
    void unverifiedRenterRejected() {
        // Register user without verifying email/phone
        var body = new AuthDtos.RegisterRequest(
            "unverified@booking.test", "+995599999099", "Password123!", "No", "Verify", true, true);
        var regResp = rest.postForEntity("/api/v1/auth/register", body, AuthDtos.AuthResponse.class);
        String unverifiedToken = regResp.getBody().tokens().accessToken();

        var resp = post("/api/v1/listings/" + listingId + "/book",
            Map.of("rentalMonths", 2, "requestedMoveInDate", LocalDate.now().plusDays(5).toString()),
            unverifiedToken, JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // Test 9: Scheduler expires a 48h+ old REQUESTED booking
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Expiry scheduler transitions REQUESTED past 48h → EXPIRED")
    void schedulerExpiresStaleRequest(@Autowired li.adgi.api.booking.BookingScheduler scheduler) {
        UUID bookingId = createBookingRequest(renterToken, listingId);

        // Wind the expiry time back past 48h
        jdbc.update("UPDATE bookings SET request_expires_at = NOW() - INTERVAL '1 hour' WHERE id = ?::uuid",
            bookingId.toString());

        scheduler.expireStaleRequests();

        String status = jdbc.queryForObject(
            "SELECT status FROM bookings WHERE id = ?::uuid", String.class, bookingId.toString());
        assertThat(status).isEqualTo("EXPIRED");
    }

    // -----------------------------------------------------------------------
    // Test 10: Scheduler auto-ends ACTIVE booking past scheduled_move_out_date
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Auto-end scheduler transitions ACTIVE past move-out date → ENDED")
    void schedulerAutoEndsBooking(@Autowired li.adgi.api.booking.BookingScheduler scheduler) {
        UUID bookingId = createBookingRequest(renterToken, listingId);
        post("/api/v1/host/bookings/" + bookingId + "/accept", null, hostToken, JsonNode.class);
        post("/api/v1/renter/bookings/" + bookingId + "/confirm-move-in", null, renterToken, JsonNode.class);

        // Wind move-out date to yesterday
        jdbc.update("UPDATE bookings SET scheduled_move_out_date = CURRENT_DATE - 1 WHERE id = ?::uuid",
            bookingId.toString());

        scheduler.autoEndCompletedBookings();

        String status = jdbc.queryForObject(
            "SELECT status FROM bookings WHERE id = ?::uuid", String.class, bookingId.toString());
        assertThat(status).isEqualTo("ENDED");

        Boolean rented = jdbc.queryForObject(
            "SELECT currently_rented FROM listings WHERE id = ?::uuid",
            Boolean.class, listingId.toString());
        assertThat(rented).isFalse();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private AuthDtos.AuthResponse registerAndVerify(String email, String phone,
                                                     String firstName, String lastName) {
        var req = new AuthDtos.RegisterRequest(email, phone, "Password123!", firstName, lastName, true, true);
        var regResp = rest.postForEntity("/api/v1/auth/register", req, AuthDtos.AuthResponse.class);
        assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthDtos.AuthResponse auth = regResp.getBody();

        // Verify email via token from notification
        String emailToken = notificationRepo.findAll().stream()
            .filter(n -> n.getUserId().equals(auth.user().id())
                && n.getChannel() == Notification.Channel.EMAIL)
            .findFirst()
            .map(n -> n.getPayload().get("token").asText())
            .orElseThrow(() -> new AssertionError("Email token notification not found"));

        rest.postForEntity("/api/v1/auth/verify-email",
            new AuthDtos.VerifyEmailRequest(emailToken), Void.class);

        // Verify phone (OTP is 000000 in test profile)
        HttpHeaders h = bearerHeaders(auth.tokens().accessToken());
        rest.exchange("/api/v1/auth/verify-phone-otp", HttpMethod.POST,
            new HttpEntity<>(new AuthDtos.VerifyPhoneOtpRequest(phone, "000000"), h), Void.class);

        return auth;
    }

    private UUID createLiveListing(String token) {
        var location = new ListingDtos.LocationRequest(
            "Vake", null, null, null, null,
            new BigDecimal("41.709400"), new BigDecimal("44.758900"));
        var req = new ListingDtos.CreateListingRequest(
            "Test Garage", "A test garage for integration tests",
            ListingEnums.SpaceType.GARAGE,
            new BigDecimal("300.00"), "GEL", null, 1, 30,
            location, null, null, null, null, null);

        var resp = post("/api/v1/listings", req, token, JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID id = UUID.fromString(resp.getBody().get("id").asText());

        jdbc.update("UPDATE listings SET listing_status = 'LIVE', moderation_status = 'APPROVED', " +
            "published_at = NOW() WHERE id = ?::uuid", id.toString());
        return id;
    }

    private UUID createBookingRequest(String token, UUID targetListingId) {
        var resp = post("/api/v1/listings/" + targetListingId + "/book",
            Map.of("rentalMonths", 3,
                "requestedMoveInDate", LocalDate.now().plusDays(10).toString()),
            token, JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(resp.getBody().get("id").asText());
    }

    private <T> ResponseEntity<T> post(String url, Object body, String token, Class<T> type) {
        HttpHeaders h = bearerHeaders(token);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), type);
    }

    private <T> ResponseEntity<T> get(String url, String token, Class<T> type) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(bearerHeaders(token)), type);
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
