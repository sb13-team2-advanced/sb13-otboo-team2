package codeit.ootd.domain.auth.service;

import codeit.ootd.domain.user.entity.User;

public interface AuthService {

    User authenticate(String email, String password);

}
