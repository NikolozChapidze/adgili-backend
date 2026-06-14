package li.adgi.api.user;

import jakarta.persistence.*;
import li.adgi.api.common.BaseEntity;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile extends BaseEntity {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "profile_photo_url", columnDefinition = "text")
    private String profilePhotoUrl;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(name = "preferred_language", length = 10)
    private String preferredLanguage;

    @Column(length = 100)
    private String city;
}
