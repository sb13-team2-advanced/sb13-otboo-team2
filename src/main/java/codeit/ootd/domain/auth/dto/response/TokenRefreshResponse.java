package codeit.ootd.domain.auth.dto.response;

import codeit.ootd.domain.user.dto.response.UserDto;

public record TokenRefreshResponse(
        UserDto userDto,
        String accessToken
) {
    public static TokenRefreshResponse from(TokenRefreshResult result) {
        return new TokenRefreshResponse(
                result.userDto(),
                result.accessToken()
        );
    }
}