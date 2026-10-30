package codeit.ootd.domain.user.service;

import codeit.ootd.domain.user.dto.request.UserCreateRequest;
import codeit.ootd.domain.user.dto.response.UserDto;

public interface UserService {

    UserDto signup(UserCreateRequest request);
}
