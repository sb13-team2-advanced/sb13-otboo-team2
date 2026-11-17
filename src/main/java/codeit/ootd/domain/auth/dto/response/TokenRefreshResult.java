package codeit.ootd.domain.auth.dto.response;

import codeit.ootd.domain.user.dto.response.UserDto;

public record TokenRefreshResult(
        UserDto userDto,
        String accessToken,
        String refreshToken
) {
}
