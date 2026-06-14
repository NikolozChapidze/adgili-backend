package li.adgi.api.user;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class UserDtos {

    public record UserMeResponse(
        UUID id,
        String email,
        String phone,
        String role,
        String status,
        boolean emailVerified,
        boolean phoneVerified,
        ProfileView profile,
        HostView host,
        RenterView renter
    ) {}

    public record ProfileView(
        String firstName,
        String lastName,
        String displayName,
        String profilePhotoUrl,
        String bio,
        String preferredLanguage,
        String city
    ) {
        public static ProfileView from(UserProfile p) {
            if (p == null) return null;
            return new ProfileView(p.getFirstName(), p.getLastName(), p.getDisplayName(),
                p.getProfilePhotoUrl(), p.getBio(), p.getPreferredLanguage(), p.getCity());
        }
    }

    public record HostView(
        String hostStatus,
        BigDecimal averageRating,
        Integer ratingsCount,
        BigDecimal responseRate,
        Integer responseTimeMinutes,
        Boolean payoutEnabled
    ) {
        public static HostView from(HostProfile h) {
            if (h == null) return null;
            return new HostView(h.getHostStatus().name(), h.getAverageRating(),
                h.getRatingsCount(), h.getResponseRate(), h.getResponseTimeMinutes(),
                h.getPayoutEnabled());
        }
    }

    public record RenterView(BigDecimal averageRating, Integer ratingsCount) {
        public static RenterView from(RenterProfile r) {
            if (r == null) return null;
            return new RenterView(r.getAverageRating(), r.getRatingsCount());
        }
    }

    public record UpdateProfileRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 150) String displayName,
        @Size(max = 2000) String bio,
        @Size(max = 10) String preferredLanguage,
        String profilePhotoUrl
    ) {}

    public record PublicUserProfile(
        UUID id,
        String displayName,
        String profilePhotoUrl,
        String bio,
        Instant memberSince,
        HostView host,
        RenterView renter
    ) {}
}
