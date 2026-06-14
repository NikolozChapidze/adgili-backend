package li.adgi.api.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repo;
    private final ObjectMapper om;

    /**
     * Log an action. Uses REQUIRES_NEW so audit failures don't roll back business transactions.
     * actorUserId=null implies SYSTEM actor.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID actorUserId, String entityType, UUID entityId,
                    String action, Map<String, Object> oldValue, Map<String, Object> newValue) {
        try {
            AuditLog entry = AuditLog.builder()
                .actorUserId(actorUserId)
                .actorType(actorUserId == null ? AuditLog.ActorType.SYSTEM : AuditLog.ActorType.USER)
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .oldValue(oldValue == null ? null : om.valueToTree(oldValue))
                .newValue(newValue == null ? null : om.valueToTree(newValue))
                .build();
            repo.save(entry);
        } catch (Exception e) {
            log.warn("Audit log write failed: {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAdmin(UUID adminId, String entityType, UUID entityId, String action,
                         Map<String, Object> oldValue, Map<String, Object> newValue) {
        try {
            AuditLog entry = AuditLog.builder()
                .actorUserId(adminId)
                .actorType(AuditLog.ActorType.ADMIN)
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .oldValue(oldValue == null ? null : om.valueToTree(oldValue))
                .newValue(newValue == null ? null : om.valueToTree(newValue))
                .build();
            repo.save(entry);
        } catch (Exception e) {
            log.warn("Admin audit write failed: {}", e.getMessage());
        }
    }
}
