package li.adgi.api.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import li.adgi.api.common.PageResponse;
import li.adgi.api.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification feed")
public class NotificationController {

    private final NotificationRepository repo;
    private final CurrentUser currentUser;

    @GetMapping
    @Operation(summary = "List in-app notifications for current user")
    public PageResponse<NotificationDto> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        var userId = currentUser.get().userId();
        Page<Notification> result = repo.findByUserIdAndChannelOrderByCreatedAtDesc(
            userId, Notification.Channel.IN_APP, PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.of(result).map(NotificationDto::from);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count of unread in-app notifications")
    public Map<String, Long> unreadCount() {
        long n = repo.countByUserIdAndChannelAndReadAtIsNull(
            currentUser.get().userId(), Notification.Channel.IN_APP);
        return Map.of("count", n);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark notification as read")
    @Transactional
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        repo.markRead(id, currentUser.get().userId(), Instant.now());
        return ResponseEntity.noContent().build();
    }
}
