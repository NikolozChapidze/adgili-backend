package li.adgi.api.listing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "listing_features")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingFeature {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "listing_id", nullable = false, columnDefinition = "uuid")
    private UUID listingId;

    @Column(name = "feature_code", nullable = false, length = 100)
    private String featureCode;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
