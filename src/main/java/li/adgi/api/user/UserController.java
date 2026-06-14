package li.adgi.api.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import li.adgi.api.common.ApiException;
import li.adgi.api.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profiles and account data")
public class UserController {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final HostProfileRepository hostProfiles;
    private final RenterProfileRepository renterProfiles;
    private final CurrentUser currentUser;

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user with full profile")
    @Transactional(readOnly = true)
    public UserDtos.UserMeResponse me() {
        UUID userId = currentUser.get().userId();
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        UserProfile p = profiles.findById(userId).orElse(null);
        HostProfile h = hostProfiles.findById(userId).orElse(null);
        RenterProfile r = renterProfiles.findById(userId).orElse(null);
        return new UserDtos.UserMeResponse(
            u.getId(), u.getEmail(), u.getPhone(),
            u.getRole().name(), u.getStatus().name(),
            u.getEmailVerifiedAt() != null, u.getPhoneVerifiedAt() != null,
            UserDtos.ProfileView.from(p),
            UserDtos.HostView.from(h),
            UserDtos.RenterView.from(r)
        );
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user's profile")
    @Transactional
    public UserDtos.ProfileView updateMe(@Valid @RequestBody UserDtos.UpdateProfileRequest req) {
        UUID userId = currentUser.get().userId();
        UserProfile p = profiles.findById(userId).orElseThrow(() -> ApiException.notFound("Profile"));
        if (req.firstName() != null)   p.setFirstName(req.firstName());
        if (req.lastName() != null)    p.setLastName(req.lastName());
        if (req.displayName() != null) p.setDisplayName(req.displayName());
        if (req.bio() != null)         p.setBio(req.bio());
        if (req.preferredLanguage() != null) p.setPreferredLanguage(req.preferredLanguage());
        if (req.profilePhotoUrl() != null)   p.setProfilePhotoUrl(req.profilePhotoUrl());
        profiles.save(p);
        return UserDtos.ProfileView.from(p);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get public profile for a user")
    @Transactional(readOnly = true)
    public UserDtos.PublicUserProfile getPublic(@PathVariable UUID userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        UserProfile p = profiles.findById(userId).orElse(null);
        HostProfile h = hostProfiles.findById(userId).orElse(null);
        RenterProfile r = renterProfiles.findById(userId).orElse(null);
        return new UserDtos.PublicUserProfile(
            u.getId(),
            p != null ? p.getDisplayName() : null,
            p != null ? p.getProfilePhotoUrl() : null,
            p != null ? p.getBio() : null,
            u.getCreatedAt(),
            UserDtos.HostView.from(h),
            UserDtos.RenterView.from(r)
        );
    }
}
