package codeit.ootd.global.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.mapping.context.PersistentEntities;

class BaseTimeEntityTest {

    @Test
    void recordsCreationAndModificationTimes() {
        AuditingHandler handler = new AuditingHandler(PersistentEntities.of());
        TestEntity entity = new TestEntity();
        Instant createdAt = Instant.parse("2026-10-22T00:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(60);

        handler.setDateTimeProvider(() -> Optional.of(createdAt));
        handler.markCreated(entity);

        assertEquals(createdAt, entity.getCreatedAt());
        assertEquals(createdAt, entity.getUpdatedAt());

        handler.setDateTimeProvider(() -> Optional.of(updatedAt));
        handler.markModified(entity);

        assertEquals(createdAt, entity.getCreatedAt());
        assertEquals(updatedAt, entity.getUpdatedAt());
    }

    private static class TestEntity extends BaseTimeEntity {
    }
}
