package codeit.ootd.domain.auth.service;

import codeit.ootd.domain.auth.dto.response.SignInResult;
import codeit.ootd.domain.auth.dto.response.TokenRefreshResult;
import codeit.ootd.domain.user.entity.User;

public interface AuthService {

    User authenticate(String email, String password);

    SignInResult signIn(String email, String password);

    TokenRefreshResult refresh(String refreshToken);

    void signOut(String refreshToken);

}
