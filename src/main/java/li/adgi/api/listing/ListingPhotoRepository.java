package li.adgi.api.listing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ListingPhotoRepository extends JpaRepository<ListingPhoto, UUID> {
    List<ListingPhoto> findByListingIdOrderBySortOrderAsc(UUID listingId);
    long countByListingId(UUID listingId);

    @Modifying
    @Query("UPDATE ListingPhoto p SET p.isCover = false WHERE p.listingId = :listingId")
    int clearCover(@Param("listingId") UUID listingId);
}
