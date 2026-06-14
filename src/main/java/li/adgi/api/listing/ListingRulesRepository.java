package li.adgi.api.listing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ListingRulesRepository extends JpaRepository<ListingRules, UUID> {
    Optional<ListingRules> findByListingId(UUID listingId);
}
