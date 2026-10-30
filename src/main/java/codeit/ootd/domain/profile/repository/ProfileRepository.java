package codeit.ootd.domain.profile.repository;

import codeit.ootd.domain.profile.entity.Profile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByUserId(UUID userId);

    List<Profile> findByUserIdIn(Collection<UUID> userIds);
}
