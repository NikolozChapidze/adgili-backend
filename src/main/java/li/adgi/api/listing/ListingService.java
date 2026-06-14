package li.adgi.api.listing;

import li.adgi.api.audit.AuditService;
import li.adgi.api.common.ApiException;
import li.adgi.api.common.GeoUtil;
import li.adgi.api.notification.NotificationService;
import li.adgi.api.user.User;
import li.adgi.api.user.UserProfile;
import li.adgi.api.user.UserProfileRepository;
import li.adgi.api.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingService {

    private static final int JITTER_METERS = 300;

    private final ListingRepository listings;
    private final ListingPhotoRepository photos;
    private final ListingFeatureRepository features;
    private final ListingRulesRepository rules;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final NotificationService notifications;
    private final AuditService audit;

    // ---------- Creation & edits ----------

    @Transactional
    public Listing createDraft(UUID hostUserId, ListingDtos.CreateListingRequest req) {
        User host = users.findById(hostUserId).orElseThrow(() -> ApiException.notFound("User"));
        if (!host.isVerified()) {
            throw ApiException.forbidden("Host must have verified email and phone before creating listings");
        }

        var exactLat = req.location().exactLatitude();
        var exactLon = req.location().exactLongitude();
        double[] approx = GeoUtil.jitter(exactLat.doubleValue(), exactLon.doubleValue(), JITTER_METERS);
        BigDecimal approxLat = BigDecimal.valueOf(approx[0]).setScale(6, java.math.RoundingMode.HALF_UP);
        BigDecimal approxLon = BigDecimal.valueOf(approx[1]).setScale(6, java.math.RoundingMode.HALF_UP);

        String slug = generateUniqueSlug(req.title());

        var d = Optional.ofNullable(req.dimensions()).orElse(new ListingDtos.DimensionsRequest(
            null, null, null, null, true, true, null, null));
        var a = Optional.ofNullable(req.access()).orElse(new ListingDtos.AccessRequest(
            null, false, 0, false));
        var s = Optional.ofNullable(req.security()).orElse(new ListingDtos.SecurityRequest(
            false, false, false, false));

        Listing l = Listing.builder()
            .hostUserId(hostUserId)
            .slug(slug)
            .title(req.title())
            .description(req.description())
            .listingStatus(ListingEnums.ListingStatus.DRAFT)
            .moderationStatus(ListingEnums.ModerationStatus.PENDING)
            .spaceType(req.spaceType())
            .monthlyPrice(req.monthlyPrice())
            .currency(Optional.ofNullable(req.currency()).orElse("GEL"))
            .depositAmount(req.depositAmount())
            .minRentalMonths(Optional.ofNullable(req.minRentalMonths()).orElse(1))
            .noticeDaysForCancellation(Optional.ofNullable(req.noticeDaysForCancellation()).orElse(30))
            .instantBookEnabled(false)

            .approximateLatitude(approxLat)
            .approximateLongitude(approxLon)
            .approximateLocation(GeoUtil.point(approxLat, approxLon))
            .exactLatitude(exactLat)
            .exactLongitude(exactLon)
            .exactLocation(GeoUtil.point(exactLat, exactLon))
            .district(req.location().district())
            .neighborhood(req.location().neighborhood())
            .addressLine1(req.location().addressLine1())
            .addressLine2(req.location().addressLine2())
            .postalCode(req.location().postalCode())
            .countryCode("GE")

            .widthM(d.widthM()).lengthM(d.lengthM()).heightM(d.heightM()).areaSqm(d.areaSqm())
            .indoorFlag(Boolean.TRUE.equals(d.indoorFlag()) || d.indoorFlag() == null)
            .enclosedFlag(Boolean.TRUE.equals(d.enclosedFlag()) || d.enclosedFlag() == null)
            .groundFloorFlag(d.groundFloorFlag())
            .humidityNotes(d.humidityNotes())

            .accessHoursText(a.accessHoursText())
            .access247Flag(Boolean.TRUE.equals(a.access247Flag()))
            .accessNoticeHours(Optional.ofNullable(a.accessNoticeHours()).orElse(0))
            .hostPresenceRequired(Boolean.TRUE.equals(a.hostPresenceRequired()))

            .securityCameraFlag(Boolean.TRUE.equals(s.securityCameraFlag()))
            .gatedPropertyFlag(Boolean.TRUE.equals(s.gatedPropertyFlag()))
            .alarmFlag(Boolean.TRUE.equals(s.alarmFlag()))
            .lightingFlag(Boolean.TRUE.equals(s.lightingFlag()))
            .build();

        l = listings.save(l);

        if (req.rules() != null) saveRules(l.getId(), req.rules());
        if (req.featureCodes() != null) saveFeatures(l.getId(), req.featureCodes());

        audit.log(hostUserId, "LISTING", l.getId(), "DRAFT_CREATED", null,
            Map.of("title", l.getTitle(), "district", l.getDistrict()));
        return l;
    }

    @Transactional
    public Listing update(UUID hostUserId, UUID listingId, ListingDtos.CreateListingRequest req) {
        Listing l = requireOwned(listingId, hostUserId);
        if (l.getListingStatus() == ListingEnums.ListingStatus.ARCHIVED
            || l.getListingStatus() == ListingEnums.ListingStatus.BLOCKED) {
            throw ApiException.conflict("BAD_STATE", "Cannot edit archived or blocked listings");
        }

        l.setTitle(req.title());
        l.setDescription(req.description());
        l.setSpaceType(req.spaceType());
        l.setMonthlyPrice(req.monthlyPrice());
        if (req.currency() != null) l.setCurrency(req.currency());
        l.setDepositAmount(req.depositAmount());
        if (req.minRentalMonths() != null) l.setMinRentalMonths(req.minRentalMonths());
        if (req.noticeDaysForCancellation() != null) l.setNoticeDaysForCancellation(req.noticeDaysForCancellation());

        // Location re-jitter if exact changed
        var exactLat = req.location().exactLatitude();
        var exactLon = req.location().exactLongitude();
        if (!exactLat.equals(l.getExactLatitude()) || !exactLon.equals(l.getExactLongitude())) {
            double[] approx = GeoUtil.jitter(exactLat.doubleValue(), exactLon.doubleValue(), JITTER_METERS);
            BigDecimal approxLat = BigDecimal.valueOf(approx[0]).setScale(6, java.math.RoundingMode.HALF_UP);
            BigDecimal approxLon = BigDecimal.valueOf(approx[1]).setScale(6, java.math.RoundingMode.HALF_UP);
            l.setExactLatitude(exactLat);
            l.setExactLongitude(exactLon);
            l.setExactLocation(GeoUtil.point(exactLat, exactLon));
            l.setApproximateLatitude(approxLat);
            l.setApproximateLongitude(approxLon);
            l.setApproximateLocation(GeoUtil.point(approxLat, approxLon));
        }

        l.setDistrict(req.location().district());
        l.setNeighborhood(req.location().neighborhood());
        l.setAddressLine1(req.location().addressLine1());
        l.setAddressLine2(req.location().addressLine2());
        l.setPostalCode(req.location().postalCode());

        if (req.dimensions() != null) {
            var d = req.dimensions();
            l.setWidthM(d.widthM()); l.setLengthM(d.lengthM());
            l.setHeightM(d.heightM()); l.setAreaSqm(d.areaSqm());
            if (d.indoorFlag() != null) l.setIndoorFlag(d.indoorFlag());
            if (d.enclosedFlag() != null) l.setEnclosedFlag(d.enclosedFlag());
            l.setGroundFloorFlag(d.groundFloorFlag());
            l.setHumidityNotes(d.humidityNotes());
        }
        if (req.access() != null) {
            var a = req.access();
            l.setAccessHoursText(a.accessHoursText());
            if (a.access247Flag() != null) l.setAccess247Flag(a.access247Flag());
            if (a.accessNoticeHours() != null) l.setAccessNoticeHours(a.accessNoticeHours());
            if (a.hostPresenceRequired() != null) l.setHostPresenceRequired(a.hostPresenceRequired());
        }
        if (req.security() != null) {
            var s = req.security();
            if (s.securityCameraFlag() != null) l.setSecurityCameraFlag(s.securityCameraFlag());
            if (s.gatedPropertyFlag() != null) l.setGatedPropertyFlag(s.gatedPropertyFlag());
            if (s.alarmFlag() != null) l.setAlarmFlag(s.alarmFlag());
            if (s.lightingFlag() != null) l.setLightingFlag(s.lightingFlag());
        }

        // Edits to a LIVE listing push it back to PENDING_REVIEW
        if (l.getListingStatus() == ListingEnums.ListingStatus.LIVE) {
            l.setListingStatus(ListingEnums.ListingStatus.PENDING_REVIEW);
            l.setModerationStatus(ListingEnums.ModerationStatus.PENDING);
        }

        listings.save(l);
        if (req.rules() != null) saveRules(l.getId(), req.rules());
        if (req.featureCodes() != null) {
            features.deleteByListingId(l.getId());
            saveFeatures(l.getId(), req.featureCodes());
        }

        audit.log(hostUserId, "LISTING", l.getId(), "UPDATED", null, null);
        return l;
    }

    // ---------- State transitions ----------

    @Transactional
    public Listing submitForReview(UUID hostUserId, UUID listingId) {
        Listing l = requireOwned(listingId, hostUserId);
        if (l.getListingStatus() != ListingEnums.ListingStatus.DRAFT
            && l.getListingStatus() != ListingEnums.ListingStatus.REJECTED) {
            throw ApiException.conflict("BAD_STATE",
                "Only DRAFT or REJECTED listings can be submitted for review");
        }
        if (photos.countByListingId(l.getId()) == 0) {
            throw ApiException.badRequest("NO_PHOTOS", "At least one photo is required");
        }
        l.setListingStatus(ListingEnums.ListingStatus.PENDING_REVIEW);
        l.setModerationStatus(ListingEnums.ModerationStatus.PENDING);
        l.setRejectionReason(null);
        listings.save(l);
        audit.log(hostUserId, "LISTING", l.getId(), "SUBMITTED_FOR_REVIEW", null, null);
        return l;
    }

    @Transactional
    public Listing pause(UUID hostUserId, UUID listingId) {
        Listing l = requireOwned(listingId, hostUserId);
        if (l.getListingStatus() != ListingEnums.ListingStatus.LIVE) {
            throw ApiException.conflict("BAD_STATE", "Only LIVE listings can be paused");
        }
        l.setListingStatus(ListingEnums.ListingStatus.PAUSED);
        listings.save(l);
        audit.log(hostUserId, "LISTING", l.getId(), "PAUSED", null, null);
        return l;
    }

    @Transactional
    public Listing unpause(UUID hostUserId, UUID listingId) {
        Listing l = requireOwned(listingId, hostUserId);
        if (l.getListingStatus() != ListingEnums.ListingStatus.PAUSED) {
            throw ApiException.conflict("BAD_STATE", "Only PAUSED listings can be unpaused");
        }
        l.setListingStatus(ListingEnums.ListingStatus.LIVE);
        listings.save(l);
        audit.log(hostUserId, "LISTING", l.getId(), "UNPAUSED", null, null);
        return l;
    }

    // ---------- Admin moderation ----------

    @Transactional
    public Listing adminApprove(UUID adminId, UUID listingId) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
        if (l.getListingStatus() != ListingEnums.ListingStatus.PENDING_REVIEW) {
            throw ApiException.conflict("BAD_STATE", "Listing is not awaiting review");
        }
        l.setListingStatus(ListingEnums.ListingStatus.LIVE);
        l.setModerationStatus(ListingEnums.ModerationStatus.APPROVED);
        l.setRejectionReason(null);
        if (l.getPublishedAt() == null) l.setPublishedAt(Instant.now());
        listings.save(l);

        notifications.sendInApp(l.getHostUserId(), "listing.approved",
            "Your listing is live", Map.of("listingId", l.getId().toString(),
                "title", l.getTitle()));
        audit.logAdmin(adminId, "LISTING", l.getId(), "APPROVED", null, null);
        return l;
    }

    @Transactional
    public Listing adminReject(UUID adminId, UUID listingId, String reason) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
        if (l.getListingStatus() != ListingEnums.ListingStatus.PENDING_REVIEW) {
            throw ApiException.conflict("BAD_STATE", "Listing is not awaiting review");
        }
        l.setListingStatus(ListingEnums.ListingStatus.REJECTED);
        l.setModerationStatus(ListingEnums.ModerationStatus.REJECTED);
        l.setRejectionReason(reason);
        listings.save(l);

        notifications.sendInApp(l.getHostUserId(), "listing.rejected",
            "Your listing was rejected",
            Map.of("listingId", l.getId().toString(), "reason", reason == null ? "" : reason));
        audit.logAdmin(adminId, "LISTING", l.getId(), "REJECTED", null,
            Map.of("reason", reason));
        return l;
    }

    @Transactional
    public Listing adminBlock(UUID adminId, UUID listingId, String reason) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
        l.setListingStatus(ListingEnums.ListingStatus.BLOCKED);
        l.setRejectionReason(reason);
        listings.save(l);
        audit.logAdmin(adminId, "LISTING", l.getId(), "BLOCKED", null,
            Map.of("reason", reason));
        return l;
    }

    // ---------- Photos ----------

    @Transactional
    public ListingPhoto addPhoto(UUID hostUserId, UUID listingId, ListingDtos.AddPhotoRequest req) {
        Listing l = requireOwned(listingId, hostUserId);
        long existing = photos.countByListingId(listingId);
        if (existing >= 20) {
            throw ApiException.badRequest("PHOTO_LIMIT", "Maximum 20 photos per listing");
        }
        boolean makeCover = Boolean.TRUE.equals(req.isCover()) || existing == 0;
        if (makeCover) photos.clearCover(listingId);

        ListingPhoto p = ListingPhoto.builder()
            .listingId(listingId)
            .fileUrl(req.fileUrl())
            .sortOrder(req.sortOrder() != null ? req.sortOrder() : (int) existing)
            .isCover(makeCover)
            .build();
        return photos.save(p);
    }

    @Transactional
    public void deletePhoto(UUID hostUserId, UUID listingId, UUID photoId) {
        requireOwned(listingId, hostUserId);
        ListingPhoto p = photos.findById(photoId).orElseThrow(() -> ApiException.notFound("Photo"));
        if (!p.getListingId().equals(listingId)) throw ApiException.notFound("Photo");
        photos.delete(p);
        // If we deleted the cover, promote the first remaining photo
        if (Boolean.TRUE.equals(p.getIsCover())) {
            photos.findByListingIdOrderBySortOrderAsc(listingId).stream().findFirst()
                .ifPresent(next -> { next.setIsCover(true); photos.save(next); });
        }
    }

    // ---------- Reads ----------

    @Transactional(readOnly = true)
    public ListingDtos.ListingDetail getDetail(UUID listingId, UUID viewerId, boolean includeExact) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
        return toDetail(l, includeExact);
    }

    @Transactional(readOnly = true)
    public ListingDtos.ListingDetail getDetailPublic(UUID listingId) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
        if (l.getListingStatus() != ListingEnums.ListingStatus.LIVE
            && l.getListingStatus() != ListingEnums.ListingStatus.PAUSED) {
            throw ApiException.notFound("Listing");
        }
        return toDetail(l, false);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Listing> listForHost(UUID hostUserId, Pageable pageable) {
        return listings.findByHostUserId(hostUserId, pageable);
    }

    public ListingDtos.ListingDetail toDetail(Listing l, boolean includeExact) {
        List<ListingDtos.PhotoView> photoViews = photos.findByListingIdOrderBySortOrderAsc(l.getId())
            .stream().map(ListingDtos.PhotoView::from).toList();
        List<String> featureCodes = features.findByListingId(l.getId()).stream()
            .map(ListingFeature::getFeatureCode).toList();
        ListingRules r = rules.findByListingId(l.getId()).orElse(null);

        User host = users.findById(l.getHostUserId()).orElseThrow();
        UserProfile hp = profiles.findById(l.getHostUserId()).orElse(null);
        ListingDtos.HostSummary hostSummary = new ListingDtos.HostSummary(
            host.getId(),
            hp != null ? hp.getDisplayName() : null,
            hp != null ? hp.getProfilePhotoUrl() : null,
            null, null,
            host.getCreatedAt()
        );

        return new ListingDtos.ListingDetail(
            l.getId(), l.getSlug(), l.getTitle(), l.getDescription(),
            l.getSpaceType().name(),
            l.getListingStatus().name(), l.getModerationStatus().name(),
            l.getRejectionReason(),
            l.getMonthlyPrice(), l.getCurrency(), l.getDepositAmount(),
            l.getMinRentalMonths(), l.getNoticeDaysForCancellation(),
            new ListingDtos.LocationView(
                l.getDistrict(), l.getNeighborhood(),
                l.getApproximateLatitude(), l.getApproximateLongitude(),
                includeExact ? l.getAddressLine1() : null,
                includeExact ? l.getAddressLine2() : null,
                includeExact ? l.getPostalCode() : null,
                includeExact ? l.getExactLatitude() : null,
                includeExact ? l.getExactLongitude() : null
            ),
            new ListingDtos.DimensionsView(
                l.getWidthM(), l.getLengthM(), l.getHeightM(), l.getAreaSqm(),
                l.getIndoorFlag(), l.getEnclosedFlag(), l.getGroundFloorFlag(), l.getHumidityNotes()
            ),
            new ListingDtos.AccessView(
                l.getAccessHoursText(), l.getAccess247Flag(),
                l.getAccessNoticeHours(), l.getHostPresenceRequired()
            ),
            new ListingDtos.SecurityView(
                l.getSecurityCameraFlag(), l.getGatedPropertyFlag(),
                l.getAlarmFlag(), l.getLightingFlag()
            ),
            r == null ? null : new ListingDtos.RulesView(
                r.getAllowedItemsText(), r.getProhibitedItemsText(),
                r.getAdditionalAccessRules(), r.getMoveInInstructions(), r.getMoveOutInstructions()
            ),
            photoViews,
            featureCodes,
            hostSummary,
            l.getPublishedAt(),
            l.getCreatedAt()
        );
    }

    // ---------- Helpers ----------

    private Listing requireOwned(UUID listingId, UUID hostUserId) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
        if (!l.getHostUserId().equals(hostUserId)) {
            throw ApiException.forbidden("You do not own this listing");
        }
        return l;
    }

    private void saveRules(UUID listingId, ListingDtos.RulesRequest req) {
        ListingRules r = rules.findByListingId(listingId).orElseGet(() ->
            ListingRules.builder().listingId(listingId).build());
        r.setAllowedItemsText(req.allowedItemsText());
        r.setProhibitedItemsText(req.prohibitedItemsText());
        r.setAdditionalAccessRules(req.additionalAccessRules());
        r.setMoveInInstructions(req.moveInInstructions());
        r.setMoveOutInstructions(req.moveOutInstructions());
        rules.save(r);
    }

    private void saveFeatures(UUID listingId, Set<String> codes) {
        for (String code : codes) {
            features.save(ListingFeature.builder()
                .listingId(listingId)
                .featureCode(code.trim().toUpperCase())
                .build());
        }
    }

    private static final SecureRandom RNG = new SecureRandom();

    private String generateUniqueSlug(String title) {
        String base = title.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
        if (base.isEmpty()) base = "listing";
        if (base.length() > 60) base = base.substring(0, 60);
        String candidate = base + "-" + Integer.toHexString(RNG.nextInt(0x1000000));
        int i = 0;
        while (listings.existsBySlug(candidate) && i++ < 5) {
            candidate = base + "-" + Integer.toHexString(RNG.nextInt(0x1000000));
        }
        return candidate;
    }
}
