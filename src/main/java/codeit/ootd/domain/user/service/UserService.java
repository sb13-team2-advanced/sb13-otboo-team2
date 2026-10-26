package codeit.ootd.domain.user.service;

import codeit.ootd.domain.user.dto.request.UserCreateRequest;

public interface UserService {

    void signup(UserCreateRequest request);
}
