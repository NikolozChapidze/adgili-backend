package li.adgi.api.search;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import li.adgi.api.common.PageResponse;
import li.adgi.api.listing.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchService {

    @PersistenceContext
    private EntityManager em;

    private final ListingPhotoRepository photos;

    @Transactional(readOnly = true)
    public PageResponse<ListingDtos.ListingSummary> search(SearchDtos.SearchQuery q) {
        // Build SQL dynamically for simplicity in MVP. Parameters are bound, never concatenated.
        StringBuilder where = new StringBuilder(
            "l.listing_status = 'LIVE' AND l.moderation_status = 'APPROVED'");
        Map<String, Object> params = new LinkedHashMap<>();

        if (q.district() != null && !q.district().isBlank()) {
            where.append(" AND LOWER(l.district) = LOWER(:district)");
            params.put("district", q.district());
        }
        if (q.neighborhood() != null && !q.neighborhood().isBlank()) {
            where.append(" AND LOWER(l.neighborhood) = LOWER(:neighborhood)");
            params.put("neighborhood", q.neighborhood());
        }
        if (q.minPrice() != null) {
            where.append(" AND l.monthly_price >= :minPrice");
            params.put("minPrice", q.minPrice());
        }
        if (q.maxPrice() != null) {
            where.append(" AND l.monthly_price <= :maxPrice");
            params.put("maxPrice", q.maxPrice());
        }
        if (q.minArea() != null) {
            where.append(" AND l.area_sqm >= :minArea");
            params.put("minArea", q.minArea());
        }
        if (q.maxArea() != null) {
            where.append(" AND l.area_sqm <= :maxArea");
            params.put("maxArea", q.maxArea());
        }
        if (Boolean.TRUE.equals(q.access247()))  where.append(" AND l.access_24_7_flag = TRUE");
        if (Boolean.TRUE.equals(q.gated()))      where.append(" AND l.gated_property_flag = TRUE");
        if (Boolean.TRUE.equals(q.cctv()))       where.append(" AND l.security_camera_flag = TRUE");
        if (Boolean.TRUE.equals(q.indoor()))     where.append(" AND l.indoor_flag = TRUE");

        boolean hasGeo = q.centerLat() != null && q.centerLon() != null && q.radiusMeters() != null;
        if (hasGeo) {
            where.append(" AND ST_DWithin(l.approximate_location, " +
                "ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radius)");
            params.put("lat", q.centerLat().doubleValue());
            params.put("lon", q.centerLon().doubleValue());
            params.put("radius", q.radiusMeters());
        }

        String orderBy = switch (q.sort()) {
            case PRICE_ASC   -> "l.monthly_price ASC NULLS LAST";
            case PRICE_DESC  -> "l.monthly_price DESC NULLS LAST";
            case AREA_DESC   -> "l.area_sqm DESC NULLS LAST";
            case NEWEST      -> "l.published_at DESC NULLS LAST";
            case CLOSEST     -> hasGeo
                ? "ST_Distance(l.approximate_location, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) ASC"
                : "l.published_at DESC NULLS LAST";
            case RECOMMENDED -> "l.published_at DESC NULLS LAST";
        };

        String countSql = "SELECT COUNT(*) FROM listings l WHERE " + where;
        String pageSql = "SELECT l.id FROM listings l WHERE " + where +
            " ORDER BY " + orderBy + " LIMIT :limit OFFSET :offset";

        Query countQuery = em.createNativeQuery(countSql);
        params.forEach(countQuery::setParameter);
        Number total = (Number) countQuery.getSingleResult();

        Query pageQuery = em.createNativeQuery(pageSql);
        params.forEach(pageQuery::setParameter);
        pageQuery.setParameter("limit", q.size());
        pageQuery.setParameter("offset", q.page() * q.size());

        @SuppressWarnings("unchecked")
        List<Object> idRows = pageQuery.getResultList();
        List<UUID> ids = idRows.stream().map(o -> (UUID) o).toList();

        if (ids.isEmpty()) {
            Page<Listing> empty = new PageImpl<>(List.of(),
                PageRequest.of(q.page(), q.size()), total.longValue());
            return PageResponse.of(empty).map(l -> null);
        }

        // Fetch full entities in order
        List<Listing> listings = em.createQuery(
                "SELECT l FROM Listing l WHERE l.id IN :ids", Listing.class)
            .setParameter("ids", ids)
            .getResultList();

        Map<UUID, Listing> byId = new HashMap<>();
        listings.forEach(l -> byId.put(l.getId(), l));
        List<Listing> ordered = ids.stream().map(byId::get)
            .filter(Objects::nonNull).toList();

        // Load cover photos in one go
        Map<UUID, String> covers = coverMap(ordered);

        List<ListingDtos.ListingSummary> content = ordered.stream()
            .map(l -> ListingDtos.ListingSummary.from(l, covers.get(l.getId())))
            .toList();

        Page<ListingDtos.ListingSummary> result = new PageImpl<>(
            content, PageRequest.of(q.page(), q.size()), total.longValue());
        return PageResponse.of(result);
    }

    private Map<UUID, String> coverMap(List<Listing> listings) {
        Map<UUID, String> map = new HashMap<>();
        for (Listing l : listings) {
            photos.findByListingIdOrderBySortOrderAsc(l.getId()).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsCover()))
                .findFirst()
                .ifPresentOrElse(
                    p -> map.put(l.getId(), p.getFileUrl()),
                    () -> {
                        var first = photos.findByListingIdOrderBySortOrderAsc(l.getId())
                            .stream().findFirst();
                        first.ifPresent(p -> map.put(l.getId(), p.getFileUrl()));
                    });
        }
        return map;
    }
}
