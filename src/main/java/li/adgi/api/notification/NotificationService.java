package li.adgi.api.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import li.adgi.api.config.FeatureFlags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Stub notification dispatch. In MVP we persist every notification and log it.
 * When real email/SMS providers are wired, swap the dispatch methods.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repo;
    private final FeatureFlags flags;
    private final ObjectMapper objectMapper;

    @Transactional
    public Notification sendEmail(UUID userId, String templateCode, String subject,
                                  Map<String, Object> payload) {
        return dispatch(userId, Notification.Channel.EMAIL, templateCode, subject, payload);
    }

    @Transactional
    public Notification sendSms(UUID userId, String templateCode, Map<String, Object> payload) {
        return dispatch(userId, Notification.Channel.SMS, templateCode, null, payload);
    }

    @Transactional
    public Notification sendInApp(UUID userId, String templateCode, String subject,
                                  Map<String, Object> payload) {
        return dispatch(userId, Notification.Channel.IN_APP, templateCode, subject, payload);
    }

    private Notification dispatch(UUID userId, Notification.Channel channel,
                                  String templateCode, String subject, Map<String, Object> payload) {
        JsonNode payloadJson = payload == null ? null : objectMapper.valueToTree(payload);
        Notification n = Notification.builder()
            .userId(userId)
            .channel(channel)
            .templateCode(templateCode)
            .subject(subject)
            .payload(payloadJson)
            .status(Notification.Status.PENDING)
            .build();
        n = repo.save(n);

        // Stub: in dev, we mark SENT immediately and log
        boolean isStubbed = (channel == Notification.Channel.EMAIL && flags.isEmailStub())
            || (channel == Notification.Channel.SMS && flags.isSmsStub())
            || channel == Notification.Channel.IN_APP;

        if (isStubbed) {
            log.info("[NOTIFY:{}] user={} template={} subject={} payload={}",
                channel, userId, templateCode, subject, payload);
            n.setStatus(Notification.Status.SENT);
            n.setSentAt(Instant.now());
            n = repo.save(n);
        } else {
            // Real dispatch would happen here, e.g. Mailgun/Twilio. For MVP this branch is unused.
            log.warn("Real dispatch not implemented for channel={}", channel);
        }
        return n;
    }
}
