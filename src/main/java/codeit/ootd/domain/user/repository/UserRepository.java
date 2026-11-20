package codeit.ootd.domain.user.repository;

import codeit.ootd.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, UserRepositoryCustom {

    Optional<User> findByEmailIgnoreCase(String email);

    long countByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
        select u.id
        from User u
        where u.status = codeit.ootd.domain.user.entity.UserStatus.ACTIVE
        """)
    List<UUID> findActiveUserIds();

}
