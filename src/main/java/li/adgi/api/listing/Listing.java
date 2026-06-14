package li.adgi.api.listing;

import jakarta.persistence.*;
import li.adgi.api.common.BaseEntity;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Listing extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "host_user_id", nullable = false, columnDefinition = "uuid")
    private UUID hostUserId;

    @Column(nullable = false, unique = true, length = 255)
    private String slug;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_status", nullable = false, length = 30)
    private ListingEnums.ListingStatus listingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 30)
    private ListingEnums.ModerationStatus moderationStatus;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "space_type", nullable = false, length = 50)
    private ListingEnums.SpaceType spaceType;

    @Column(name = "monthly_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "deposit_amount", precision = 12, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "min_rental_months", nullable = false)
    private Integer minRentalMonths;

    @Column(name = "notice_days_for_cancellation", nullable = false)
    private Integer noticeDaysForCancellation;

    @Column(name = "instant_book_enabled", nullable = false)
    private Boolean instantBookEnabled;

    // Location — approximate (public)
    @Column(name = "approximate_latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal approximateLatitude;

    @Column(name = "approximate_longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal approximateLongitude;

    @Column(name = "approximate_location", nullable = false,
            columnDefinition = "geography(Point,4326)")
    private Point approximateLocation;

    // Location — exact (private, revealed post-booking)
    @Column(name = "exact_latitude", precision = 9, scale = 6)
    private BigDecimal exactLatitude;

    @Column(name = "exact_longitude", precision = 9, scale = 6)
    private BigDecimal exactLongitude;

    @Column(name = "exact_location", columnDefinition = "geography(Point,4326)")
    private Point exactLocation;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(length = 100)
    private String neighborhood;

    @Column(name = "address_line_1", length = 255)
    private String addressLine1;

    @Column(name = "address_line_2", length = 255)
    private String addressLine2;

    @Column(name = "postal_code", length = 50)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "width_m", precision = 8, scale = 2)  private BigDecimal widthM;
    @Column(name = "length_m", precision = 8, scale = 2) private BigDecimal lengthM;
    @Column(name = "height_m", precision = 8, scale = 2) private BigDecimal heightM;
    @Column(name = "area_sqm", precision = 8, scale = 2) private BigDecimal areaSqm;

    @Column(name = "indoor_flag", nullable = false)   private Boolean indoorFlag;
    @Column(name = "enclosed_flag", nullable = false) private Boolean enclosedFlag;
    @Column(name = "ground_floor_flag")               private Boolean groundFloorFlag;
    @Column(name = "humidity_notes", columnDefinition = "text") private String humidityNotes;

    @Column(name = "access_hours_text", length = 255)  private String accessHoursText;
    @Column(name = "access_24_7_flag", nullable = false) private Boolean access247Flag;
    @Column(name = "access_notice_hours", nullable = false) private Integer accessNoticeHours;
    @Column(name = "host_presence_required", nullable = false) private Boolean hostPresenceRequired;

    @Column(name = "security_camera_flag", nullable = false) private Boolean securityCameraFlag;
    @Column(name = "gated_property_flag", nullable = false)  private Boolean gatedPropertyFlag;
    @Column(name = "alarm_flag", nullable = false)           private Boolean alarmFlag;
    @Column(name = "lighting_flag", nullable = false)        private Boolean lightingFlag;

    @Column(name = "published_at")
    private Instant publishedAt;
}
