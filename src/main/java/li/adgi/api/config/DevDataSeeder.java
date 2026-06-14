package li.adgi.api.config;

import li.adgi.api.common.GeoUtil;
import li.adgi.api.listing.*;
import li.adgi.api.user.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Seeds a demo dataset on startup when the 'dev' or 'seed' profile is active
 * AND the users table is empty. Idempotent.
 */
@Slf4j
@Component
@Profile({"dev", "seed"})
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final HostProfileRepository hostProfiles;
    private final RenterProfileRepository renterProfiles;
    private final ListingRepository listings;
    private final ListingPhotoRepository photos;
    private final ListingRulesRepository rules;
    private final ListingFeatureRepository features;
    private final PasswordEncoder encoder;

    private static final String DEMO_PASSWORD = "Password123!";

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) {
            log.info("Seed skipped: users table not empty ({} rows)", users.count());
            return;
        }
        log.info("Seeding demo data...");

        // Admin
        User admin = createUser("admin@adgi.li", "+995555000001", "Ana", "Admin",
            UserRole.ADMIN, UserStatus.ACTIVE, true, true);

        // Hosts
        User host1 = createUser("giorgi@example.com", "+995555000011", "Giorgi", "Beridze",
            UserRole.USER, UserStatus.ACTIVE, true, true);
        User host2 = createUser("nino@example.com", "+995555000012", "Nino", "Tsiklauri",
            UserRole.USER, UserStatus.ACTIVE, true, true);
        User host3 = createUser("levan@example.com", "+995555000013", "Levan", "Maisuradze",
            UserRole.USER, UserStatus.ACTIVE, true, true);

        // Renters
        User renter1 = createUser("mariam@example.com", "+995555000021", "Mariam", "Kapanadze",
            UserRole.USER, UserStatus.ACTIVE, true, true);
        User renter2 = createUser("david@example.com", "+995555000022", "David", "Lomidze",
            UserRole.USER, UserStatus.ACTIVE, true, false);

        // Activate host profiles
        markHostActive(host1.getId()); markHostActive(host2.getId()); markHostActive(host3.getId());

        // Seed listings distributed across Tbilisi districts
        seedListing(host1, "Clean dry garage in Vake, near Chavchavadze", "Vake", "Chavchavadze Ave",
            41.7094, 44.7589, 18.0, new BigDecimal("280.00"), true, true, true, "STORAGE_UNIT");
        seedListing(host1, "Spacious basement storage, Saburtalo", "Saburtalo", "Vazha-Pshavela",
            41.7252, 44.7415, 35.0, new BigDecimal("450.00"), true, false, true, "GARAGE");
        seedListing(host2, "Gated garage in Vera with 24/7 access", "Vera", null,
            41.7085, 44.7837, 22.5, new BigDecimal("380.00"), true, true, false, "GARAGE");
        seedListing(host2, "Small dry storage, Saburtalo", "Saburtalo", "Bakhtrioni",
            41.7314, 44.7481, 12.0, new BigDecimal("180.00"), false, true, false, "STORAGE_UNIT");
        seedListing(host2, "Large warehouse-style garage, Didube", "Didube", "Tsereteli",
            41.7430, 44.7930, 48.0, new BigDecimal("620.00"), false, true, true, "GARAGE");
        seedListing(host3, "Cozy garage near Rustaveli metro", "Mtatsminda", "Rustaveli",
            41.6986, 44.7986, 16.0, new BigDecimal("320.00"), true, false, false, "GARAGE");
        seedListing(host3, "Old town garage with CCTV", "Old Tbilisi", "Abanotubani",
            41.6892, 44.8094, 14.0, new BigDecimal("260.00"), false, true, true, "GARAGE");
        seedListing(host3, "Bright storage unit, Isani", "Isani", "Ketevan Tsamebuli",
            41.6821, 44.8325, 20.0, new BigDecimal("240.00"), true, false, false, "STORAGE_UNIT");
        seedListing(host1, "Double-size garage, Gldani", "Gldani", null,
            41.7813, 44.8195, 40.0, new BigDecimal("420.00"), true, true, true, "GARAGE");
        seedListing(host3, "Small neat garage, Nadzaladevi", "Nadzaladevi", null,
            41.7605, 44.7835, 15.0, new BigDecimal("200.00"), false, false, false, "GARAGE");

        log.info("Seed complete. Admin: admin@adgi.li / {}", DEMO_PASSWORD);
        log.info("Hosts: giorgi@, nino@, levan@ (all password: {})", DEMO_PASSWORD);
        log.info("Renters: mariam@, david@ (all password: {})", DEMO_PASSWORD);
    }

    private User createUser(String email, String phone, String firstName, String lastName,
                            UserRole role, UserStatus status,
                            boolean emailVerified, boolean phoneVerified) {
        Instant now = Instant.now();
        User u = User.builder()
            .email(email)
            .phone(phone)
            .passwordHash(encoder.encode(DEMO_PASSWORD))
            .role(role)
            .status(status)
            .emailVerifiedAt(emailVerified ? now : null)
            .phoneVerifiedAt(phoneVerified ? now : null)
            .termsAcceptedAt(now)
            .privacyAcceptedAt(now)
            .build();
        u = users.save(u);
        profiles.save(UserProfile.builder()
            .userId(u.getId())
            .firstName(firstName).lastName(lastName)
            .displayName(firstName + " " + lastName)
            .preferredLanguage("ka")
            .city("Tbilisi")
            .build());
        hostProfiles.save(HostProfile.builder()
            .userId(u.getId())
            .hostStatus(HostProfile.HostStatus.PENDING)
            .averageRating(BigDecimal.ZERO)
            .ratingsCount(0)
            .payoutEnabled(false)
            .build());
        renterProfiles.save(RenterProfile.builder()
            .userId(u.getId())
            .averageRating(BigDecimal.ZERO)
            .ratingsCount(0)
            .build());
        return u;
    }

    private void markHostActive(UUID userId) {
        hostProfiles.findById(userId).ifPresent(h -> {
            h.setHostStatus(HostProfile.HostStatus.ACTIVE);
            hostProfiles.save(h);
        });
    }

    private void seedListing(User host, String title, String district, String neighborhood,
                             double lat, double lon, double areaSqm, BigDecimal price,
                             boolean cctv, boolean gated, boolean access247, String spaceType) {
        // Jitter for approximate location
        double[] approx = GeoUtil.jitter(lat, lon, 300);
        BigDecimal approxLat = BigDecimal.valueOf(approx[0]).setScale(6, java.math.RoundingMode.HALF_UP);
        BigDecimal approxLon = BigDecimal.valueOf(approx[1]).setScale(6, java.math.RoundingMode.HALF_UP);
        BigDecimal exactLat = BigDecimal.valueOf(lat).setScale(6, java.math.RoundingMode.HALF_UP);
        BigDecimal exactLon = BigDecimal.valueOf(lon).setScale(6, java.math.RoundingMode.HALF_UP);

        String slug = (title.toLowerCase().replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "")) + "-" + Integer.toHexString(new Random().nextInt(0x1000000));
        if (slug.length() > 120) slug = slug.substring(0, 120);

        BigDecimal width = BigDecimal.valueOf(Math.sqrt(areaSqm * 0.6)).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal length = BigDecimal.valueOf(areaSqm).divide(width, 2, java.math.RoundingMode.HALF_UP);

        Listing l = Listing.builder()
            .hostUserId(host.getId())
            .slug(slug)
            .title(title)
            .description("Demo listing for " + title + ". Located in " + district + ".")
            .listingStatus(ListingEnums.ListingStatus.LIVE)
            .moderationStatus(ListingEnums.ModerationStatus.APPROVED)
            .spaceType(ListingEnums.SpaceType.valueOf(spaceType))
            .monthlyPrice(price)
            .currency("GEL")
            .minRentalMonths(1)
            .noticeDaysForCancellation(30)
            .instantBookEnabled(false)
            .approximateLatitude(approxLat).approximateLongitude(approxLon)
            .approximateLocation(GeoUtil.point(approxLat, approxLon))
            .exactLatitude(exactLat).exactLongitude(exactLon)
            .exactLocation(GeoUtil.point(exactLat, exactLon))
            .district(district)
            .neighborhood(neighborhood)
            .addressLine1(district + ", " + (neighborhood == null ? "central" : neighborhood))
            .countryCode("GE")
            .widthM(width).lengthM(length)
            .heightM(BigDecimal.valueOf(2.40))
            .areaSqm(BigDecimal.valueOf(areaSqm))
            .indoorFlag(true).enclosedFlag(true).groundFloorFlag(true)
            .accessHoursText(access247 ? "24/7" : "08:00-22:00")
            .access247Flag(access247)
            .accessNoticeHours(access247 ? 0 : 2)
            .hostPresenceRequired(false)
            .securityCameraFlag(cctv)
            .gatedPropertyFlag(gated)
            .alarmFlag(false)
            .lightingFlag(true)
            .publishedAt(Instant.now())
            .build();
        l = listings.save(l);

        // A couple of placeholder photos (unsplash-ish URLs; safe public placeholder)
        photos.save(ListingPhoto.builder()
            .listingId(l.getId())
            .fileUrl("https://picsum.photos/seed/" + l.getId() + "-1/1200/800")
            .sortOrder(0).isCover(true).build());
        photos.save(ListingPhoto.builder()
            .listingId(l.getId())
            .fileUrl("https://picsum.photos/seed/" + l.getId() + "-2/1200/800")
            .sortOrder(1).isCover(false).build());

        rules.save(ListingRules.builder()
            .listingId(l.getId())
            .allowedItemsText("Boxes, furniture, seasonal items, sports gear, archived documents")
            .prohibitedItemsText("Flammables, perishables, animals, illegal goods")
            .moveInInstructions("Coordinate with host via the app 24h before move-in")
            .build());

        if (cctv)     features.save(ListingFeature.builder().listingId(l.getId()).featureCode("CCTV").build());
        if (gated)    features.save(ListingFeature.builder().listingId(l.getId()).featureCode("GATED").build());
        if (access247) features.save(ListingFeature.builder().listingId(l.getId()).featureCode("ACCESS_24_7").build());
        features.save(ListingFeature.builder().listingId(l.getId()).featureCode("DRY_SPACE").build());
    }
}
