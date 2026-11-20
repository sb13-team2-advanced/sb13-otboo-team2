package codeit.ootd.domain.notification.repository;

import codeit.ootd.domain.notification.entity.NotificationReceipt;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationReceiptRepository extends JpaRepository<NotificationReceipt, UUID> {
}
