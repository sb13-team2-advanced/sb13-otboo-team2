package codeit.ootd.domain.notification.repository;

import codeit.ootd.domain.notification.entity.NotificationOutbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {
    List<NotificationOutbox> findTop100ByOrderByCreatedAtAscIdAsc();
}
