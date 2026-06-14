package li.adgi.api.listing;

import jakarta.persistence.*;
import li.adgi.api.common.BaseEntity;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "listing_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingRules extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "listing_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID listingId;

    @Column(name = "allowed_items_text", columnDefinition = "text")
    private String allowedItemsText;

    @Column(name = "prohibited_items_text", columnDefinition = "text")
    private String prohibitedItemsText;

    @Column(name = "additional_access_rules", columnDefinition = "text")
    private String additionalAccessRules;

    @Column(name = "move_in_instructions", columnDefinition = "text")
    private String moveInInstructions;

    @Column(name = "move_out_instructions", columnDefinition = "text")
    private String moveOutInstructions;
}
