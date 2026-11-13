package codeit.ootd.domain.auth.service;

public interface PasswordResetRateLimiter {

    void check(String email, String ipAddress);

}
