package li.adgi.api.listing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ListingRepository
        extends JpaRepository<Listing, UUID>, JpaSpecificationExecutor<Listing> {

    Optional<Listing> findBySlug(String slug);
    boolean existsBySlug(String slug);
    Page<Listing> findByHostUserId(UUID hostUserId, Pageable pageable);
    Page<Listing> findByModerationStatus(ListingEnums.ModerationStatus status, Pageable pageable);
}
