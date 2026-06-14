package li.adgi.api.listing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListingFeatureRepository extends JpaRepository<ListingFeature, UUID> {
    List<ListingFeature> findByListingId(UUID listingId);
    void deleteByListingId(UUID listingId);
}
