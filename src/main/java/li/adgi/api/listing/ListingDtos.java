package li.adgi.api.listing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import li.adgi.api.user.UserDtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ListingDtos {

    // ========== Requests ==========

    public record CreateListingRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 5000) String description,
        @NotNull ListingEnums.SpaceType spaceType,
        @NotNull @DecimalMin("1.00") BigDecimal monthlyPrice,
        @Size(max = 10) String currency,
        @DecimalMin("0.00") BigDecimal depositAmount,
        @Min(1) @Max(24) Integer minRentalMonths,
        @Min(7) @Max(180) Integer noticeDaysForCancellation,

        @NotNull LocationRequest location,
        @Valid DimensionsRequest dimensions,
        @Valid AccessRequest access,
        @Valid SecurityRequest security,
        @Valid RulesRequest rules,
        Set<String> featureCodes
    ) {}

    public record LocationRequest(
        @NotBlank @Size(max = 100) String district,
        @Size(max = 100) String neighborhood,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 50) String postalCode,

        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal exactLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal exactLongitude
    ) {}

    public record DimensionsRequest(
        @DecimalMin("0.0") BigDecimal widthM,
        @DecimalMin("0.0") BigDecimal lengthM,
        @DecimalMin("0.0") BigDecimal heightM,
        @DecimalMin("0.0") BigDecimal areaSqm,
        Boolean indoorFlag,
        Boolean enclosedFlag,
        Boolean groundFloorFlag,
        @Size(max = 2000) String humidityNotes
    ) {}

    public record AccessRequest(
        @Size(max = 255) String accessHoursText,
        Boolean access247Flag,
        @Min(0) @Max(168) Integer accessNoticeHours,
        Boolean hostPresenceRequired
    ) {}

    public record SecurityRequest(
        Boolean securityCameraFlag,
        Boolean gatedPropertyFlag,
        Boolean alarmFlag,
        Boolean lightingFlag
    ) {}

    public record RulesRequest(
        @Size(max = 5000) String allowedItemsText,
        @Size(max = 5000) String prohibitedItemsText,
        @Size(max = 5000) String additionalAccessRules,
        @Size(max = 5000) String moveInInstructions,
        @Size(max = 5000) String moveOutInstructions
    ) {}

    public record AddPhotoRequest(
        @NotBlank String fileUrl,
        Integer sortOrder,
        Boolean isCover
    ) {}

    public record AdminModerationRequest(String reason) {}

    // ========== Responses ==========

    public record ListingSummary(
        UUID id,
        String slug,
        String title,
        String district,
        String neighborhood,
        BigDecimal monthlyPrice,
        String currency,
        BigDecimal areaSqm,
        BigDecimal approximateLatitude,
        BigDecimal approximateLongitude,
        Boolean access247Flag,
        Boolean securityCameraFlag,
        Boolean gatedPropertyFlag,
        String coverPhotoUrl,
        String listingStatus,
        Instant publishedAt
    ) {
        public static ListingSummary from(Listing l, String coverUrl) {
            return new ListingSummary(
                l.getId(), l.getSlug(), l.getTitle(),
                l.getDistrict(), l.getNeighborhood(),
                l.getMonthlyPrice(), l.getCurrency(), l.getAreaSqm(),
                l.getApproximateLatitude(), l.getApproximateLongitude(),
                l.getAccess247Flag(), l.getSecurityCameraFlag(), l.getGatedPropertyFlag(),
                coverUrl,
                l.getListingStatus().name(),
                l.getPublishedAt()
            );
        }
    }

    public record ListingDetail(
        UUID id,
        String slug,
        String title,
        String description,
        String spaceType,
        String listingStatus,
        String moderationStatus,
        String rejectionReason,

        BigDecimal monthlyPrice,
        String currency,
        BigDecimal depositAmount,
        Integer minRentalMonths,
        Integer noticeDaysForCancellation,

        LocationView location,
        DimensionsView dimensions,
        AccessView access,
        SecurityView security,
        RulesView rules,

        List<PhotoView> photos,
        List<String> featureCodes,
        HostSummary host,

        Instant publishedAt,
        Instant createdAt
    ) {}

    public record LocationView(
        String district,
        String neighborhood,
        BigDecimal approximateLatitude,
        BigDecimal approximateLongitude,
        // exact address/lat/lon only populated post-booking
        String addressLine1,
        String addressLine2,
        String postalCode,
        BigDecimal exactLatitude,
        BigDecimal exactLongitude
    ) {}

    public record DimensionsView(
        BigDecimal widthM, BigDecimal lengthM, BigDecimal heightM, BigDecimal areaSqm,
        Boolean indoorFlag, Boolean enclosedFlag, Boolean groundFloorFlag, String humidityNotes
    ) {}

    public record AccessView(
        String accessHoursText, Boolean access247Flag, Integer accessNoticeHours,
        Boolean hostPresenceRequired
    ) {}

    public record SecurityView(
        Boolean securityCameraFlag, Boolean gatedPropertyFlag,
        Boolean alarmFlag, Boolean lightingFlag
    ) {}

    public record RulesView(
        String allowedItemsText, String prohibitedItemsText,
        String additionalAccessRules, String moveInInstructions, String moveOutInstructions
    ) {}

    public record PhotoView(
        UUID id, String fileUrl, Integer sortOrder, Boolean isCover
    ) {
        public static PhotoView from(ListingPhoto p) {
            return new PhotoView(p.getId(), p.getFileUrl(), p.getSortOrder(), p.getIsCover());
        }
    }

    public record HostSummary(
        UUID id,
        String displayName,
        String profilePhotoUrl,
        BigDecimal averageRating,
        Integer ratingsCount,
        Instant memberSince
    ) {
        public static HostSummary from(UserDtos.PublicUserProfile p) {
            return new HostSummary(
                p.id(), p.displayName(), p.profilePhotoUrl(),
                p.host() == null ? null : p.host().averageRating(),
                p.host() == null ? null : p.host().ratingsCount(),
                p.memberSince());
        }
    }
}
