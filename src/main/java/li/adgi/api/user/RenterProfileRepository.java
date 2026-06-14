package li.adgi.api.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RenterProfileRepository extends JpaRepository<RenterProfile, UUID> {
}
