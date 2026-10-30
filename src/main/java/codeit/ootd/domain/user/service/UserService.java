package codeit.ootd.domain.user.service;

import codeit.ootd.domain.user.dto.request.UserCreateRequest;
import codeit.ootd.domain.user.dto.response.UserDto;
import codeit.ootd.domain.user.dto.response.UserListResponse;
import codeit.ootd.domain.user.entity.UserRole;

import java.util.UUID;

public interface UserService {

    UserDto signup(UserCreateRequest request);

    UserListResponse getUsers(
            String cursor,
            UUID idAfter,
            int limit,
            String sortBy,
            String sortDirection,
            String emailLike,
            UserRole roleEqual,
            Boolean locked
    );
}
