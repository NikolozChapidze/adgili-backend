package li.adgi.api.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdAndChannelOrderByCreatedAtDesc(
        UUID userId, Notification.Channel channel, Pageable pageable);

    long countByUserIdAndChannelAndReadAtIsNull(UUID userId, Notification.Channel channel);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :readAt " +
           "WHERE n.id = :id AND n.userId = :userId AND n.readAt IS NULL")
    int markRead(@Param("id") UUID id, @Param("userId") UUID userId, @Param("readAt") Instant readAt);
}
