package li.adgi.api.notification;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
    UUID id,
    String templateCode,
    String subject,
    JsonNode payload,
    Instant createdAt,
    Instant readAt
) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getTemplateCode(), n.getSubject(),
            n.getPayload(), n.getCreatedAt(), n.getReadAt());
    }
}
