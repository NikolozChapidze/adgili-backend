package li.adgi.api.security;

import java.util.UUID;

public record AuthPrincipal(UUID userId, String role) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
