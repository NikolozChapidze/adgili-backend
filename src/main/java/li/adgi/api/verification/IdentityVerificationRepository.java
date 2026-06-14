package li.adgi.api.verification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, UUID> {
    List<IdentityVerification> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Page<IdentityVerification> findByStatus(IdentityVerification.Status status, Pageable pageable);
    boolean existsByUserIdAndStatus(UUID userId, IdentityVerification.Status status);
}
