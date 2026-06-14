package li.adgi.api.user;

import jakarta.persistence.*;
import li.adgi.api.common.BaseEntity;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "host_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HostProfile extends BaseEntity {

    public enum HostStatus { PENDING, ACTIVE, BLOCKED }

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "host_status", nullable = false, length = 30)
    private HostStatus hostStatus;

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "ratings_count")
    private Integer ratingsCount;

    @Column(name = "response_rate", precision = 5, scale = 2)
    private BigDecimal responseRate;

    @Column(name = "response_time_minutes")
    private Integer responseTimeMinutes;

    @Column(name = "payout_enabled")
    private Boolean payoutEnabled;
}
