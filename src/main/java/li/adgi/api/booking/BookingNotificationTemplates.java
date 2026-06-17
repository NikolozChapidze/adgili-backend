package li.adgi.api.booking;

public final class BookingNotificationTemplates {

    private BookingNotificationTemplates() {}

    // Sent to host
    public static final String BOOKING_REQUESTED         = "booking.requested";
    public static final String BOOKING_CANCELLED_BY_RENTER = "booking.cancelled_by_renter";
    public static final String BOOKING_MOVED_IN          = "booking.moved_in";
    public static final String BOOKING_EXPIRED_FOR_HOST  = "booking.expired_for_host";
    public static final String BOOKING_ENDED_FOR_HOST    = "booking.ended_for_host";

    // Sent to renter
    public static final String BOOKING_SUBMITTED         = "booking.submitted";
    public static final String BOOKING_ACCEPTED          = "booking.accepted";
    public static final String BOOKING_DECLINED          = "booking.declined";
    public static final String BOOKING_CANCELLED_BY_HOST = "booking.cancelled_by_host";
    public static final String BOOKING_EXPIRED           = "booking.expired";
    public static final String BOOKING_TERMINATED        = "booking.terminated";
    public static final String BOOKING_ENDED             = "booking.ended";
}
