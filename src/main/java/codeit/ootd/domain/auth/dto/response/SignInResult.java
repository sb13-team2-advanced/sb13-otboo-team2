package codeit.ootd.domain.auth.dto.response;

public record SignInResult(
        String accessToken,
        String refreshToken
) {
}
