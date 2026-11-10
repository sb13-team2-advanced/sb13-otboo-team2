package codeit.ootd.domain.directmessage.repository;

import codeit.ootd.domain.directmessage.entity.DirectMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {
}
