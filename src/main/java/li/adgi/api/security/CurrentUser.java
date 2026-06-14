package li.adgi.api.security;

import li.adgi.api.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentUser {

    public AuthPrincipal get() {
        return optional().orElseThrow(() -> ApiException.unauthorized("Not authenticated"));
    }

    public Optional<AuthPrincipal> optional() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || !(a.getPrincipal() instanceof AuthPrincipal p)) {
            return Optional.empty();
        }
        return Optional.of(p);
    }
}
