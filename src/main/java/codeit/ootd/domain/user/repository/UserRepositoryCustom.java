package codeit.ootd.domain.user.repository;

import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import org.springframework.data.domain.Slice;

import java.util.UUID;

public interface UserRepositoryCustom {

    Slice<User> findUsers(
            String cursor,
            UUID idAfter,
            int limit,
            String sortBy,
            String sortDirection,
            String emailLike,
            UserRole roleEqual,
            Boolean locked
    );

    long countUsers(
            String emailLike,
            UserRole roleEqual,
            Boolean locked
    );
}