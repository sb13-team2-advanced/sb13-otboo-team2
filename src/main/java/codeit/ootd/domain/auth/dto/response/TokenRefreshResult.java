package codeit.ootd.domain.auth.dto.response;

public record TokenRefreshResult(
        String accessToken,
        String refreshToken
) {
}
